import Foundation
import UIKit
import GoogleMobileAds

// Port of AdMobBannerNetwork.cs. C# computed an iPad-vs-iPhone fixed-height banner
// frame derived from `controller.InterfaceOrientation` (deprecated since iOS 13).
// Swift port uses the adaptive banner ad size relative to the canvas width and
// resolves the host UIViewController via `manager.context as? UIViewController`,
// matching the C# `(UIViewController)m_adManager.Context` cast.
final class AdMobBannerNetwork: NSObject, AdNetworkHandler, GADBannerViewDelegate {
    private weak var manager: AdManager?
    private var unitId: String = ""
    private var bannerView: GADBannerView?
    private var data: Any?

    let network: String = "AdMobBanner"

    init(manager: AdManager) {
        self.manager = manager
    }

    func setId(_ id: String) {
        unitId = id
    }

    func show(data: Any) -> Bool {
        self.data = data
        hide()

        guard let controller = manager?.context as? UIViewController else {
            manager?.adError(data: data, error: "AdMobBanner needs a UIViewController context")
            return false
        }

        let bounds = UIScreen.main.bounds
        let width = max(bounds.width, bounds.height)
        let height = min(bounds.width, bounds.height)

        let showOnTop = (Apps4BroSDK.settings[.showBannerOnTop] as? Bool) == true
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let bannerHeight: CGFloat = isPad ? 90 : 60

        let isLandscape = UIDevice.current.orientation.isLandscape
            || (bounds.width > bounds.height)
        let canvasWidth = isLandscape ? width : height
        let canvasHeight = isLandscape ? height : width

        let y: CGFloat = showOnTop ? 0 : (canvasHeight - bannerHeight)
        let frame = CGRect(x: 0, y: y, width: canvasWidth, height: bannerHeight)

        let adSize: GADAdSize = isLandscape
            ? GADCurrentOrientationAnchoredAdaptiveBannerAdSizeWithWidth(canvasWidth)
            : GADCurrentOrientationAnchoredAdaptiveBannerAdSizeWithWidth(canvasWidth)
        let banner = GADBannerView(adSize: adSize)
        banner.rootViewController = controller
        banner.frame = frame
        banner.isHidden = true
        banner.delegate = self

        if (Apps4BroSDK.settings[.useBannerSuperview] as? Bool) == true {
            controller.view.superview?.addSubview(banner)
        } else {
            controller.view.addSubview(banner)
        }

        #if DEBUG
        banner.adUnitID = "ca-app-pub-3940256099942544/2934735716" // Google test banner unit
        #else
        banner.adUnitID = unitId
        #endif

        let request = GADRequest()
        if Apps4BroSDK.useNonPersonalizedAds {
            let extras = GADExtras()
            extras.additionalParameters = ["npa": "1"]
            request.register(extras)
        }
        banner.load(request)
        bannerView = banner
        return true
    }

    func display() {
        guard let banner = bannerView else { return }
        banner.isHidden = false
        manager?.adShown(data: data, view: banner)
    }

    func hide() {
        bannerView?.delegate = nil
        bannerView?.removeFromSuperview()
        bannerView = nil
    }

    // MARK: - GADBannerViewDelegate

    func bannerViewDidReceiveAd(_ bannerView: GADBannerView) {
        manager?.adLoaded(data: data)
    }

    func bannerView(_ bannerView: GADBannerView, didFailToReceiveAdWithError error: Error) {
        let ns = error as NSError
        manager?.adError(data: data, error: "Error \(ns.code): \(ns.localizedDescription)")
    }

    func bannerViewDidRecordClick(_ bannerView: GADBannerView) {
        manager?.adClicked(data: data)
    }

    func bannerViewWillDismissScreen(_ bannerView: GADBannerView) {
        manager?.adClicked(data: data)
    }
}
