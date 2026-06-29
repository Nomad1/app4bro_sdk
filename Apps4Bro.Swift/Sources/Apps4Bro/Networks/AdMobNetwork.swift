import Foundation
import UIKit
import GoogleMobileAds

// Port of AdMobNetwork.cs. C# used the legacy `InterstitialAd.Load(unit, request, ...)`
// API + `FullScreenContentDelegate`. Modern GoogleMobileAds Swift uses
// `InterstitialAd.load(with:request:completionHandler:)` + `FullScreenContentDelegate`.
final class AdMobNetwork: NSObject, AdNetworkHandler, GADFullScreenContentDelegate {
    private weak var manager: AdManager?
    private var unitId: String = ""
    private var interstitial: GADInterstitialAd?
    private var data: Any?
    private var nonPersonalizedAds: Bool = false

    let network: String = "AdMob"

    init(manager: AdManager) {
        self.manager = manager
    }

    func setId(_ id: String) {
        // C# split "<unit>;<bool>" — second part toggles NPA per-zone.
        let parts = id.split(separator: ";").map(String.init)
        unitId = parts.first ?? id
        nonPersonalizedAds = parts.count > 1 && parts[1].lowercased() == "true"
    }

    func show(data: Any) -> Bool {
        self.data = data
        hide()

        let request = GADRequest()
        if nonPersonalizedAds || Apps4BroSDK.useNonPersonalizedAds {
            let extras = GADExtras()
            extras.additionalParameters = ["npa": "1"]
            request.register(extras)
        }

        GADInterstitialAd.load(withAdUnitID: unitId, request: request) { [weak self] ad, error in
            guard let self = self else { return }
            if let error = error {
                let ns = error as NSError
                self.manager?.adError(data: self.data, error: "Error \(ns.code): \(ns.localizedDescription)")
                return
            }
            self.interstitial = ad
            self.interstitial?.fullScreenContentDelegate = self
            self.manager?.adLoaded(data: self.data)
        }
        return true
    }

    func hide() {
        interstitial?.fullScreenContentDelegate = nil
        interstitial = nil
    }

    func display() {
        // C# comment: present from the topmost view controller, not the original context,
        // to avoid skipping ads while settings windows are open.
        guard let root = AdMobNetwork.topViewController() else { return }
        interstitial?.present(fromRootViewController: root)
    }

    // MARK: - FullScreenContentDelegate

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        let ns = error as NSError
        manager?.adError(data: data, error: "Error \(ns.code): \(ns.localizedDescription)")
    }

    func adDidRecordClick(_ ad: GADFullScreenPresentingAd) {
        manager?.adClicked(data: data)
    }

    func adDidRecordImpression(_ ad: GADFullScreenPresentingAd) {
        manager?.adShown(data: data, view: ad)
    }

    // MARK: - Helpers

    static func topViewController(_ base: UIViewController? = nil) -> UIViewController? {
        let root = base ?? UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })?
            .rootViewController
        if let nav = root as? UINavigationController { return topViewController(nav.visibleViewController) }
        if let tab = root as? UITabBarController { return topViewController(tab.selectedViewController) }
        if let presented = root?.presentedViewController { return topViewController(presented) }
        return root
    }
}
