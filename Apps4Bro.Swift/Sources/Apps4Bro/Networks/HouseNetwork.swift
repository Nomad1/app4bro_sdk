import Foundation
import UIKit
@preconcurrency import WebKit
import GoogleMobileAds

// HouseNetwork — full-screen WKWebView banner served from the App4Bro `house.php`
// endpoint. Replaces C# `HouseBannerView : UIWebView`.
//
// Communication contract (ported from Apps4Bro.Windows.InterstitialView):
//   * The HTML creative MUST call `window.webkit.messageHandlers.appNotify.postMessage(<status>)`
//     with one of:  "ready" (loaded + show me), "404" (empty fill), "200" (user closed).
//   * Clicks navigate via `window.open(url, "_blank")` or `<a target="_blank">`, which
//     triggers WKUIDelegate.createWebViewWith — the handler treats that as an ad click,
//     opens the URL in the system browser, and tears down the ad.
//   * The `app4bro://` URL-scheme intercept used by the old C# port is gone.
//
// GADMobileAds.register(webView) is called at init so any Google Ad Manager tag
// inside the creative attributes to this publisher's session.
//
// Naming: the network is still registered as "House" so existing server configs
// keep working. The interstitial-flavoured variant is `HouseInterNetwork`.
final class HouseNetwork: AdNetworkHandler {
    private weak var manager: AdManager?
    private var id: String = ""
    private var data: Any?
    private var bannerView: HouseBannerView?

    var network: String { "House" }

    init(manager: AdManager) {
        self.manager = manager
    }

    func setId(_ id: String) {
        self.id = id
    }

    func show(data: Any) -> Bool {
        self.data = data
        hide()

        let banner = HouseBannerView(zoneId: id, isBanner: true)
        wire(banner)
        banner.load()
        bannerView = banner
        return true
    }

    func display() {
        bannerView?.showBanner()
    }

    func hide() {
        bannerView?.teardown()
        bannerView?.removeFromSuperview()
        bannerView = nil
    }

    private func wire(_ banner: HouseBannerView) {
        banner.didLoadAd = { [weak self] in
            self?.manager?.adLoaded(data: self?.data)
        }
        banner.didShowAd = { [weak self] in
            self?.manager?.adShown(data: self?.data, view: self?.bannerView)
        }
        banner.didClickAd = { [weak self] url in
            self?.manager?.adClicked(data: self?.data)
            if let url = url { UIApplication.shared.open(url, options: [:], completionHandler: nil) }
            self?.hide()
        }
        banner.didClose = { [weak self] in
            self?.hide()
        }
        banner.didFailWithError = { [weak self] err in
            self?.manager?.adError(data: self?.data, error: "Error \(err)")
            self?.hide()
        }
    }
}

/// WKWebView-backed full-screen ad view, shared between `HouseNetwork` (banner)
/// and `HouseInterNetwork` (interstitial). The `isBanner` flag is forwarded as a
/// query parameter so the server can pick the right creative.
final class HouseBannerView: UIView, WKNavigationDelegate, WKUIDelegate {
    private let zoneId: String
    private let isBanner: Bool
    private let timeout: TimeInterval
    private let webView: WKWebView
    private let messageProxy: WeakMessageHandler

    /// `appNotify` is the only script-message channel we listen on. Adding it
    /// retains the handler — `messageProxy` is a weak shim so we can remove it
    /// without leaking the view.
    private static let notifyChannel = "appNotify"

    var didLoadAd: (() -> Void)?
    var didShowAd: (() -> Void)?
    var didFailWithError: ((String) -> Void)?
    /// Fired on window.open / `<a target="_blank">` / a direct link tap.
    /// `url == nil` indicates a click whose URL we couldn't recover.
    var didClickAd: ((URL?) -> Void)?
    var didClose: (() -> Void)?

