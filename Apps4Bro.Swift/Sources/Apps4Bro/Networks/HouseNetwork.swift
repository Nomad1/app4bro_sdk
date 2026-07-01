import Foundation
import UIKit
@preconcurrency import WebKit
import GoogleMobileAds

// HouseNetwork — full-screen WKWebView interstitial served from the App4Bro
// `house.php` endpoint. Ports the C# `Apps4Bro.iOS/Networks/HouseNetwork.cs`
// which — despite the name — has always presented as a full-screen overlay
// added to the key window, not a container-hosted banner. See
// `Apps4Bro.iOS/Networks/HouseNetwork.cs:126,142` for the original constructor
// (`base(KeyWindow.Frame)`) and Show (`KeyWindow.AddSubview`).
//
// Communication contract:
//   * The HTML creative signals status by one of two channels:
//     - `window.webkit.messageHandlers.appNotify.postMessage(<status>)`  (JS channel)
//     - navigation to `app4bro://<status>`                                (URL scheme)
//     Statuses:  "ready" (loaded, show me) / "200" (user closed) /
//                "404" (empty fill) / other (server error code).
//   * Clicks reach us via THREE paths in decreasing order of preference:
//     - `window.open(url, "_blank")` → `createWebViewWith` (WKUIDelegate)
//     - `<a target="_blank">` link tap → `linkActivated` navigationType
//     - script-initiated top-level nav to a foreign host (post-load) → intercepted
//       and treated as a click. Google GPT ads often use this path for click-outs.
//
// GADMobileAds.register(webView) is called at init so any Google Ad Manager
// tag inside the creative attributes to this publisher's session.
//
// Theoretical banner support: `HouseBannerView.init(zoneId:isBanner:)` accepts
// an `isBanner` flag that, when `true`, opts into transparent-background rendering
// with no window pinning, plus a `noclose=true` URL parameter. That code path
// exists for future extension but is **never exercised in production** — the
// SDK never instantiates `HouseNetwork` with `isBanner=true` and no server
// config returns a network key that would route there. If a game ever ships
// real banner slots (positioned inside a host-provided container, refreshable
// on failure), the banner path will need a proper API redesign; the current
// `HouseBannerView.showBanner()` helper is a stub for that future work.
final class HouseNetwork: AdNetworkHandler {
    private weak var manager: AdManager?
    private var id: String = ""
    private var data: Any?
    private var bannerView: HouseBannerView?
    private weak var hostController: UIViewController?

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

