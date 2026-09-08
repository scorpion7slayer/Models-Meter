import Foundation
import XCTest
@testable import CodexMeterCore

final class ProviderTests: XCTestCase {
    func testMalformedWindowDoesNotReplaceUsage() {
        for json in [#"{"five_hour":true}"#, #"{"five_hour":"bad"}"#] {
            XCTAssertThrowsError(try ProviderUsageParser.parse(Data(json.utf8), provider: .anthropic))
        }
    }
    func testLanguageFallback() {
        XCTAssertEqual(MeterLanguage.system.resolved(system: "fr-BE"), "fr")
        XCTAssertEqual(MeterLanguage.system.resolved(system: "en-US"), "en")
        XCTAssertEqual(MeterLanguage.system.resolved(system: "de-DE"), "en")
        XCTAssertEqual(MeterLanguage.fr.resolved(system: "de-DE"), "fr")
        XCTAssertEqual(MeterLanguage.en.resolved(system: "fr-FR"), "en")
    }
    func testAbsentClaudeWindowIsNotFullAllowance() throws {
        let data = Data(#"{"five_hour":null,"seven_day":{"utilization":42,"resets_at":null}}"#.utf8)
        let result = try ProviderUsageParser.parse(data, provider: .anthropic)
        XCTAssertNil(result.fiveHour)
        XCTAssertEqual(result.weekly?.usedPercent, 42)
        XCTAssertNil(result.resetCreditsAvailable)
        XCTAssertNil(result.weekly?.resetAt)
    }
    func testCursorUsesPercentageUnitsAndMonthlyBillingWindow() throws {
        let data = Data(#"""
        {"billingCycleStart":"2026-09-01T00:00:00Z","billingCycleEnd":"2026-10-01T00:00:00Z",
         "individualUsage":{"plan":{"totalPercentUsed":0.6,"used":2500,"limit":5000}}}
        """#.utf8)
        let result = try ProviderUsageParser.parse(data, provider: .cursor)
        XCTAssertNil(result.fiveHour)
        XCTAssertNil(result.weekly)
        XCTAssertEqual(result.monthly?.usedPercent, 1)
        XCTAssertEqual(result.monthly?.windowSeconds, 2_592_000)
    }
    func testGoPercentageUnitsAndResetOffset() throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let data = Data(#"{"rollingUsage":{"usagePercent":25,"resetInSec":7200},"weeklyUsage":{"usagePercent":0.6,"resetInSec":0}}"#.utf8)
        let result = try ProviderUsageParser.parse(data, provider: .opencodeGo, now: now)
        XCTAssertEqual(result.fiveHour?.usedPercent, 25)
        XCTAssertEqual(result.weekly?.usedPercent, 1)
        XCTAssertNil(result.weekly?.resetAt)
        XCTAssertEqual(result.fiveHour?.resetAt, now.addingTimeInterval(7200))
    }
    func testErrorsCannotBecomeUsage() {
        for provider in [MeterProvider.anthropic, .cursor, .opencodeGo] {
            XCTAssertThrowsError(try ProviderUsageParser.parse(Data(#"{"error":"unauthorized"}"#.utf8), provider: provider))
        }
    }
    func testModelBaselineRenamesAndReappearance() {
        var snapshot = ProviderSnapshot()
        let start = Date(timeIntervalSince1970: 1000)
        XCTAssertTrue(snapshot.updateModels([.init(id: "a", name: "A")], now: start).isEmpty)
        XCTAssertEqual(snapshot.updateModels([.init(id: "a", name: "Renamed"), .init(id: "b", name: "B")], now: start.addingTimeInterval(1)).map(\.id), ["b"])
        XCTAssertEqual(snapshot.models.map(\.id), ["b", "a"])
        _ = snapshot.updateModels([], now: start.addingTimeInterval(2))
        XCTAssertTrue(snapshot.updateModels([.init(id: "a", name: "A")], now: start.addingTimeInterval(3)).isEmpty)
        XCTAssertEqual(snapshot.baselineAt, start)
    }
}
