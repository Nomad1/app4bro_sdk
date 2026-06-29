import Foundation

// Port of App4Bro/SDK/Apps4Bro.iOS/AdContextDelegate.cs.
// Single-subscriber callback protocol that the AdManager invokes on the host app.
public protocol AdContextDelegate: AnyObject {
    func onInited(_ manager: AdManager)

    /// Return true to ask the manager to advance to the next ad wrapper after a failure.
    func onFailedAd(_ manager: AdManager, network: String, error: String) -> Bool

    func onLoadedAd(_ manager: AdManager, network: String)

    /// `view` is whatever the network returned at show time (e.g. a BannerView, an interstitial
    /// FullScreenPresentingAd). Cast on the receiving side based on `network`.
    func onShownAd(_ manager: AdManager, network: String, view: Any?)

    func onClickedAd(_ manager: AdManager, network: String)
}
