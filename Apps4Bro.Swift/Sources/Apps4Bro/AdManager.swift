import Foundation
import UIKit

// Port of AdManager.cs. The manager owns the network registry, fetches the
// ordered "try this network, fall back to that one" config from the App4Bro
// backend, and dispatches the public `loadAd`/`displayAd`/`hideAd` calls.
public final class AdManager {
    private static let app4BroTag = "app4bro"
    private static let TAG = "AdManager" // matches Android Log.d(TAG, ...) prefix

    private let reportManager: ReportManager
    private let session: URLSession

    private var adNetworks: [String: AdNetworkHandler] = [:]
    private let keys: [String: String]
    /// App number (e.g. "5" for Klondike). Exposed `internal` so network handlers
    /// can include it in HouseAdUrl/etc. requests for consistent analytics labels.
    let appId: String

    private var adWrappers: [AdWrapper]?
    private var currentWrapper: Int = -1

    private(set) public var isAdShown: Bool = false
    private(set) public var isAdLoaded: Bool = false
    private var inited: Bool = false

    public weak var context: AdContextDelegate?

    public var isInited: Bool { adWrappers != nil }

    /// Pass keys as `["app4bro", "<your-app-id>", "admob", "<unit-id>", ...]` to mirror
    /// the C# `params string[] keys` API. The first match for "app4bro" becomes the appId.
    public init(keys: [String]) {
        var dict: [String: String] = [:]
        for i in stride(from: 0, to: keys.count - 1, by: 2) {
            dict[keys[i].lowercased()] = keys[i + 1]
        }
        guard let appId = dict[Self.app4BroTag] else {
            Log.e(Self.TAG, "No App4Bro key specified for AdManager!")
            fatalError("No App4Bro key specified for AdManager!")
        }
        self.keys = dict
        self.appId = appId
        self.reportManager = ReportManager(applicationId: appId)

        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 5
        self.session = URLSession(configuration: cfg)

        Log.d(Self.TAG, "AdManager inited with id \(appId)")
        reportManager.reportEvent("AD_START", parameter: String(Apps4BroSDK.version))

        // Auto-register the built-in networks; host can still call `registerAdNetwork`
        // for custom ones before calling `initialize(context:)`. Order matters: the
        // wrapper list returned by the server picks among these by name.
        registerAdNetwork(AdMobNetwork(manager: self))
        registerAdNetwork(AdMobBannerNetwork(manager: self))
        registerAdNetwork(HouseNetwork(manager: self))
        registerAdNetwork(HouseInterNetwork(manager: self))
        registerAdNetwork(DummyNetwork(manager: self))

        preInit()
    }

    public func registerAdNetwork(_ handler: AdNetworkHandler) {
        adNetworks[handler.network.lowercased()] = handler
    }

    /// C# Init(context) — once the network fetch finishes, OnInited fires on the delegate.
    public func initialize(context: AdContextDelegate) {
        if self.context == nil && inited {
            self.context = context
            context.onInited(self)
            return
        }
        if isInited {
            loadAd()
            return
        }
        self.context = context
    }

    // MARK: - Server fetch

    private func formatAdRequest() -> String {
        // New args (App4Bro ID in path + id=; analytics name in app=; did= trails).
        // did= may be empty when ATT denied / SDK not inited.
        let analyticsName = Bundle.main.bundleIdentifier ?? ""
        let lang = Locale.current.languageCode ?? "en"
        return String(format: Apps4BroSDK.adManagerUrl,
                      appId,
                      analyticsName,
                      lang,
                      Apps4BroSDK.version,
                      Apps4BroSDK.platform,
                      Apps4BroSDK.advertisingId)
    }

