import Foundation
import CoreFoundation

public enum MeterProvider: String, Codable, CaseIterable, Sendable, Identifiable {
    case chatgpt, anthropic, cursor
    case opencodeGo = "opencode-go"
    public var id: String { rawValue }
    public var title: String {
        switch self {
        case .chatgpt: "ChatGPT"
        case .anthropic: "Anthropic · Claude"
        case .cursor: "Cursor"
        case .opencodeGo: "OpenCode Go"
        }
    }
}

public enum MeterLanguage: String, Codable, CaseIterable, Sendable, Identifiable {
    case system, fr, en
    public var id: String { rawValue }
    public func resolved(system: String = Locale.preferredLanguages.first ?? "en") -> String {
        let tag = self == .system ? system : rawValue
        return tag.lowercased().split(whereSeparator: { $0 == "-" || $0 == "_" }).first == "fr" ? "fr" : "en"
    }
    public var locale: Locale { Locale(identifier: resolved()) }
}

/// A sanitized snapshot shared with extensions. No credentials or remote account identifiers.
public struct ProviderSnapshot: Codable, Sendable, Equatable {
    public var usage: UsageSnapshot?
    public var models: [CodexModel]
    public var checkedAt: Date?
    public var firstSeen: [String: Date]
    public var baselineAt: Date?
    public init(usage: UsageSnapshot? = nil, models: [CodexModel] = [], checkedAt: Date? = nil,
                firstSeen: [String: Date] = [:], baselineAt: Date? = nil) {
        self.usage = usage; self.models = models; self.checkedAt = checkedAt
        self.firstSeen = firstSeen; self.baselineAt = baselineAt
    }
    public mutating func updateModels(_ available: [CodexModel], now: Date = Date()) -> [CodexModel] {
        let initialized = baselineAt != nil
        if !available.isEmpty && baselineAt == nil { baselineAt = now }
        let additions = initialized ? available.filter { firstSeen[$0.id] == nil } : []
        var ids = Set<String>()
        models = available.filter { ids.insert($0.id).inserted }
        for model in models where firstSeen[model.id] == nil { firstSeen[model.id] = now }
        // Stable API order inside each discovery batch.
        models = models.enumerated().sorted {
            let left = firstSeen[$0.element.id] ?? .distantPast
            let right = firstSeen[$1.element.id] ?? .distantPast
            return left == right ? $0.offset < $1.offset : left > right
        }.map(\.element)
        checkedAt = now
        return additions
    }
}

public enum ProviderUsageParser {
    public enum Failure: Error { case unsupportedResponse }
    public static func parse(_ data: Data, provider: MeterProvider, now: Date = Date()) throws -> UsageSnapshot {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw Failure.unsupportedResponse }
        var five: UsageWindow?, week: UsageWindow?, month: UsageWindow?
        switch provider {
        case .chatgpt: throw Failure.unsupportedResponse
        case .anthropic:
            guard json.keys.contains("five_hour") || json.keys.contains("seven_day") else { throw Failure.unsupportedResponse }
            five = try window(json["five_hour"], key: "utilization", seconds: 18_000)
            week = try window(json["seven_day"], key: "utilization", seconds: 604_800)
        case .cursor:
            guard let individual = json["individualUsage"] as? [String: Any],
                  let plan = individual["plan"] as? [String: Any] else { throw Failure.unsupportedResponse }
            var percent = number(plan["totalPercentUsed"])
            if percent == nil, let used = number(plan["used"]), let limit = number(plan["limit"]), limit > 0 {
                percent = used / limit * 100
            }
            if let percent {
                let end = date(json["billingCycleEnd"]), start = date(json["billingCycleStart"])
                let duration = end.flatMap { end in start.map { end.timeIntervalSince($0) } } ?? 2_592_000
                month = UsageWindow(usedPercent: Int(min(100, percent.rounded())), windowSeconds: Int64(max(1, duration)), resetAt: end)
            }
        case .opencodeGo:
            let usage = json["usage"] as? [String: Any]
            guard usage != nil || json.keys.contains("rollingUsage") else { throw Failure.unsupportedResponse }
            five = try goWindow(usage?["rolling"] ?? json["rollingUsage"], seconds: 18_000, now: now)
            week = try goWindow(usage?["weekly"] ?? json["weeklyUsage"], seconds: 604_800, now: now)
            month = try goWindow(usage?["monthly"] ?? json["monthlyUsage"], seconds: 2_592_000, now: now)
        }
        let limited = [five, week, month].compactMap { $0 }.contains { $0.usedPercent >= 100 }
        return UsageSnapshot(planType: provider.title, allowed: !limited, limitReached: limited,
                             fiveHour: five, weekly: week, monthly: month, fetchedAt: now)
    }
    private static func number(_ value: Any?) -> Double? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(),
              number.doubleValue.isFinite, number.doubleValue >= 0 else { return nil }
        return number.doubleValue
    }
    private static func date(_ value: Any?) -> Date? {
        guard let text = value as? String else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: text) ?? ISO8601DateFormatter().date(from: text)
    }
    private static func window(_ value: Any?, key: String, seconds: Int64) throws -> UsageWindow? {
        guard let value, !(value is NSNull) else { return nil }
        guard let object = value as? [String: Any] else { throw Failure.unsupportedResponse }
        guard let percent = number(object[key]) else { throw Failure.unsupportedResponse }
        return UsageWindow(usedPercent: Int(min(100, percent.rounded())), windowSeconds: seconds, resetAt: date(object["resets_at"]))
    }
    private static func goWindow(_ value: Any?, seconds: Int64, now: Date) throws -> UsageWindow? {
        guard let value, !(value is NSNull) else { return nil }
        guard let object = value as? [String: Any] else { throw Failure.unsupportedResponse }
        guard let percent = number(object["percentUsed"] ?? object["usagePercent"] ?? object["percent"]) else { throw Failure.unsupportedResponse }
        let reset = date(object["resetsAt"]) ?? number(object["resetInSec"]).flatMap { $0 > 0 ? now.addingTimeInterval($0) : nil }
        return UsageWindow(usedPercent: Int(min(100, percent.rounded())), windowSeconds: seconds, resetAt: reset)
    }
}

public enum MeterL10n {
    public static let group = "group.dev.scorpion7slayer.modelsmeter"
    public static var language: MeterLanguage {
        MeterLanguage(rawValue: UserDefaults(suiteName: group)?.string(forKey: "language") ?? "system") ?? .system
    }
    public static func text(_ en: String, _ fr: String) -> String { language.resolved() == "fr" ? fr : en }
    public static func translate(_ value: String) -> String {
        guard let path = Bundle.main.path(forResource: language.resolved(), ofType: "lproj"),
              let bundle = Bundle(path: path) else { return value }
        return bundle.localizedString(forKey: value, value: value, table: "Localizable")
    }
}
