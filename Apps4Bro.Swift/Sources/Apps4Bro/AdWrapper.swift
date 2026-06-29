import Foundation

// Port of AdWrapper.cs. Per-network counters/state tracked by AdManager so the
// host can see fill/success/click ratios in the AD_LOAD / AD_ERROR report events.
final class AdWrapper {
    let handler: AdNetworkHandler
    let id: String
    let name: String

    var successCount: Int = 0
    var failCount: Int = 0
    var callCount: Int = 0
    var clickCount: Int = 0
    var showCount: Int = 0
    var lastError: String?

    init(handler: AdNetworkHandler, id: String, name: String) {
        self.handler = handler
        self.id = id
        self.name = name
    }
}
