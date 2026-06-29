import Foundation

// Port of the AdNetworkHandler interface declared inside AdManager.cs.
// Each network implementation has its own concrete state; the manager only
// talks to it through these five entry points.
public protocol AdNetworkHandler: AnyObject {
    /// `data` is the AdWrapper this load belongs to; networks must round-trip it
    /// back to the manager via `adLoaded`/`adShown`/`adError`/`adClicked`.
    /// Returns false to abort and ask the manager to try the next wrapper.
    func show(data: Any) -> Bool

    func hide()

    /// Actually present the ad (split from `show` because `show` triggers the
    /// async load — `display` only fires after `onLoadedAd` arrives).
    func display()

    func setId(_ id: String)

    var network: String { get }
}
