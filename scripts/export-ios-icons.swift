#!/usr/bin/env swift
// Export the repository's Models Meter vector mark as opaque iOS icon variants.
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let output = URL(fileURLWithPath: CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "ios/ModelsMeter/Assets.xcassets/AppIcon.appiconset", isDirectory: true)
let variants: [(String, UInt32, UInt32, UInt32, UInt32)] = [
    ("AppIcon.png", 0xF3F6FA, 0xD3DDE5, 0x007D77, 0x142335),
    ("AppIcon-Dark.png", 0x0D1622, 0x293747, 0x5EE9CD, 0xF1F7FF),
    ("AppIcon-Tinted.png", 0x101010, 0x383838, 0xBBBBBB, 0xFFFFFF)
]
func color(_ rgb: UInt32) -> CGColor {
    CGColor(red: CGFloat((rgb >> 16) & 255) / 255, green: CGFloat((rgb >> 8) & 255) / 255, blue: CGFloat(rgb & 255) / 255, alpha: 1)
}
for (name, background, track, accent, mark) in variants {
    guard let context = CGContext(data: nil, width: 1024, height: 1024, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { fatalError("Could not create icon context") }
    context.setFillColor(color(background)); context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))
    context.translateBy(x: 0, y: 1024); context.scaleBy(x: 1024 / 108, y: -1024 / 108)
    context.setLineCap(.round); context.setLineJoin(.round); context.setLineWidth(5)
    context.setStrokeColor(color(track)); context.addArc(center: CGPoint(x: 54, y: 54), radius: 31,
        startAngle: .pi * 0.75, endAngle: .pi * 2.25, clockwise: false); context.strokePath()
    context.setStrokeColor(color(accent)); context.addArc(center: CGPoint(x: 54, y: 54), radius: 31,
        startAngle: .pi * 0.75, endAngle: .pi * 2, clockwise: false); context.strokePath()
    context.setStrokeColor(color(mark)); context.setLineWidth(7)
    context.move(to: CGPoint(x: 40, y: 66)); context.addLines(between: [CGPoint(x: 40, y: 66), CGPoint(x: 40, y: 44), CGPoint(x: 54, y: 58), CGPoint(x: 68, y: 44), CGPoint(x: 68, y: 66)]); context.strokePath()
    guard let image = context.makeImage(), let destination = CGImageDestinationCreateWithURL(output.appendingPathComponent(name) as CFURL, UTType.png.identifier as CFString, 1, nil) else { fatalError("Could not export icon") }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("Could not save icon") }
}
