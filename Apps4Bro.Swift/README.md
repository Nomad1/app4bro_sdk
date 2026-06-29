# Apps4Bro (Swift)

Swift Package Manager port of `App4Bro/SDK/Apps4Bro.iOS` (Xamarin C#).
iOS 13+. Uses [Google-Mobile-Ads-SDK SPM](https://github.com/googleads/swift-package-manager-google-mobile-ads) for AdMob + User Messaging Platform.

## What's included

| Module | Swift file | Status vs C# original |
|---|---|---|
| `Apps4BroSDK` | `Apps4BroSDK.swift` | Static-API port. `Init` uses ATT instead of the deprecated `IsAdvertisingTrackingEnabled`. |
| `AdManager` | `AdManager.swift` | URLSession instead of `NSURLConnection`. Same wire format on the `app.php` response. |
| `ReportManager` | `ReportManager.swift` | URLSession + serial DispatchQueue replace the C# polling background thread. |
| `AdContextDelegate` | protocol | Same five callbacks. |
| `AdNetworkHandler` | protocol | Same five entry points. |
| `AdMobNetwork` | interstitial | Modern `InterstitialAd.load(with:request:)` API. Presents from topmost view controller. |
| `AdMobBannerNetwork` | banner | Adaptive banner ad size. Old fixed 60/90pt height removed. |
| `HouseNetwork` | full-screen banner | `WKWebView` replaces deprecated `UIWebView`. Same `app4bro://` scheme. |
| `HouseInterNetwork` | interstitial overlay | New variant — splits the C# "House" entry into banner + interstitial slots. |
| `DummyNetwork` | no-op stub | Identity port. |

## What's *not* included

The C# project shipped several legacy networks; none of them are ported:

- `iAdNetwork` / `iAdBannerNetwork` — Apple iAd was shut down in 2016.
- `InneractiveNetwork` / `InneractiveBanner` — service dead.
- `AdToAppNetwork` — service dead.
- `AdCashNetwork` — service dead.
- `AppodealNetwork` — was behind `_USE_APPODEAL`, Debug-only in the C# build.
- `AdMobNetworkOld` — pre-8.x AdMob API path.

If you need them later, port them next to the existing networks in `Sources/Apps4Bro/Networks/` and add a `manager.registerAdNetwork(...)` call in `AdManager.init`.

## Usage

```swift
import Apps4Bro

// 1. At app launch, before requesting any ad.
Apps4BroSDK.initialize(context: nil)

// 2. (Optional) Drive the consent flow before any AdMob load.
Apps4BroSDK.initCMP(viewController: rootViewController)

// 3. Build a manager. The first key MUST be "app4bro" followed by your app ID.
//    Subsequent pairs are per-network fallback unit IDs.
let manager = AdManager(keys: [
    "app4bro", "your-app4bro-id",
    "admob",   "ca-app-pub-XXX/YYY",
    "house",   "house-zone-1"
])

// 4. Adopt AdContextDelegate on whatever class owns the manager.
class AdController: AdContextDelegate {
    func onInited(_ m: AdManager) { m.loadAd() }
    func onLoadedAd(_ m: AdManager, network: String) { m.displayAd() }
    func onFailedAd(_ m: AdManager, network: String, error: String) -> Bool { true }
    func onShownAd(_ m: AdManager, network: String, view: Any?) {}
    func onClickedAd(_ m: AdManager, network: String) {}
}
manager.initialize(context: adController)
```

## Consuming from another SPM project

Add as a local dependency in your `Package.swift`:

```swift
.package(path: "/Volumes/Projects/Projects/App4Bro/SDK/Apps4Bro.Swift")
```

Or from an Xcode project: File → Add Packages → Add Local → select `Apps4Bro.Swift`.

## Tests

```
swift test
```

Smoke tests only — `version` parity, settings round-trip, ATT-unauthorized advertising ID fallback.
