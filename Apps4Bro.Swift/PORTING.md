# Apps4Bro Swift Port Plan

Living document. Maintained across sessions. **If you are a new Claude session:** read this whole file before doing any porting work on Apps4Bro, then read `README.md` for usage and the C# source under `../Apps4Bro.iOS/` for behaviour parity.

---

## Status legend

- `[ ]` not started
- `[~]` in progress (resume context in the task body)
- `[x]` complete
- `[!]` blocked — see note
- `[-]` cancelled / scope-dropped — see decisions log

---

## Resume context (read this first)

**Current phase:** Phase 2 complete — package wired as local SPM dep into all 5 Solitaire iOS Free targets; all 10 game targets BUILD SUCCEEDED. Interstitial-only consumption (no banners in card games).
**Last session:** 2026-06-29 (session 2) — Phase 2 wiring into Solitaire. AdController shim in Klondike (symlinked to other 4 games), per-game AdConfig with C#-parity IDs, AppDelegate wiring with #if FREE_VERSION blocks, 5 pbxproj edits adding `XCLocalSwiftPackageReference`/`XCSwiftPackageProductDependency`/`Apps4Bro in Frameworks` to each game's Free target. Q1/Q2/Q3 resolved (see Open questions).
**Next action:** Phase 3 — production hardening. Info.plist `NSUserTrackingUsageDescription` already added by Solitaire's session 26 (mirrors the Apps4Bro consumer requirement). Remaining: live-server handshake verification with mitmproxy/Charles + memory-leak audit under load.
**Source layout:**
- C# original: `/Volumes/Projects/Projects/App4Bro/SDK/Apps4Bro.iOS/` (Xamarin, csproj-based)
- Swift port: `/Volumes/Projects/Projects/App4Bro/SDK/Apps4Bro.Swift/` (SPM)
- Sister ports: `Apps4Bro.Android/` (Java), `Apps4Bro.Windows/` (C#/WinRT) — out of scope here

**Solitaire integration:** Solitaire SwiftPort iOS targets will consume this package once Phase 8 (ad SDK wiring) starts. See `/Volumes/Projects/Projects/Solitaire/PORTING.md` for the cross-link.

---

## Scope

| In | Out |
|---|---|
| `Apps4BroSDK` (entry point, ATT advertising ID, CMP) | `iAdNetwork` + `iAdBannerNetwork` — service shut down 2016 |
| `AdManager` (registry, server fetch, load/display/hide) | `InneractiveNetwork` + `InneractiveBanner` — service dead |
| `ReportManager` (async event logging) | `AdToAppNetwork` — service dead |
| `AdContextDelegate` / `AdNetworkHandler` protocols | `AdCashNetwork` — service dead |
| `AdWrapper` (internal stats) | `AppodealNetwork` — was Debug-only behind `_USE_APPODEAL` |
| `AdMobNetwork` (interstitial) | `AdMobNetworkOld` — pre-8.x AdMob API |
| `AdMobBannerNetwork` | `Apps4Bro.Android` / `Apps4Bro.Windows` ports |
| `HouseNetwork` (full-screen WKWebView banner) | C# `#if __ANDROID__` / `#elif NETFX_CORE` branches |
| `HouseInterNetwork` (interstitial overlay variant — new in Swift port) | |
| `DummyNetwork` | |

---

## Phases

### Phase 0 — Scoping `[x]`
- Decided directory: `App4Bro/SDK/Apps4Bro.Swift/` (sibling to `Apps4Bro.iOS`, matching naming convention).
- Decided distribution: Swift Package Manager (with possibility of an Xcode framework wrapper later if a consumer needs it).
- Decided iOS deployment target: 13 (lower than C#'s implicit 12 because WKWebView's modern delegate signatures require it; covers ~99% of iOS users in 2026).
- Network scope confirmed (see above table).

### Phase 1 — Core port `[x]`
- Ported all 5 in-scope networks + 3 manager classes + 2 protocols + 1 wrapper.
- Modernized deprecated iOS APIs (see "Deviations from C#" below).
- SPM dependencies: `swift-package-manager-google-mobile-ads` (11.x) + `swift-package-manager-google-user-messaging-platform` (2.x). Both are separate packages (the SDK's GitHub README is misleading; the CMP product is not re-exported from the ads package).
- `xcodebuild build` green for `generic/platform=iOS Simulator`.
- `xcodebuild test` green on iPhone 15 simulator — 3 smoke tests (version parity, settings round-trip, ATT-unauthorized fallback).

### Phase 2 — Solitaire integration `[x]`
- `[x]` Added as `XCLocalSwiftPackageReference relativePath = "../../../../App4Bro/SDK/Apps4Bro.Swift/"` to all 5 Solitaire iOS Free targets (Klondike, FreeCell, Spider, Pyramid, TriPeaks).
- `[x]` Shared `AdController` class implementing `AdContextDelegate` lives in `SwiftPort/iOS/Klondike/Klondike/AdController.swift` (symlinked into the other 4 games). FREE_VERSION-gated.
- `[x]` Per-game `static let adConfig: AdConfig` on each game's `<Game>ViewController.swift` with C#-parity app4bro + AdMob IDs.
- `[x]` AppDelegate wiring: `AdController.shared.start/.resume` from `didFinishLaunching` + `applicationWillEnterForeground`.
- `[x]` **Interstitial-only consumption** — Solitaire registers `admob`, `houseInter`, `dummy`. Banner networks (`adMobBanner`, `house`) ship in the SDK but are not registered by Solitaire per owner direction.
- `[x]` All 10 game targets build clean (5 Free linking Apps4Bro, 5 HD skipping it).
- `[ ]` Live-traffic verification: AdMob test unit returns an interstitial in DEBUG (next session, on simulator).
- `[ ]` Production server handshake: real `app4bro` ID returns a valid wrapper config from `https://app4bro.runserver.net/app4bro/ad.php`.

### Phase 3 — Production hardening `[ ]`
- Real-device test pass: Info.plist must include `NSUserTrackingUsageDescription` and the `SKAdNetworkItems` array required by AdMob 11.x.
- Run with Charles / mitmproxy to verify the report endpoint URL format matches the server's expectations byte-for-byte (the C# `string.Format` template was ported as `String(format:)` with the same positional specifiers; one untested edge case is the `'['` / `']'` → `'{' / '}'` parameter sanitisation).
- Memory leak audit on the network handlers (weak-back-references to `AdManager` are in place but not yet exercised under load).

### Phase 4 — Optional Mac Catalyst / iPad split `[ ]`
- Currently iPad inherits the iPhone path. Banner sizing math is identical (adaptive banner), but the C# original hard-coded different `bannerHeight` (60 vs 90) — Swift port collapses to adaptive. Decision deferred until a consumer reports a concrete issue.

### Phase 5 — Legacy network revival `[-]`
- Cancelled. The 6 dropped networks target dead services. If a new partner needs revival, add them as fresh files in `Sources/Apps4Bro/Networks/` and `manager.registerAdNetwork(...)` in `AdManager.init` — the protocol is intentionally identical to C#'s `AdNetworkHandler`.

---

## Deviations from C#

Each of these is intentional; reverting them re-introduces a deprecated API or crashes on modern iOS.

| C# (Xamarin) | Swift (this port) | Reason |
|---|---|---|
| `ASIdentifierManager.IsAdvertisingTrackingEnabled` | `ATTrackingManager.trackingAuthorizationStatus == .authorized` on iOS 14+; falls back on iOS 13 only | `IsAdvertisingTrackingEnabled` was deprecated in iOS 14 alongside ATT. Without ATT, the IDFA always reads as all-zero. |
| `NSURLConnection.SendAsynchronousRequest(...)` | `URLSession.shared.dataTask(with:)` | `NSURLConnection` was removed in iOS 13. |
| `ConcurrentQueue<EventData>` + polling background thread sleeping 100 ms | Serial `DispatchQueue` + URLSession completion handler chains | No polling sleep; back-pressure is implicit via the `sending` flag. Equivalent ordering. |
| `HouseBannerView : UIWebView` | `HouseBannerView : UIView` containing a `WKWebView` subview | `UIWebView` was hard-removed in iOS 12. WKWebView's `decidePolicyFor` replaces `shouldStartLoadWith`. |
| `UIApplication.SharedApplication.KeyWindow` | `connectedScenes` walk → first `UIWindowScene` with `isKeyWindow` | `keyWindow` is deprecated since iOS 13. |
| `controller.InterfaceOrientation` (iOS 8 deprecated) | Read `UIScreen.main.bounds` aspect, plus `UIDevice.current.orientation.isLandscape` | `InterfaceOrientation` returns garbage on modern iOS. |
| Fixed-height banner (60 pt iPhone / 90 pt iPad) | Adaptive banner via `GADCurrentOrientationAnchoredAdaptiveBannerAdSizeWithWidth` | Google deprecates fixed banner sizes; adaptive is required for fill rate. |
| `GADInterstitialAd.Load(unit, request, ...)` (synchronous-looking) | `GADInterstitialAd.load(withAdUnitID:request:completionHandler:)` | Same API, different Swift signature. |
| Single `HouseNetwork` class handling both banner + interstitial | Split into `HouseNetwork` + `HouseInterNetwork` | User-requested. Lets server configs route banner vs interstitial slots independently while sharing the WKWebView delivery layer. |
| `Console.WriteLine` everywhere | Removed (no `print`/`os_log` substitute) | Avoids leaking strings to production logs. Restore via `os.Logger` if debugging is needed. |

---

## Decisions log

| Date | Decision | Rationale |
|---|---|---|
| 2026-06-29 | iOS 13 deployment target, not 12 | WKWebView's modern delegate signatures + scene-based key-window lookup require it; iOS 12 covers ~0.5% of active devices in 2026. |
| 2026-06-29 | Use AdMob 11.x (`GAD`-prefixed names) | 11.x is the last release before Google renamed every type without the `GAD` prefix. 12.x bumps the deployment target to iOS 15 and renames the entire surface. Stay on 11.x until a consumer needs iOS 12+ → iOS 15+ migration. |
| 2026-06-29 | Split `House` into `House` + `HouseInter` | User-requested; preserves backward-compatible server config (`House` still names the banner slot) while opening a route for interstitial-style house ads. |
| 2026-06-29 | Skip CTCarrier probe in HouseNetwork URL | `subscriberCellularProvider` returns nil on iOS 16+ (deprecated) and `carrierName` returns `"--"` since iOS 16.4. Sending an empty operator field matches what newer real devices report. |
| 2026-06-29 | Use Swift Package Manager, not Xcode framework | SPM consumes as a local-path dep from Solitaire SwiftPort projects without intermediate artefacts. Easy to switch to a binary framework later if Apple introduces SPM module-stability issues. |

---

## Open questions

- **Q1 [resolved 2026-06-29] — `useNonPersonalizedAds` per-zone semantics.** Behaviour confirmed by owner: per-zone `;true` suffix on the AdMob unit ID forces NPA for that zone regardless of the global `Apps4BroSDK.useNonPersonalizedAds` flag; without the suffix it defers to the global flag. The parsing is `id.split(";")` → first part = unit, second part lower-cased == `"true"` toggles per-zone. Solitaire doesn't use the per-zone suffix in practice (sets global flag from UMP consent), but the parsing is preserved verbatim from C# `AdMobNetwork.SetId` for forward-compat with server configs.
- **Q2 [closed 2026-06-29] — Wrapper config save key.** Owner confirmed Solitaire has its own cache and does not need to inherit anything from Xamarin's `UserDefaults` keys. Swift port still writes under `app4bro_<appId>` for its own caching, but that's an implementation detail with no cross-version contract.
- **Q3 [closed 2026-06-29] — `HouseBannerView` link-out callback order.** Owner: doesn't matter. Swift behaviour (`willLeaveApplication` fires before `UIApplication.shared.open`) is kept as-is.

---

## Session log

Append a new entry at the top each session. Format:

```
### YYYY-MM-DD — <one-line summary>
- Phase: <which phase touched>
- Changed: <files/areas modified, or "none — planning only">
- Decided: <decisions made this session, with IDs if applicable>
- Next: <single concrete next action for the next session>
- Notes: <gotchas, hypotheses to test, open questions>
```

---

### 2026-06-29 (session 3) — HouseBannerView modernised + new `/route/<appId>/` ad endpoint (also ported to Windows)

- Phase: 1 (SDK core) — non-breaking improvements
- Changed (Apps4Bro.Swift):
  - **`HouseBannerView` rewrite** in `Sources/Apps4Bro/Networks/HouseNetwork.swift`. The `app4bro://` URL-scheme intercept is gone; replaced with a `WKScriptMessageHandler` listening on the `appNotify` channel. The HTML creative now signals via `window.webkit.messageHandlers.appNotify.postMessage("ready"|"404"|"200")`. Matches Apps4Bro.Windows `InterstitialView.WebMessageReceived`.
  - **`WKUIDelegate.createWebViewWith` added** — handles `window.open(url, "_blank")` / `<a target="_blank">` clicks. Replaces the Windows `NewWindowRequested` event. `decidePolicyFor` kept as a backstop for direct linkActivated taps so the creative can't escape via self-navigation.
  - **`GADMobileAds.sharedInstance().register(webView)`** called in `HouseBannerView.init` so any Google Ad Manager tag inside the creative attributes to the publisher's session.
  - **`WeakMessageHandler` shim** — `WKUserContentController.add(_:name:)` retains the handler; the weak proxy lets the view dealloc cleanly.
  - **`teardown()`** added — removes the script-message handler, detaches delegates, stops loading. Idempotent.
  - **`HouseInterNetwork` updated** to the new `didClickAd: (URL?) -> Void` signature and `isBanner: false`.
  - **`houseAdUrl`** gained the trailing `&banner=true|false` param (12th positional arg). Matches Apps4Bro.Windows; the server picks banner-sized vs full-screen creative.
  - **`adManagerUrl` reworked** to the new `/route/<appId>/?did=<advertisingId>&lang=&sdk=&os=` form + short fallback `/route/<appId>/`. Path-embedded app ID dodges ad-blockers that catch `/ad`. **Naming convention** (owner clarification 2026-06-29): `app` is the app NUMBER (e.g. "5" for Klondike) — sits in the path. `did` is the device advertising ID. The Apps4Bro.Android (Java) constants currently use `?app=<advertisingId>` instead of `?did=` — they're inconsistent with houseAdUrl/ReportUrl; flag if a future port touches them. `AdManager.formatAdRequest` picks long vs short based on whether `Apps4BroSDK.advertisingId` is empty (ATT denied → short form, server returns generic wrapper config without IDFA targeting).
- Changed (Apps4Bro.Windows — backported the same `adManagerUrl` change owner requested):
  - **`Apps4BroSDK.cs:AdManagerUrl`** updated to `https://app4bro.runserver.net/route/{1}/?app={0}&lang={2}&sdk={3}&os={4}` plus new `AdManagerUrlShort = "https://app4bro.runserver.net/route/{0}/"`.
  - **`AdManager.cs:FormatRequest`** picks long vs short form based on `Apps4BroSDK.AdvertisingId` populated/empty. Falls back to `CultureInfo.CurrentCulture.TwoLetterISOLanguageName` for the lang param.
- Decided:
  - Drop the `app4bro://` scheme entirely — the new appNotify message channel is the only path. Backward compatibility for older creatives is *not* preserved; the house ad server's HTML templates need to be updated to use `window.webkit.messageHandlers.appNotify.postMessage(...)` on iOS (matches Windows's WebView2 message API already in production).
  - Short-form fallback URL (`/route/<appId>/`) used only when AdvertisingId is empty. The server returns a generic wrapper config that doesn't need IDFA targeting — matches Android.
  - HouseBannerView is shared between `HouseNetwork` (banner) and `HouseInterNetwork` (interstitial). Single `isBanner: Bool` constructor parameter controls the `banner=true|false` query string the server uses to pick the creative size. The presentation surface (key window vs topmost view controller's view) is the only thing that differs between the two networks.
  - `decidePolicyFor` linkActivated handling kept as backstop — `WKUIDelegate.createWebViewWith` covers `window.open` and `<a target="_blank">`, but a plain `<a href>` without target stays as a top-level navigation, which `decidePolicyFor` catches.
- Build:
  - `swift build` green; 3/3 smoke tests still pass on iPhone 15 simulator.
  - Solitaire Klondike Free iOS rebuilt clean — downstream consumer unaffected (all changes are internal to the SDK).
- Next: server-side change to update the house-ad HTML template to emit `window.webkit.messageHandlers.appNotify.postMessage("ready"|"404"|"200")` instead of `app4bro://...` (must precede shipping). Phase 3 production hardening still open.

### 2026-06-29 (session 2) — Phase 2: wired into 5 Solitaire iOS Free targets, all 10 game targets BUILD SUCCEEDED

- Phase: 2 (Solitaire integration)
- Changed (Solitaire side — see `/Volumes/Projects/Projects/Solitaire/PORTING.md` session 26 for the full Solitaire-side changelog):
  - New `SwiftPort/iOS/Klondike/Klondike/AdController.swift` (shim, 98 LOC, symlinked into 4 other games).
  - Per-game `static let adConfig: AdConfig` added on each `<Game>ViewController.swift` with C#-lifted IDs.
  - 5 `AppDelegate.swift` files wired with `#if FREE_VERSION` blocks for `start/.resume`.
  - 5 Info.plist files gained `NSUserTrackingUsageDescription` (AdMob 11.x + ATT).
  - 5 `.xcodeproj/project.pbxproj` files edited:
    - `XCLocalSwiftPackageReference relativePath = "../../../../App4Bro/SDK/Apps4Bro.Swift/"`
    - `XCSwiftPackageProductDependency productName = Apps4Bro`
    - Added to project `packageReferences`, Free target `packageProductDependencies`, Free target `PBXFrameworksBuildPhase`.
    - HD targets explicitly skipped (no Apps4Bro).
- Decided:
  - Local SPM ref (relative path), not remote URL — matches the SolitaireEngine pattern in same projects.
  - Interstitial-only — banner networks ship in the SDK for other consumers, but Solitaire never registers them.
  - Per-zone NPA suffix (Q1) parsing preserved; Solitaire doesn't use it.
- Q1/Q2/Q3 resolved this session — see Open questions section above.
- Build: all 10 Solitaire iOS targets BUILD SUCCEEDED. Engine 280/280 traces still PASS.
- Pbxproj gotcha: first pass put Apps4Bro into the *first* PBXNativeTarget block. For Klondike + Spider that's the Free target (correct); for FreeCell + Pyramid + TriPeaks the .HD target is listed first. Corrective pass matched by `name = <Game>(.HD)?;` and moved the entries to the Free target. Detected by `error: missing required modules: 'GoogleMobileAds', 'UserMessagingPlatform'` on the Free build (Apps4Bro transitively imports both).
- Next:
  - **Phase 3** — production hardening. Live-traffic verification on simulator (AdMob test unit returns an interstitial in DEBUG) + production server handshake (real `app4bro` ID returns wrapper config from `ad.php`).
- Notes:
  - `relativePath` resolves from the `.xcodeproj`'s SOURCE_ROOT (the folder containing the `.xcodeproj`). Four levels up + `App4Bro/SDK/Apps4Bro.Swift/`.
  - The 5 Solitaire pbxproj files now have 9 `Apps4Bro` references each (1 file-ref, 1 prod-dep, 1 pkg-ref, 1 build-file, 1 frameworks-phase entry, 1 native-target dep, and 3 PBXLocalSwiftPackageReference traces in comments).
  - Apps4Bro module transitively imports `GoogleMobileAds` + `UserMessagingPlatform`. Solitaire's `AdController.swift` only does `import Apps4Bro`; Google SDK symbols leak through but Solitaire never touches them directly.

### 2026-06-29 (session 1) — Initial Swift port: 5 networks + core managers, SPM package builds + tests pass

- Phase: 0 (scoping) + 1 (core port)
- Changed:
  - **New: `Package.swift`** — SPM manifest. iOS 13+. Depends on `swift-package-manager-google-mobile-ads` (11.x) + `swift-package-manager-google-user-messaging-platform` (2.x). The two are *separate* packages — the GoogleMobileAds package no longer re-exports `UserMessagingPlatform`. First failing build called this out (`product 'UserMessagingPlatform' required by package 'apps4bro.swift' target 'Apps4Bro' not found`).
  - **New: `Sources/Apps4Bro/Apps4BroSDK.swift`** — static-API port of `Apps4BroSDK.cs`. ATT for IDFA on iOS 14+. `initCMP(viewController:)` drives `UMPConsentInformation` + `UMPConsentForm`. Server URL constants ported verbatim (positional `%@` / `%d` specifiers match C# `string.Format`).
  - **New: `Sources/Apps4Bro/AdManager.swift`** — port of `AdManager.cs`. URLSession replaces `NSURLConnection`. Wrapper-list parsing identical (`network:zoneId|adUnitId|...`). Cached fallback via `UserDefaults` under `app4bro_<appId>`. Same `OnInited / OnLoadedAd / OnFailedAd / OnShownAd / OnClickedAd` callback contract.
  - **New: `Sources/Apps4Bro/ReportManager.swift`** — port of `ReportManager.cs`. Serial `DispatchQueue` + URLSession replaces the C# `ConcurrentQueue` + polling background thread. Same FIFO + re-queue-on-failure behaviour; cancels DNS failures silently to match `WebExceptionStatus.NameResolutionFailure`.
  - **New: `Sources/Apps4Bro/AdContextDelegate.swift`** — protocol port of the C# interface.
  - **New: `Sources/Apps4Bro/AdNetworkHandler.swift`** — protocol port of the C# nested interface.
  - **New: `Sources/Apps4Bro/AdWrapper.swift`** — internal stats class port. Same counters.
  - **New: `Sources/Apps4Bro/Networks/AdMobNetwork.swift`** — interstitial. `GADInterstitialAd.load(withAdUnitID:request:completionHandler:)`. Presents from `topViewController()` (walks navigation/tab/presented stacks). C# `;true` NPA suffix in unit IDs honoured.
  - **New: `Sources/Apps4Bro/Networks/AdMobBannerNetwork.swift`** — banner. Adaptive ad size (replaces C# fixed 60 / 90 pt heights). Reads `Apps4BroSDK.settings[.showBannerOnTop]` and `.useBannerSuperview` to mirror C# `Apps4BroSettings`. Resolves host view controller via `manager.context as? UIViewController` matching the C# `(UIViewController)m_adManager.Context` cast.
  - **New: `Sources/Apps4Bro/Networks/HouseNetwork.swift`** — full-screen WKWebView banner. URL-encodes device model / OS version / locale / IDFA for the `house.php` request. `app4bro://200` scheme → close; any other host → server error. `WKNavigationActionPolicy.cancel` returns plus `UIApplication.shared.open` for link-out clicks. `@preconcurrency import WebKit` to suppress the iOS 17 `Sendable` warning.
  - **New: `Sources/Apps4Bro/Networks/HouseInterNetwork.swift`** — interstitial variant. Shares `HouseBannerView` rendering but presents as an overlay on top of `AdMobNetwork.topViewController()` instead of the key window.
  - **New: `Sources/Apps4Bro/Networks/DummyNetwork.swift`** — no-op stub.
  - **New: `Tests/Apps4BroTests/Apps4BroTests.swift`** — 3 smoke tests.
  - **New: `README.md`** — usage + module table + dropped-networks rationale.
  - **New: `PORTING.md`** — this file.

- Decided:
  - Use `GAD`-prefixed type names (AdMob 11.x). 12.x renames the entire surface and bumps to iOS 15 — accept the cost when a consumer needs iOS 15+ exclusively.
  - Split C# `HouseNetwork` into `HouseNetwork` (banner) + `HouseInterNetwork` (interstitial). Lets server route slots independently. Both share `HouseBannerView`.
  - Skip CTCarrier probe entirely. Returns nil on iOS 16+; sending empty operator field matches modern-device behaviour.
  - Drop `Console.WriteLine` — no log replacement. Restore with `os.Logger` if a consumer needs verbose diagnostics.
  - Drop all 6 legacy networks: iAd × 2 (service dead 2016), Inneractive × 2 (service dead), AdToApp, AdCash, Appodeal (Debug-only in C#), AdMobNetworkOld (pre-8.x AdMob API). Easy to revive by adding files + a `registerAdNetwork(...)` line; protocol is identical.

- Build:
  - `swift package resolve` — pulls 11.13.0 GoogleMobileAds + 2.7.0 UserMessagingPlatform (binary xcframeworks).
  - First build failed on `'UserMessagingPlatform' required by package ... not found in package 'swift-package-manager-google-mobile-ads'`. Root cause: that product lives in a *different* SPM package. Fixed `Package.swift` to declare both deps.
  - Second build failed on missing 12.x-style type names (`BannerView`, `InterstitialAd`, `FullScreenPresentingAd`, `Request`, `Extras`). Root cause: 11.x still uses `GAD`-prefixed names. Bulk-renamed; one edit briefly produced `GADGADBannerViewDelegate` from double-prefix, fixed in a follow-up.
  - Third build green with two warnings: `subscriberCellularProvider` deprecation and `WebKit` Sendable warning. Removed CoreTelephony entirely + added `@preconcurrency` on WebKit import. Final build green with **zero errors, zero warnings**.
  - `xcodebuild test -destination 'platform=iOS Simulator,name=iPhone 15'` — 3/3 tests pass in 0.005 s.

- Next:
  - Phase 2 — add as a local SPM dep to one Solitaire iOS target (likely `SwiftPort/iOS/Klondike/`). Adopt `AdContextDelegate` on a shared `AdController` class to be reused across the 5 games. Verify AdMob test unit returns a banner. Verify a production `app4bro` ID returns a valid wrapper config from `https://app4bro.runserver.net/app4bro/ad.php`.

- Notes:
  - 11.x → 12.x is a near-rewrite (every `GAD` prefix dropped) and bumps deployment target. Defer until consumer pressure forces it.
  - The User Messaging Platform SDK ships its module as `UserMessagingPlatform` despite the package being named `GoogleUserMessagingPlatform` and the SPM repo URL containing `google-user-messaging-platform`. Three different names for the same thing. Import statement uses the module name (`UserMessagingPlatform`); Package.swift uses the product name (`GoogleUserMessagingPlatform`).
  - C#'s ApplicationException-on-missing-app4bro-key became `fatalError` in Swift — same crash semantics, less ceremony. Consumers should ensure the first pair of `keys` is always `["app4bro", "<appId>"]`.
  - The `AdManager.init` ordering means `Apps4BroSDK.initialize(context:)` should be called first (to populate IDFA + platform) — otherwise the initial `ad.php` fetch sends an empty `id` query param. Matches C# behaviour.
  - User specified `App4Bro/SDK/Apps4Bro.Swift` as the destination directory, matching the existing `Apps4Bro.iOS/.Android/.Windows` sibling convention.
