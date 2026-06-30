import Foundation
import UIKit
import AdSupport
import AppTrackingTransparency
import UserMessagingPlatform // module ships under this name from the GoogleUserMessagingPlatform SPM package

// Port of Apps4BroSDK.cs. C# was a static class — Swift keeps the same API as
// type-level statics on `Apps4BroSDK`.
public enum Apps4BroSettings: Int {
    case none = 0
    case showBannerOnTop = 1
    case useBannerSuperview = 2
}

public enum Apps4BroSDK {
    private static let emptyId = "00000000-0000-0000-0000-000000000000"

    /// Bumped from C# Version=130. Keep in sync if the server expects parity.
    public static let version: Int = 130

    private static var advertisingIdValue: String = emptyId
    private static var initedFlag: Bool = false
    private static var platformValue: String = "unknown"

    public static var useNonPersonalizedAds: Bool = false

    public static var platform: String { platformValue }

    /// Server endpoints — mirrors C# `internal readonly static string` constants.
    /// `settings` and `useNonPersonalizedAds` are read by the network implementations.
    static let reportUrl = "https://app4bro.runserver.net/app4bro/event.php?id=%@&app=%@&event=%@&param=%@&time=%d&eventid=%d"
    // /route/<App4Bro-ID>/ — path-embedded routing (dodges ad-blockers that catch `/ad`).
    // Query params:
    //   id    = App4Bro app ID (same value as path segment). Routing key.
    //   app   = analytics name (bundle identifier). Cosmetic — server uses it only
    //           in GA labels.
    //   lang/sdk/os = metadata.
    //   did   = device advertising ID. Trailing; may be empty when ATT denied.
    //
    // Args order across SDKs (positional): appId, analyticsName, lang, sdk, os, did.
    static let adManagerUrl = "https://app4bro.runserver.net/route/%1$@/?id=%1$@&app=%2$@&lang=%3$@&sdk=%4$d&os=%5$@&did=%6$@"
    static let adManagerUrlShort = "https://app4bro.runserver.net/route/%@/"
    // HouseAdUrl args (in positional order):
    //   id         — house zone ID (per-zone server config; NOT the App4Bro app ID)
    //   app        — analytics name (bundle identifier — same convention as AdManagerUrl)
    //   brand      — device brand ("Apple" on iOS)
    //   model      — device model identifier (e.g. "iPhone15,2")
    //   operator   — carrier (often empty on iOS 16+)
    //   width, height
    //   lang       — ISO 639-1 language code
    //   sdk        — Apps4BroSDK.Version
    //   os         — platform name
    //   osver      — OS version (e.g. "17.5")
    //   appver     — host app version (CFBundleShortVersionString)
    //   did        — device advertising ID (trailing; may be empty)
    //   noclose    — true → hide close button (banner), false → show it (interstitial)
    static let houseAdUrl = "https://app4bro.runserver.net/app4bro/house.php?id=%@&app=%@&brand=%@&model=%@&operator=%@&width=%d&height=%d&lang=%@&sdk=%d&os=%@&osver=%@&appver=%@&did=%@&noclose=%@"
    static let houseAdTimeout: TimeInterval = 10

    private static let settingsQueue = DispatchQueue(label: "Apps4Bro.settings")
    private static var settingsStorage: [Apps4BroSettings: Any] = [:]
    static var settings: [Apps4BroSettings: Any] {
        settingsQueue.sync { settingsStorage }
    }

    public static func setSetting(_ setting: Apps4BroSettings, value: Any) {
        settingsQueue.sync { settingsStorage[setting] = value }
    }

    /// C# `AdvertisingId` getter — auto-inits on iOS if the host never called `init`.
    public static var advertisingId: String {
        if !initedFlag {
            initialize(context: nil)
        }
        if advertisingIdValue.isEmpty || advertisingIdValue == emptyId {
            return "" // C# behavior: conserve the traffic
        }
        return advertisingIdValue
    }

    /// Port of `Init(object context)`. Matches C# semantics: idempotent flag, reads
    /// the IDFA via ASIdentifierManager. C# checked `IsAdvertisingTrackingEnabled`
    /// — deprecated since iOS 14; the modern equivalent is App Tracking Transparency.
    /// Hosts that want a real IDFA must call `requestTrackingAuthorization` first.
    public static func initialize(context: Any?) {
        initedFlag = true
        platformValue = "ios"

        let manager = ASIdentifierManager.shared()
        let authorized: Bool
        if #available(iOS 14, *) {
            authorized = ATTrackingManager.trackingAuthorizationStatus == .authorized
        } else {
            // iOS 13: fall back to the deprecated isAdvertisingTrackingEnabled.
            authorized = manager.isAdvertisingTrackingEnabled
        }
        if authorized {
            let raw = manager.advertisingIdentifier.uuidString
            advertisingIdValue = raw.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? raw
        }
    }

    /// Port of `InitCMP` — drives the Google UserMessagingPlatform consent flow.
    public static func initCMP(viewController: UIViewController) {
        let params = UMPRequestParameters()
        params.tagForUnderAgeOfConsent = false
        #if DEBUG
        let debug = UMPDebugSettings()
        debug.geography = .EEA
        params.debugSettings = debug
        #endif

        UMPConsentInformation.sharedInstance.requestConsentInfoUpdate(with: params) { error in
            if let error = error {
                Log.w("Apps4BroSDK", "UMP consent info update failed: \(error)")
                return
            }
            let status = UMPConsentInformation.sharedInstance.consentStatus
            guard status == .unknown || status == .required else { return }
            UMPConsentForm.load { form, loadError in
                if let loadError = loadError {
                    Log.w("Apps4BroSDK", "UMP consent form load failed: \(loadError)")
                    return
                }
                guard let form = form else { return }
                form.present(from: viewController) { _ in }
            }
        }
    }
}
