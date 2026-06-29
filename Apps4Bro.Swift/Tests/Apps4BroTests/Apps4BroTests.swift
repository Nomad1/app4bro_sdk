import XCTest
@testable import Apps4Bro

final class Apps4BroTests: XCTestCase {

    func testSDKVersionMatchesCSharp() {
        // C# Apps4BroSDK.Version = 130 — server expects parity.
        XCTAssertEqual(Apps4BroSDK.version, 130)
    }

    func testSettingsRoundTrip() {
        Apps4BroSDK.setSetting(.showBannerOnTop, value: true)
        XCTAssertEqual(Apps4BroSDK.settings[.showBannerOnTop] as? Bool, true)

        Apps4BroSDK.setSetting(.useBannerSuperview, value: false)
        XCTAssertEqual(Apps4BroSDK.settings[.useBannerSuperview] as? Bool, false)
    }

    func testAdvertisingIdReturnsEmptyWhenUnauthorized() {
        // Tests run outside of an app context — ATT is never authorized, so the
        // getter must return "" (C# "conserve the traffic" behavior).
        XCTAssertEqual(Apps4BroSDK.advertisingId, "")
    }
}
