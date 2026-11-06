// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "AeatKeychain",
    platforms: [.macOS(.v13)],
    products: [.executable(name: "verifactu-query-keychain", targets: ["AeatKeychain"])],
    targets: [
        .executableTarget(name: "AeatKeychain"),
        .testTarget(name: "AeatKeychainTests", dependencies: ["AeatKeychain"]),
    ],
    swiftLanguageModes: [.v5]
)
