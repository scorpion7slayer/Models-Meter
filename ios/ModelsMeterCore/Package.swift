// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "ModelsMeterCore",
    platforms: [
        .iOS("26.0"),
    ],
    products: [
        .library(
            name: "ModelsMeterCore",
            targets: ["ModelsMeterCore"]
        ),
    ],
    targets: [
        .target(name: "ModelsMeterCore"),
        .testTarget(
            name: "ModelsMeterCoreTests",
            dependencies: ["ModelsMeterCore"],
            resources: [.process("Fixtures")]
        ),
    ],
    swiftLanguageModes: [.v6]
)
