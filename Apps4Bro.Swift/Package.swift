// swift-tools-version: 5.9
import PackageDescription

// Swift port of App4Bro/SDK/Apps4Bro.iOS (Xamarin C#). See README for status.
let package = Package(
    name: "Apps4Bro",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "Apps4Bro", targets: ["Apps4Bro"]),
    ],
    dependencies: [
        // GoogleMobileAds (AdMob 11.x) and the consent SDK are shipped as separate
        // SPM packages. Both are needed: GoogleMobileAds for the interstitial/banner
        // networks, GoogleUserMessagingPlatform for `Apps4BroSDK.initCMP`.
        .package(url: "https://github.com/googleads/swift-package-manager-google-mobile-ads.git",
                 from: "11.0.0"),
        .package(url: "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git",
                 from: "2.0.0"),
    ],
    targets: [
        .target(
            name: "Apps4Bro",
            dependencies: [
                .product(name: "GoogleMobileAds",
                         package: "swift-package-manager-google-mobile-ads"),
                .product(name: "GoogleUserMessagingPlatform",
                         package: "swift-package-manager-google-user-messaging-platform"),
            ]
        ),
        .testTarget(
            name: "Apps4BroTests",
            dependencies: ["Apps4Bro"]
        ),
    ]
)
