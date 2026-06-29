import Foundation

// Port of DummyNetwork.cs. No-op stub useful for routing tests through AdManager
// without hitting a real ad network.
final class DummyNetwork: AdNetworkHandler {
    private weak var manager: AdManager?
    private var id: String = ""

    let network: String = "Dummy"

    init(manager: AdManager) {
        self.manager = manager
    }

    func show(data: Any) -> Bool { true }
    func hide() {}
    func display() {}
    func setId(_ id: String) { self.id = id }
}
