import ModelsMeterCore
import Foundation
import Observation
import Security
import UserNotifications
import WidgetKit

private final class ProviderRedirectGuard: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping @Sendable (URLRequest?) -> Void) { completionHandler(nil) }
}

@MainActor @Observable final class ProviderStore {
    static let shared = ProviderStore()
    static let group = "group.dev.scorpion7slayer.modelsmeter"
    var selected: MeterProvider {
        didSet { UserDefaults.standard.set(selected.rawValue, forKey: "selected_provider") }
    }
    var snapshots: [MeterProvider: ProviderSnapshot] = [:]
    var errors: [MeterProvider: String] = [:]
    var refreshing = false
    var modelErrors: [MeterProvider: String] = [:]
    private var refreshingProviders: Set<MeterProvider> = []
    private var generations: [MeterProvider: UUID] = [:]
    private var connectedProviders: Set<MeterProvider> = []
    private let defaults = UserDefaults(suiteName: group) ?? .standard
    private let session: URLSession
    private init() {
        selected = MeterProvider(rawValue: UserDefaults.standard.string(forKey: "selected_provider") ?? "") ?? .chatgpt
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil; config.httpShouldSetCookies = false
        config.timeoutIntervalForRequest = 20; config.timeoutIntervalForResource = 40
        session = URLSession(configuration: config, delegate: ProviderRedirectGuard(), delegateQueue: nil)
        for provider in MeterProvider.allCases {
            if let data = defaults.data(forKey: "provider_" + provider.rawValue),
               let snapshot = try? JSONDecoder().decode(ProviderSnapshot.self, from: data) { snapshots[provider] = snapshot }
            if provider != .chatgpt && credential(provider) != nil { connectedProviders.insert(provider) }
        }
    }
    func connected(_ provider: MeterProvider) -> Bool { connectedProviders.contains(provider) }
    func hasModelKey(_ provider: MeterProvider) -> Bool { !(credential(provider)?["model_key"] ?? "").isEmpty }
    func connect(_ provider: MeterProvider, token: String, modelKey: String) async throws {
        let token = token.trimmingCharacters(in: .whitespacesAndNewlines)
        let modelKey = modelKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty, !token.contains("\n"), !token.contains("\r"), !modelKey.contains("\n"), !modelKey.contains("\r") else {
            throw ProviderError.message("Enter a valid credential.")
        }
        let generation = UUID(); generations[provider] = generation
        let values = ["session": token, "model_key": modelKey]
        let usage = try await fetchUsage(provider, values: values)
        try Task.checkCancellation()
        guard generations[provider] == generation else { return }
        try saveCredential(provider, values: values)
        connectedProviders.insert(provider)
        snapshots[provider] = ProviderSnapshot(usage: usage)
        errors[provider] = nil; modelErrors[provider] = nil; selected = provider; publish(provider)
        await refresh(provider)
    }
    func disconnect(_ provider: MeterProvider) {
        generations[provider] = UUID()
        SecItemDelete(query(provider) as CFDictionary)
        connectedProviders.remove(provider); snapshots[provider] = nil; errors[provider] = nil; modelErrors[provider] = nil
        defaults.removeObject(forKey: "provider_" + provider.rawValue)
        WidgetCenter.shared.reloadAllTimelines()
        let ids = notificationIDs(provider)
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ids)
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
    }
    func updateChatGPT(_ models: [CodexModel], account: String) {
        if UserDefaults.standard.string(forKey: "model_account") != account {
            snapshots[.chatgpt] = ProviderSnapshot()
            UserDefaults.standard.set(account, forKey: "model_account")
        }
        var snapshot = snapshots[.chatgpt] ?? ProviderSnapshot()
        _ = snapshot.updateModels(models)
        snapshots[.chatgpt] = snapshot; publish(.chatgpt)
    }
    func clearChatGPT() {
        snapshots[.chatgpt] = nil; defaults.removeObject(forKey: "provider_chatgpt")
        UserDefaults.standard.removeObject(forKey: "model_account")
        WidgetCenter.shared.reloadAllTimelines()
    }
    func refreshAll() async {
        guard !refreshing else { return }
        refreshing = true; defer { refreshing = false }
        for provider in MeterProvider.allCases where provider != .chatgpt && connected(provider) { await refresh(provider) }
    }
    private func refresh(_ provider: MeterProvider) async {
        guard refreshingProviders.insert(provider).inserted else { return }
        defer { refreshingProviders.remove(provider) }
        guard let values = credential(provider) else { return }
        let generation = generations[provider]
        var next = snapshots[provider] ?? ProviderSnapshot()
        do {
            let usage = try await fetchUsage(provider, values: values)
            guard generation == generations[provider], connected(provider) else { return }
            let previous = next.usage; next.usage = usage
            snapshots[provider] = next; errors[provider] = nil; publish(provider)
            await scheduleResets(provider, usage: usage)
            await notifyUsage(provider, old: previous, fresh: usage)
        } catch {
            guard generation == generations[provider], connected(provider) else { return }
            errors[provider] = MeterL10n.translate(error.localizedDescription)
        }
        do {
            if let checked = next.checkedAt, Date().timeIntervalSince(checked) < 900 { return }
            guard let models = try await fetchModels(provider, values: values), generation == generations[provider], connected(provider) else { return }
            modelErrors[provider] = nil
            let additions = next.updateModels(models)
            snapshots[provider] = next; publish(provider)
            let settings = AppSettingsStore().settings
            if settings.newModelAlertsEnabled, !additions.isEmpty {
                await notify(provider, kind: "models", title: MeterL10n.text("New models", "Nouveaux modèles"), body: additions.map(\.name).joined(separator: ", "))
            }
        } catch {
            if generation == generations[provider], connected(provider) {
                modelErrors[provider] = MeterL10n.translate(error.localizedDescription)
            }
        }
    }
    private func publish(_ provider: MeterProvider) {
        if let snapshot = snapshots[provider], let data = try? JSONEncoder().encode(snapshot) {
            defaults.set(data, forKey: "provider_" + provider.rawValue)
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
    private func notifyUsage(_ provider: MeterProvider, old: UsageSnapshot?, fresh: UsageSnapshot) async {
        guard let old else { return }
        let settings = AppSettingsStore().settings
        let oldWindows = [old.fiveHour, old.weekly, old.monthly]
        let newWindows = [fresh.fiveHour, fresh.weekly, fresh.monthly]
        for index in newWindows.indices {
            if index == 0 && settings.alertMetric == .weekly || index > 0 && settings.alertMetric == .fiveHour { continue }
            guard let before = oldWindows[index], let after = newWindows[index] else { continue }
            if before.remainingPercent > settings.alertThreshold && after.remainingPercent <= settings.alertThreshold {
                await notify(provider, kind: "low.\(index)", title: MeterL10n.text("Allowance running low", "Quota bientôt épuisé"), body: "\(Int(after.remainingPercent))%")
            } else if settings.unexpectedRefillAlertsEnabled && before.usedPercent - after.usedPercent >= 10 {
                await notify(provider, kind: "refill.\(index)", title: MeterL10n.text("Allowance replenished", "Quota rechargé"), body: "\(Int(after.remainingPercent))%")
            }
        }
    }
    private func notify(_ provider: MeterProvider, kind: String, title: String, body: String) async {
        guard AppSettingsStore().settings.notificationsEnabled else { return }
        let content = UNMutableNotificationContent()
        content.title = provider.title + " · " + title; content.body = body; content.sound = .default
        content.userInfo = ["provider": provider.rawValue]
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "models-meter." + provider.rawValue + "." + kind, content: content, trigger: nil))
    }
    private func notificationIDs(_ provider: MeterProvider) -> [String] {
        let prefix = "models-meter." + provider.rawValue
        return [prefix, prefix + ".models"] + (0..<3).flatMap { index in
            ["reset", "low", "refill"].map { prefix + "." + $0 + "." + String(index) }
        }
    }
    func updateNotificationSettings() async {
        let center = UNUserNotificationCenter.current()
        for provider in MeterProvider.allCases where provider != .chatgpt {
            if !AppSettingsStore().settings.notificationsEnabled {
                center.removePendingNotificationRequests(withIdentifiers: notificationIDs(provider))
                center.removeDeliveredNotifications(withIdentifiers: notificationIDs(provider))
            } else if let usage = snapshots[provider]?.usage, connected(provider) {
                await scheduleResets(provider, usage: usage)
            }
        }
    }
    private func scheduleResets(_ provider: MeterProvider, usage: UsageSnapshot) async {
        let settings = AppSettingsStore().settings
        let center = UNUserNotificationCenter.current()
        let generation = generations[provider]
        let windows = [usage.fiveHour, usage.weekly, usage.monthly]
        for index in windows.indices {
            let id = "models-meter." + provider.rawValue + ".reset." + String(index)
            center.removePendingNotificationRequests(withIdentifiers: [id])
            guard settings.notificationsEnabled, connected(provider),
                  !(index == 0 && settings.alertMetric == .weekly || index > 0 && settings.alertMetric == .fiveHour),
                  let reset = windows[index]?.effectiveResetDate(relativeTo: usage.fetchedAt), reset > .now else { continue }
            let content = UNMutableNotificationContent()
            content.title = provider.title + " · " + MeterL10n.text("Scheduled allowance reset", "Réinitialisation prévue du quota")
            content.body = MeterL10n.text("Your allowance should be available again. Open Models Meter to refresh.", "Votre quota devrait être à nouveau disponible. Ouvrez Models Meter pour l’actualiser.")
            content.sound = .default; content.userInfo = ["provider": provider.rawValue]
            let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: reset)
            try? await center.add(UNNotificationRequest(identifier: id, content: content,
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)))
            if generation != generations[provider] || !connected(provider) || !AppSettingsStore().settings.notificationsEnabled {
                center.removePendingNotificationRequests(withIdentifiers: [id]); return
            }
        }
    }

    private func fetchUsage(_ provider: MeterProvider, values: [String: String]) async throws -> UsageSnapshot {
        let token = values["session"] ?? ""
        let data: Data
        switch provider {
        case .anthropic:
            if token.hasPrefix("sk-ant-oat") {
                data = try await get("https://api.anthropic.com/api/oauth/usage", headers: ["Authorization": "Bearer " + token, "anthropic-beta": "oauth-2025-04-20"])
            } else {
                let cookie = token.hasPrefix("sessionKey=") ? token : "sessionKey=" + token
                let orgData = try await get("https://claude.ai/api/organizations", headers: ["Cookie": cookie])
                guard let orgs = try JSONSerialization.jsonObject(with: orgData) as? [[String: Any]], orgs.count == 1,
                      let org = orgs.first?["uuid"] as? String, org.range(of: "^[a-zA-Z0-9-]+$", options: .regularExpression) != nil else {
                    throw ProviderError.message("Use a Claude Code OAuth token for an account with multiple organizations.")
                }
                data = try await get("https://claude.ai/api/organizations/\(org)/usage", headers: ["Cookie": cookie])
            }
        case .cursor:
            data = try await get("https://cursor.com/api/usage-summary", headers: ["Cookie": token.hasPrefix("WorkosCursorSessionToken=") ? token : "WorkosCursorSessionToken=" + token])
        case .opencodeGo:
            data = try await get("https://opencode.ai/zen/go/v1/usage", headers: ["Authorization": "Bearer " + token])
        case .chatgpt: throw ProviderError.message("Connect ChatGPT from Settings.")
        }
        return try ProviderUsageParser.parse(data, provider: provider)
    }
    private func fetchModels(_ provider: MeterProvider, values: [String: String]) async throws -> [CodexModel]? {
        if provider == .anthropic {
            let data = try await get("https://models.dev/api.json")
            guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let anthropic = root["anthropic"] as? [String: Any], let models = anthropic["models"] as? [String: [String: Any]] else { throw ProviderError.message("Invalid model catalog.") }
            return models.keys.sorted {
                let left = models[$0]?["release_date"] as? String ?? "", right = models[$1]?["release_date"] as? String ?? ""
                return left == right ? $0 < $1 : left > right
            }.map { CodexModel(id: $0, name: models[$0]?["name"] as? String ?? $0) }
        }
        let key = values["model_key"] ?? ""
        if provider == .cursor && key.isEmpty { return nil }
        let data = try await get(provider == .cursor ? "https://api.cursor.com/v1/models" : "https://opencode.ai/zen/go/v1/models", headers: provider == .cursor ? ["Authorization": "Bearer " + key] : [:])
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let models = json[provider == .cursor ? "models" : "data"] as? [[String: Any]] else { throw ProviderError.message("Invalid model catalog.") }
        return try models.map { model in
            guard let id = model["id"] as? String else { throw ProviderError.message("Invalid model catalog.") }
            return CodexModel(id: id, name: model["name"] as? String ?? id)
        }
    }
    private func get(_ endpoint: String, headers: [String: String] = [:]) async throws -> Data {
        var request = URLRequest(url: URL(string: endpoint)!)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ModelsMeter/1.0.1", forHTTPHeaderField: "User-Agent")
        for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw ProviderError.message("Invalid response.") }
        guard response.statusCode == 200 else {
            throw ProviderError.message(response.statusCode == 401 || response.statusCode == 403
                ? "Connection expired or access denied. Reconnect this provider."
                : "The provider could not be refreshed (HTTP \(response.statusCode)).")
        }
        guard data.count <= 8 * 1024 * 1024 else { throw ProviderError.message("Provider response is too large.") }
        return data
    }
    private func query(_ provider: MeterProvider) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "dev.scorpion7slayer.modelsmeter.providers", kSecAttrAccount as String: provider.rawValue]
    }
    private func credential(_ provider: MeterProvider) -> [String: String]? {
        var query = query(provider); query[kSecReturnData as String] = true
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return try? JSONDecoder().decode([String: String].self, from: data)
    }
    private func saveCredential(_ provider: MeterProvider, values: [String: String]) throws {
        let query = query(provider)
        let attributes: [String: Any] = [kSecValueData as String: try JSONEncoder().encode(values), kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            guard SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil) == errSecSuccess else { throw ProviderError.message("Could not save credentials.") }
        } else if status != errSecSuccess { throw ProviderError.message("Could not save credentials.") }
    }
    private enum ProviderError: LocalizedError {
        case message(String)
        var errorDescription: String? { if case .message(let text) = self { text } else { nil } }
    }
}