    init(zoneId: String, isBanner: Bool) {
        self.zoneId = zoneId
        self.isBanner = isBanner
        self.timeout = Apps4BroSDK.houseAdTimeout

        let userContent = WKUserContentController()
        let messageProxy = WeakMessageHandler()
        userContent.add(messageProxy, name: Self.notifyChannel)
        self.messageProxy = messageProxy

        let cfg = WKWebViewConfiguration()
        cfg.userContentController = userContent
        // Match Apps4Bro.Windows InterstitialView settings: no context menu, no
        // dev tools, no zoom — the creative owns the full viewport.
        cfg.preferences.javaScriptCanOpenWindowsAutomatically = true
        cfg.allowsInlineMediaPlayback = true

        self.webView = WKWebView(frame: .zero, configuration: cfg)
        let frame = HouseBannerView.keyWindow()?.frame ?? UIScreen.main.bounds
        super.init(frame: frame)

        messageProxy.owner = self

        isOpaque = false
        backgroundColor = .clear
        alpha = 0.0
        autoresizingMask = [.flexibleWidth, .flexibleHeight]

        webView.frame = bounds
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.bounces = false
        webView.scrollView.minimumZoomScale = 1.0
        webView.scrollView.maximumZoomScale = 1.0
        webView.navigationDelegate = self
        webView.uiDelegate = self

        // Register with GoogleMobileAds so any Google tag inside the creative
        // is recognised as first-party publisher content. Safe to call before
        // GADMobileAds.start(); the registration is queued.
        GADMobileAds.sharedInstance().register(webView)

        addSubview(webView)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not used") }

    /// Detach delegates + remove the script-message handler. Idempotent.
    func teardown() {
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
        webView.configuration.userContentController.removeScriptMessageHandler(forName: Self.notifyChannel)
        webView.stopLoading()
        messageProxy.owner = nil
    }

    func showBanner() {
        guard let window = HouseBannerView.keyWindow() else { return }
        window.addSubview(self)
        UIView.animate(withDuration: 1.0) { self.alpha = 1.0 }
        didShowAd?()
    }

    func load() {
        // `UIDevice.current.model` is the generic category ("iPhone"/"iPad") —
        // replaced by the specific hardware identifier ("iPhone15,2") via sysctl.
        let deviceId = Self.deviceIdentifier()
        let osVer = UIDevice.current.systemVersion
        let lang = Locale.current.languageCode ?? "en"
        // CTCarrier returns "--" since iOS 16.4; skip the probe entirely.
        let carrier = ""
        let bundle = Bundle.main.bundleIdentifier ?? ""
        let appVer = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? ""

        let url = String(
            format: Apps4BroSDK.houseAdUrl,
            zoneId,
            bundle,
            "Apple",
            urlEncode(deviceId),
            urlEncode(carrier),
            Int(frame.width),
            Int(frame.height),
            lang,
            Apps4BroSDK.version,
            Apps4BroSDK.platform,
            osVer,
            appVer,
            Apps4BroSDK.advertisingId,
            isBanner ? "true" : "false"
        )
        guard let nsurl = URL(string: url) else { return }
        var req = URLRequest(url: nsurl,
                             cachePolicy: .reloadIgnoringLocalAndRemoteCacheData,
                             timeoutInterval: timeout)
        req.timeoutInterval = timeout
        webView.load(req)
    }

    // MARK: - Script-message channel (replaces `app4bro://` scheme)

    fileprivate func handleNotify(_ body: String) {
        // Log every appNotify message so we can see what the creative is telling us.
        // Same channel feeds HouseNetwork (banner) and HouseInterNetwork (interstitial),
        // so the prefix calls out which surface received it.
        let surface = isBanner ? "banner" : "inter"
        Log.d("HouseNetwork", "appNotify(\(surface)): \(body)")
        switch body {
        case "ready":
            didLoadAd?()
        case "404":
            didFailWithError?("empty")
        case "200":
            didClose?()
        default:
            // Unknown status — server may have added new values; cascade should
            // not abort over noise. Bump to a warning so unrecognized values
            // surface in logs even when debug logging is stripped.
            Log.w("HouseNetwork", "appNotify(\(surface)): unrecognized body '\(body)'")
        }
    }

    // MARK: - WKUIDelegate (replaces `NewWindowRequested` from Apps4Bro.Windows)

    /// Fired when the creative does `window.open(url, "_blank")` or follows a
    /// `<a target="_blank">` link. We never actually open a child WKWebView —
    /// return nil and let `didClickAd` open the URL in the system browser.
    func webView(_ webView: WKWebView,
                 createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction,
                 windowFeatures: WKWindowFeatures) -> WKWebView? {
        didClickAd?(navigationAction.request.url)
        return nil
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        // Top-level link taps that do NOT go through window.open (e.g. an `<a>`
        // without `target="_blank"`) land here. Treat them as clicks too, so the
        // creative can't escape the ad surface by self-navigating.
        if navigationAction.navigationType == .linkActivated {
            didClickAd?(navigationAction.request.url)
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        let ns = error as NSError
        Log.w("HouseNetwork", "WebView didFail: \(ns.domain)#\(ns.code) \(ns.localizedDescription) url=\(webView.url?.absoluteString ?? "<nil>")")
        didFailWithError?(ns.localizedDescription)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let ns = error as NSError
        Log.w("HouseNetwork", "WebView didFailProvisional: \(ns.domain)#\(ns.code) \(ns.localizedDescription) url=\(webView.url?.absoluteString ?? "<nil>")")
        didFailWithError?(ns.localizedDescription)
    }

    // MARK: - Helpers

    /// Hardware identifier string ("iPhone15,2", "iPad13,18"). Empty on failure.
    /// Falls back to `UIDevice.current.model` if sysctl misbehaves.
    static func deviceIdentifier() -> String {
        var size: Int = 0
        sysctlbyname("hw.machine", nil, &size, nil, 0)
        guard size > 0 else { return UIDevice.current.model }
        var buf = [CChar](repeating: 0, count: size)
        sysctlbyname("hw.machine", &buf, &size, nil, 0)
        let raw = String(cString: buf)
        return raw.isEmpty ? UIDevice.current.model : raw
    }

    static func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })
    }

    /// Port of the C# inline URL encoder. Keeps wire-format parity with the server.
    private func urlEncode(_ source: String) -> String {
        guard !source.isEmpty else { return "" }
        var result = ""
        result.reserveCapacity(source.count * 3)
        for ch in source.unicodeScalars {
            let v = ch.value
            if ch == " " {
                result.append("+")
            } else if ch == "." || ch == "-" || ch == "_" || ch == "~" ||
                      (v >= 0x61 && v <= 0x7A) /* a-z */ ||
                      (v >= 0x41 && v <= 0x5A) /* A-Z */ ||
                      (v >= 0x30 && v <= 0x39) /* 0-9 */ {
                result.append(Character(ch))
            } else {
                result.append(String(format: "%%%02X", v))
            }
        }
        return result
    }
}

/// Weak shim so the `userContentController.add(handler, name:)` retained reference
/// doesn't keep `HouseBannerView` alive. Forwards script messages to the owner.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var owner: HouseBannerView?
    func userContentController(_ userContentController: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        guard let body = message.body as? String else { return }
        owner?.handleNotify(body)
    }
}