        // isBanner: false → interstitial-flavoured: opaque black backdrop,
        // full-window presentation, `noclose=false` sent in the URL.
        let banner = HouseBannerView(zoneId: id, isBanner: false)
        wire(banner)
        banner.load()
        bannerView = banner
        return true
    }

    func display() {
        // Full-screen interstitial presentation. Pin to the window's four edges
        // via Auto Layout so it survives rotation without a layout-timing race.
        // Mirrors what C# `HouseNetwork.cs:142` did with KeyWindow.AddSubview +
        // FlexibleWidth/Height autoresizing, made robust vs modern iOS scenes.
        guard let banner = bannerView,
              let host = AdMobNetwork.topViewController(),
              let window = host.view.window ?? HouseBannerView.keyWindow() else {
            Log.w("HouseNetwork", "display: no host/window (banner=\(bannerView != nil)) — cannot present")
            return
        }
        hostController = host

        Log.d("HouseNetwork", "display: window.bounds=\(window.bounds) host.view.bounds=\(host.view.bounds)")

        banner.translatesAutoresizingMaskIntoConstraints = false
        window.addSubview(banner)
        NSLayoutConstraint.activate([
            banner.topAnchor.constraint(equalTo: window.topAnchor),
            banner.leadingAnchor.constraint(equalTo: window.leadingAnchor),
            banner.trailingAnchor.constraint(equalTo: window.trailingAnchor),
            banner.bottomAnchor.constraint(equalTo: window.bottomAnchor),
        ])
        banner.layoutIfNeeded()
        Log.d("HouseNetwork", "display: banner.bounds=\(banner.bounds) after layoutIfNeeded")

        UIView.animate(withDuration: 0.25) { banner.alpha = 1.0 }
        manager?.adShown(data: data, view: banner)
    }

    func hide() {
        bannerView?.teardown()
        bannerView?.removeFromSuperview()
        bannerView = nil
        hostController = nil
    }

    private func wire(_ banner: HouseBannerView) {
        banner.didLoadAd = { [weak self] in
            self?.manager?.adLoaded(data: self?.data)
        }
        banner.didShowAd = { [weak self] in
            self?.manager?.adShown(data: self?.data, view: self?.bannerView)
        }
        banner.didClickAd = { [weak self] url in
            Log.d("HouseNetwork", "click url=\(url?.absoluteString ?? "<nil>")")
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

/// WKWebView-backed full-screen ad view. Used by `HouseNetwork` exclusively as
/// an interstitial (`isBanner=false`). The `isBanner=true` branch exists in
/// this class for a hypothetical future banner API but is not exercised in
/// production — no game instantiates it and no server config routes to it.
/// See the banner-support note atop this file for details.
final class HouseBannerView: UIView, WKNavigationDelegate, WKUIDelegate {
    private let zoneId: String
    private let isBanner: Bool
    private let timeout: TimeInterval
    private let webView: WKWebView
    private let messageProxy: WeakMessageHandler

    /// Set true once the creative signals `ready` (or, as a fallback, once the
    /// main frame finishes its initial load). Post-`didLoad` main-frame
    /// navigations to external hosts are then treated as ad click-outs — Google
    /// Publisher Tags typically use `window.top.location = <clickUrl>` for
    /// clicks, which shows up as a `.other`-typed nav, not `.linkActivated`,
    /// and does not go through `createWebViewWith`. Without this branch such
    /// clicks would just swap the WebView's contents and look like a no-op.
    private var didLoadFire = false

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
        // Pick the largest foreground-active window, NOT the key window. During
        // a UMP consent dialog the consent form's window may be `isKeyWindow`,
        // and its frame is whatever size UMP chose — not the game's full bounds.
        // Largest-window heuristic survives that case + still picks the right
        // window after consent dismisses.
        let frame = HouseBannerView.hostWindowBounds()
        Log.d("HouseNetwork", "init \(isBanner ? "banner" : "inter") frame=\(Int(frame.width))x\(Int(frame.height))")
        super.init(frame: frame)

        messageProxy.owner = self

        // Background differs by use case:
        //   * Banner    — transparent so it can live inside a host-provided
        //                 container without painting a rectangle around itself.
        //                 The creative is expected to fill its own area.
        //   * Interstitial — opaque black so wherever the creative doesn't paint
        //                 we get a solid backdrop, not a see-through to the app
        //                 underneath. Prevents the "green game canvas visible
        //                 around a partially-transparent ad" symptom.
        // Note: the shared `HouseBannerView` class is used for both flavours;
        // the `isBanner` flag received at init selects the right backing here.
        if isBanner {
            isOpaque = false
            backgroundColor = .clear
        } else {
            isOpaque = true
            backgroundColor = .black
        }
        alpha = 0.0
        autoresizingMask = [.flexibleWidth, .flexibleHeight]

        // Pin the WKWebView to our full bounds via Auto Layout — belt-and-braces
        // vs the earlier autoresizingMask + layoutSubviews approach. The screenshots
        // from real hardware showed the webview staying at init-time dimensions
        // (portrait) even after the container was resized to landscape; constraints
        // eliminate the frame-vs-layout-timing race entirely.
        webView.translatesAutoresizingMaskIntoConstraints = false
        if isBanner {
            webView.isOpaque = false
            webView.backgroundColor = .clear
            webView.scrollView.backgroundColor = .clear
        } else {
            webView.isOpaque = true
            webView.backgroundColor = .black
            webView.scrollView.backgroundColor = .black
        }
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
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: topAnchor),
            webView.leadingAnchor.constraint(equalTo: leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
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

    /// Banner presentation. Adds the view to the key window at its current
    /// frame — a real banner API should let the host provide the target
    /// container and position, and support refresh + close-on-fail semantics.
    /// The current addSubview-to-window path predates that redesign and is
    /// only exercised on games that don't ship banners today.
    func showBanner() {
        guard let window = HouseBannerView.keyWindow() else {
            Log.w("HouseNetwork", "showBanner: no key window — cannot present")
            return
        }
        Log.d("HouseNetwork", "showBanner: window.bounds=\(window.bounds) banner.frame=\(frame)")
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
        guard let nsurl = URL(string: url) else {
            Log.w("HouseNetwork", "URL(string:) rejected house ad url: \(url)")
            return
        }
        Log.d("HouseNetwork", "load \(isBanner ? "banner" : "inter") url=\(url)")
        var req = URLRequest(url: nsurl,
                             cachePolicy: .reloadIgnoringLocalAndRemoteCacheData,
                             timeoutInterval: timeout)
        req.timeoutInterval = timeout
        webView.load(req)
    }

    // MARK: - Script-message channel (replaces `app4bro://` scheme)

    fileprivate func handleNotify(_ body: String) {
        let surface = isBanner ? "banner" : "inter"
        dispatchStatus(body, source: "appNotify(\(surface))", treatUnknownAsError: false)
    }

    /// Single point of dispatch for status messages, used by both the JS-channel
    /// (`appNotify.postMessage`) and the legacy URL-scheme intercept
    /// (`app4bro://<status>`). Status vocabulary:
    ///   * "ready" — creative loaded, ready to be presented → `didLoadAd`
    ///   * "200"   — user closed the ad                      → `didClose`
    ///   * "404"   — server returned no fill                   → `didFailWithError("empty")`
    ///   * other   — unknown
    ///                In JS-channel mode (`treatUnknownAsError=false`) we just warn,
    ///                matching the old swallow-and-continue behaviour. In URL-scheme
    ///                mode (`treatUnknownAsError=true`) we forward the host as the
    ///                error code, matching Xamarin iOS `BannerServerError(Url.Host)`
    ///                and Android `onError("FailedToReceiveAd: " + uri.getHost())`.
    fileprivate func dispatchStatus(_ status: String, source: String, treatUnknownAsError: Bool) {
        Log.d("HouseNetwork", "\(source) \(status)")
        switch status {
        case "ready":
            didLoadFire = true
            didLoadAd?()
        case "200":
            didClose?()
        case "404":
            didFailWithError?("empty")
        default:
            if treatUnknownAsError {
                didFailWithError?(status.isEmpty ? "unknown" : status)
            } else {
                Log.w("HouseNetwork", "\(source) unrecognized status '\(status)'")
            }
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
        Log.d("HouseNetwork",
              "createWebView (window.open) type=\(navigationAction.navigationType.rawValue) " +
              "userGesture=\(navigationActionHasUserGesture(navigationAction)) " +
              "url=\(navigationAction.request.url?.absoluteString.prefix(200) ?? "<nil>")")
        didClickAd?(navigationAction.request.url)
        return nil
    }

    /// Best-effort user-gesture detection. Public API on `WKNavigationAction`
    /// is limited — `_isRedirect`/`sourceFrame` are private — but the modern
    /// SDK exposes `navigationType == .linkActivated`, `.formSubmitted`, and
    /// `.formResubmitted` which imply user interaction. `.other` may or may
    /// not be user-initiated (script-driven, redirects, iframe navs).
    private func navigationActionHasUserGesture(_ action: WKNavigationAction) -> Bool {
        switch action.navigationType {
        case .linkActivated, .formSubmitted, .formResubmitted, .backForward, .reload:
            return true
        default:
            return false
        }
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView,
                 decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let url = navigationAction.request.url

        // Log every navigation attempt so we can diagnose click paths that
        // aren't currently being intercepted. Trim to essentials — full URL
        // may be very long for Google click-tracking redirects.
        Log.d("HouseNetwork",
              "decidePolicy type=\(navigationAction.navigationType.rawValue) " +
              "targetFrame=\(navigationAction.targetFrame?.isMainFrame == true ? "main" : (navigationAction.targetFrame == nil ? "nil" : "sub")) " +
              "userGesture=\(navigationActionHasUserGesture(navigationAction)) " +
              "url=\(url?.absoluteString.prefix(200) ?? "<nil>")")

        // Legacy `app4bro://<status>` scheme intercept. Mirrors
        //   * Xamarin iOS `HouseNetwork.cs:ShouldStartLoad` (`Url.Scheme == "app4bro"`)
        //   * Android `HouseNetwork.java:shouldOverrideUrlLoading` (`uri.getScheme().equals("app4bro")`)
        // Contract: host "200" = user closed the banner; any other host = server-side
        // error code (e.g. "404"). WKWebView returns NSURLErrorUnsupportedURL (-1002)
        // for unknown schemes, so without this hook the creative's status message
        // surfaces as a generic load failure. The newer `appNotify` JS channel
        // delivers the same callbacks for creatives that have been updated.
        if let url = url, url.scheme == "app4bro" {
            // Route through the unified dispatcher so URL-scheme creatives get
            // identical semantics to the JS-channel ones: "ready" → didLoadAd,
            // "200" → didClose, "404" → didFailWithError, anything else →
            // didFailWithError (matches Xamarin iOS / Android legacy contract).
            dispatchStatus(url.host ?? "", source: "app4bro://", treatUnknownAsError: true)
            decisionHandler(.cancel)
            return
        }

        // Top-level link taps that do NOT go through window.open (e.g. an `<a>`
        // without `target="_blank"`) land here. Treat them as clicks too, so the
        // creative can't escape the ad surface by self-navigating.
        if navigationAction.navigationType == .linkActivated {
            Log.d("HouseNetwork", "click (.linkActivated) url=\(url?.absoluteString ?? "<nil>")")
            didClickAd?(url)
            decisionHandler(.cancel)
            return
        }

        // Post-load main-frame navigation to an external host = click-out.
        // Google Publisher Tags typically fire clicks via `window.top.location = ...`
        // which shows up here as `.other` navigation type (script-initiated),
        // not `.linkActivated`. Without this branch such clicks would swap the
        // WebView's contents — appearing as if the tap did nothing. The
        // `didLoadFire` guard prevents intercepting the initial page load or any
        // redirects that happen before the creative signals `ready`.
        if didLoadFire,
           navigationAction.targetFrame?.isMainFrame == true,
           let url = url,
           let scheme = url.scheme,
           (scheme == "http" || scheme == "https") {
            Log.d("HouseNetwork", "click (post-load main-frame nav, type=\(navigationAction.navigationType.rawValue)) url=\(url.absoluteString)")
            didClickAd?(url)
            decisionHandler(.cancel)
            return
        }

        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // Fallback for creatives that don't use the appNotify/app4bro:// channels:
        // once the main frame finishes loading, allow post-load main-frame nav to
        // be treated as a click. Creatives that DO signal `ready` will have
        // already set this flag inside dispatchStatus.
        Log.d("HouseNetwork", "didFinish: main-frame load complete, arming click intercept")
        didLoadFire = true
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        Log.d("HouseNetwork", "didStartProvisional")
    }

    // MARK: - Touch diagnostics
    //
    // Log every touch that reaches the container so we can distinguish
    // "touches never arrive" (the container isn't in the responder chain, or
    // something above it is intercepting) from "touches arrive but the
    // creative's JS doesn't react" (WKWebView pass-through / GPT click-handler
    // problem). If we see touchesBegan lines fire in the log when you tap the
    // ad, the container is receiving the touch and it's the creative side.
    // If we don't see them, iOS is routing the tap somewhere else entirely.
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        if let t = touches.first {
            let p = t.location(in: self)
            Log.d("HouseNetwork", "touchesBegan @ (\(Int(p.x)),\(Int(p.y))) in banner \(Int(bounds.width))x\(Int(bounds.height))")
        }
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        Log.d("HouseNetwork", "hitTest @ (\(Int(point.x)),\(Int(point.y))) -> \(hit.map { String(describing: type(of: $0)) } ?? "nil")")
        return hit
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        let ns = error as NSError
        Log.w("HouseNetwork", "WebView didFail: \(ns.domain)#\(ns.code) \(ns.localizedDescription) url:\(Self.failingURL(from: ns, fallback: webView))")
        didFailWithError?(ns.localizedDescription)
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let ns = error as NSError
        Log.w("HouseNetwork", "WebView didFailProvisional: \(ns.domain)#\(ns.code) \(ns.localizedDescription) url:\(Self.failingURL(from: ns, fallback: webView))")
        didFailWithError?(ns.localizedDescription)
    }

    /// Prefer `NSURLErrorFailingURLErrorKey` (set by URLLoading when a redirect
    /// or canonicalization step rejected a different URL than the one we sent).
    /// Falls back to `webView.url` for the originating-request URL.
    private static func failingURL(from error: NSError, fallback webView: WKWebView) -> String {
        if let u = error.userInfo[NSURLErrorFailingURLErrorKey] as? URL {
            return u.absoluteString
        }
        if let s = error.userInfo[NSURLErrorFailingURLStringErrorKey] as? String {
            return s
        }
        return webView.url?.absoluteString ?? "<nil>"
    }

    // MARK: - Helpers

    /// Hardware identifier string ("iPhone15,2", "iPad13,18"). Empty on failure.
    /// On the iOS Simulator `hw.machine` returns the host architecture ("arm64" /
    /// "x86_64"), not the simulated device — use `SIMULATOR_MODEL_IDENTIFIER`
    /// which iOS sets to the model the user picked in Xcode.
    static func deviceIdentifier() -> String {
        #if targetEnvironment(simulator)
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"],
           !simulated.isEmpty {
            return simulated
        }
        #endif
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

    /// Bounds of the host app's main window, even when a consent/system dialog
    /// is presenting from a separate window or is the key window. Picks the
    /// largest window in any foreground-active windowScene — that's almost
    /// always the game's own window. Falls back to UIScreen.main.bounds (which
    /// is in natural orientation) only if no scenes are foregrounded.
    static func hostWindowBounds() -> CGRect {
        let activeWindows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive ||
                      $0.activationState == .foregroundInactive }
            .flatMap { $0.windows }
        if let largest = activeWindows.max(by: { lhs, rhs in
            (lhs.bounds.width * lhs.bounds.height) < (rhs.bounds.width * rhs.bounds.height)
        }) {
            return largest.bounds
        }
        return UIScreen.main.bounds
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
