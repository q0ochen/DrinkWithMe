// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "DrinkWithMeCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "DrinkWithMeCore", targets: ["DrinkWithMeCore"])
    ],
    targets: [
        .target(name: "DrinkWithMeCore"),
        .testTarget(name: "DrinkWithMeCoreTests", dependencies: ["DrinkWithMeCore"])
    ]
)
