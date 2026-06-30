import Foundation

/// Minimal Android-Log-compatible facade. Mirrors `android.util.Log.d/w/e(TAG, msg)`
/// so call sites can be ported almost verbatim from the Java SDK without picking
/// up a heavier logging dependency. Output goes through `NSLog`, which lands in
/// both Console.app and the Xcode console.
///
/// Levels:
///   d = debug    (normal lifecycle: init, load, show, click) — stripped in release
///   w = warning  (recoverable misuse: hideAd before init, network errors)   — always on
///   e = error    (programmer errors: loadAd before init)                    — always on
///
/// `@autoclosure` defers string interpolation until we decide to log. Combined
/// with `#if DEBUG`, debug log call sites compile down to zero in release builds
/// (no NSLog, no interpolation, no `[%@]` format args) — matching Android's
/// `BuildConfig.DEBUG`-gated `Log.d`.
public enum Log {
    public static func d(_ tag: String, _ message: @autoclosure () -> String) {
        #if DEBUG
        NSLog("[%@] %@", tag, message())
        #endif
    }
    public static func w(_ tag: String, _ message: @autoclosure () -> String) {
        NSLog("[%@] W/%@", tag, message())
    }
    public static func e(_ tag: String, _ message: @autoclosure () -> String) {
        NSLog("[%@] E/%@", tag, message())
    }
}