    private func preInit() {
        let urlString = formatAdRequest()
        Log.d(Self.TAG, "We are starting requestAds: \(urlString)")
        guard let url = URL(string: urlString) else {
            Log.w(Self.TAG, "Bad URL: \(urlString)")
            DispatchQueue.main.async { self.doInit(data: nil, ok: false) }
            return
        }
        var req = URLRequest(url: url)
        req.timeoutInterval = 5
        session.dataTask(with: req) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                let body = data.flatMap { String(data: $0, encoding: .utf8) }
                let ok = error == nil && (response as? HTTPURLResponse)?.statusCode == 200
                if let err = error {
                    Log.w(Self.TAG, "Network error: \(err)")
                } else if let body = body, !body.isEmpty {
                    Log.d(Self.TAG, "Got response: \(body)")
                } else {
                    Log.w(Self.TAG, "Got empty response")
                }
                self.doInit(data: body, ok: ok)
            }
        }.resume()
    }

    private func doInit(data: String?, ok: Bool) {
        var fromCache = false
        var fromFallback = false
        if let body = data, !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            parseAdData(body, save: true)
        }
        // Cached fallback from a previous good fetch.
        if adWrappers == nil {
            if let cached = UserDefaults.standard.string(forKey: Self.app4BroTag + "_" + appId) {
                Log.d(Self.TAG, "Using cached ad config")
                fromCache = true
                parseAdData(cached, save: false)
            }
        }
        // Hard-coded fallback from the constructor keys (each non-app4bro key becomes its own wrapper).
        if adWrappers == nil {
            Log.d(Self.TAG, "Registering fallback ads")
            fromFallback = true
            var builder = ""
            var count = 0
            for (k, v) in keys where k != Self.app4BroTag {
                count += 1
                builder += "\(k):\(k)def\(count)|\(v)|"
            }
            parseAdData(builder, save: false)
            if adWrappers == nil {
                Log.w(Self.TAG, "doInit: no wrappers after server/cache/fallback — staying uninitialized")
                return
            }
        }
        currentWrapper = -1
        inited = true
        let source = fromCache ? " (from cache)" : (fromFallback ? " (from fallback keys)" : "")
        Log.d(Self.TAG, "Initializing complete. Networks registered: \(adWrappers?.count ?? 0)\(source)")
        context?.onInited(self)
    }

    /// Parses the server response format `network1:zone1|adUnit1|network2:zone2|adUnit2|...`.
    private func parseAdData(_ data: String, save: Bool) {
        let parts = data.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard parts.count > 1 else { return }

        Log.d(Self.TAG, "Got \(parts.count / 2) items from server")

        var wrappers: [AdWrapper] = []
        var i = 0
        while i + 1 < parts.count {
            var networkSpec = parts[i].lowercased()
            let zoneId: String
            if networkSpec.contains(":") {
                let split = networkSpec.split(separator: ":", maxSplits: 1).map(String.init)
                networkSpec = split[0]
                zoneId = split[1]
            } else {
                zoneId = "\(networkSpec) \(wrappers.count + 1)"
            }
            if let handler = adNetworks[networkSpec] {
                wrappers.append(AdWrapper(handler: handler, id: parts[i + 1], name: zoneId))
                Log.d(Self.TAG, "Registered ad network \(networkSpec)[\(parts[i + 1])]")
            } else {
                Log.w(Self.TAG, "Failed to register ad network \(networkSpec)")
            }
            i += 2
        }
        guard !wrappers.isEmpty else { return }
        adWrappers = wrappers
        if save {
            UserDefaults.standard.set(data, forKey: Self.app4BroTag + "_" + appId)
            Log.d(Self.TAG, "Cache refreshed for next launch")
        }
        reportManager.reportEvent("AD_INIT", parameter: String(wrappers.count))
    }

    // MARK: - Public ad-flow API

    public func hideAd() {
        if !isInited {
            Log.w(Self.TAG, "hideAd called before initialization!")
            return
        }
        guard isAdShown else { return }
        isAdShown = false
        guard let wrappers = adWrappers, currentWrapper >= 0, currentWrapper < wrappers.count else { return }
        Log.d(Self.TAG, "hideAd: hiding wrapper \(wrappers[currentWrapper].name)")
        wrappers[currentWrapper].handler.hide()
    }

    public func loadAd() {
        guard isInited, let wrappers = adWrappers else {
            Log.e(Self.TAG, "loadAd called before initialization!")
            fatalError("loadAd called before initialization!")
        }
        if isAdShown {
            Log.w(Self.TAG, "loadAd called while showing")
        }
        currentWrapper += 1
        isAdLoaded = false
        if currentWrapper >= wrappers.count {
            Log.w(Self.TAG, "loadAd: exhausted all \(wrappers.count) wrappers, giving up")
            currentWrapper = -1
            return
        }
        let wrapper = wrappers[currentWrapper]
        wrapper.callCount += 1
        wrapper.handler.setId(wrapper.id)
        Log.d(Self.TAG, "loadAd: trying wrapper \(wrapper.name) (\(currentWrapper + 1)/\(wrappers.count))")
        if !wrapper.handler.show(data: wrapper) {
            Log.w(Self.TAG, "loadAd: wrapper \(wrapper.name) returned false synchronously")
            adError(data: wrapper, error: "Ad wrapper \(wrapper.name) returned false")
        }
    }

    public func displayAd() {
        guard isInited else {
            Log.e(Self.TAG, "displayAd called before initialization!")
            fatalError("displayAd called before initialization!")
        }
        guard isAdLoaded, let wrappers = adWrappers, currentWrapper >= 0, currentWrapper < wrappers.count else {
            Log.d(Self.TAG, "displayAd: no ad ready (loaded=\(isAdLoaded), idx=\(currentWrapper))")
            return
        }
        Log.d(Self.TAG, "displayAd: presenting wrapper \(wrappers[currentWrapper].name)")
        wrappers[currentWrapper].handler.display()
    }

    // MARK: - Network callbacks (called by AdNetworkHandler implementations)

    func adLoaded(data: Any?) {
        var networkName = "Unknown network"
        defer { context?.onLoadedAd(self, network: networkName) }
        isAdLoaded = true
        if let w = data as? AdWrapper {
            networkName = w.name
            w.successCount += 1
            Log.d(Self.TAG, "Ad loaded: \(stats(prefix: networkName, w: w))")
            reportManager.reportEvent("AD_LOAD", parameter: stats(prefix: networkName, w: w))
        } else {
            Log.d(Self.TAG, "Ad loaded: \(networkName)")
            reportManager.reportEvent("AD_LOAD", parameter: "")
        }
    }

    func adError(data: Any?, error: String) {
        var networkName = "Unknown network"
        defer {
            if context?.onFailedAd(self, network: networkName, error: error) == true {
                loadAd()
            }
        }
        isAdShown = false
        isAdLoaded = false
        if let w = data as? AdWrapper {
            networkName = w.name
            w.lastError = error
            w.failCount += 1
            Log.w(Self.TAG, "Ad error: \(stats(prefix: networkName, w: w)) '\(error)'")
            reportManager.reportEvent("AD_ERROR", parameter: "\(stats(prefix: networkName, w: w)) '\(error)'")
        } else {
            Log.w(Self.TAG, "Ad error: \(networkName) '\(error)'")
            reportManager.reportEvent("AD_ERROR", parameter: "\(networkName) '\(error)'")
        }
    }

    func adClicked(data: Any?) {
        var networkName = "Unknown network"
        defer { context?.onClickedAd(self, network: networkName) }
        if let w = data as? AdWrapper {
            networkName = w.name
            w.clickCount += 1
            Log.d(Self.TAG, "Ad clicked: \(stats(prefix: networkName, w: w))")
            reportManager.reportEvent("AD_CLICK", parameter: stats(prefix: networkName, w: w))
        } else {
            Log.d(Self.TAG, "Ad clicked: \(networkName)")
            reportManager.reportEvent("AD_CLICK", parameter: networkName)
        }
    }

    func adShown(data: Any?, view: Any?) {
        var networkName = "Unknown network"
        defer { context?.onShownAd(self, network: networkName, view: view) }
        isAdShown = true
        if let w = data as? AdWrapper {
            networkName = w.name
            w.showCount += 1
            let statsStr = "\(networkName) [\(w.callCount)/\(w.successCount)/\(w.failCount)/\(w.clickCount)/\(w.showCount)]"
            Log.d(Self.TAG, "Ad shown: \(statsStr)")
            reportManager.reportEvent("AD_SHOW", parameter: statsStr)
        } else {
            Log.d(Self.TAG, "Ad shown: \(networkName)")
            reportManager.reportEvent("AD_SHOW", parameter: networkName)
        }
    }

    private func stats(prefix: String, w: AdWrapper) -> String {
        "\(prefix) [\(w.callCount)/\(w.successCount)/\(w.failCount)/\(w.clickCount)]"
    }
}
