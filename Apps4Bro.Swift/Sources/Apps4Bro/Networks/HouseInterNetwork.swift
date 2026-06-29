import Foundation
import UIKit

// HouseInter — interstitial-flavoured variant of HouseNetwork. Shares the
// `HouseBannerView` WKWebView delivery layer; only the presentation surface
// differs (full-screen overlay on the topmost view controller instead of
// adding to the key window, so dismissal/rotation feel like a real modal).
//
// Communication contract is identical to HouseNetwork — see HouseNetwork.swift
// for the appNotify message channel and click-out semantics. The `isBanner=false`
// query parameter tells the server to serve a full-screen creative.
final class HouseInterNetwork: AdNetworkHandler {
    private weak var manager: AdManager?
    private var id: String = ""
    private var data: Any?
    private var bannerView: HouseBannerView?
    private weak var hostController: UIViewController?

    var network: String { "HouseInter" }

    init(manager: AdManager) {
        self.manager = manager
    }

    func setId(_ id: String) {
        self.id = id
    }

    func show(data: Any) -> Bool {
        self.data = data
        hide()

        let banner = HouseBannerView(zoneId: id, isBanner: false)
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
        banner.load()
        bannerView = banner
        return true
    }

    func display() {
        // Present as an overlay on the topmost view controller's view, not the
        // key window — keeps it inside the host's responder chain so dismissal
        // animations and rotation behave like a real modal.
        guard let banner = bannerView,
              let host = AdMobNetwork.topViewController() else { return }
        hostController = host
        banner.frame = host.view.bounds
        banner.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        host.view.addSubview(banner)
        UIView.animate(withDuration: 0.25) { banner.alpha = 1.0 }
        manager?.adShown(data: data, view: banner)
    }

    func hide() {
        bannerView?.teardown()
        bannerView?.removeFromSuperview()
        bannerView = nil
        hostController = nil
    }
}
