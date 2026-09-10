import SwiftUI
import ModelsMeterCore
import UserNotifications

extension Notification.Name {
    static let modelsMeterOpenRoute = Notification.Name("modelsMeterOpenRoute")
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let content = response.notification.request.content
        if let token = content.userInfo["token"] as? String {
            await NotificationCoordinator().markCreditExpiryAnnounced(token: token)
        }

        let route: String?
        if response.actionIdentifier == NotificationDeduplication.useResetActionIdentifier {
            route = "reset"
        } else if let value = content.userInfo[NotificationDeduplication.routeUserInfoKey] as? String {
            route = value
        } else {
            route = nil
        }

        let provider = content.userInfo["provider"] as? String
        guard route != nil || provider != nil else { return }
        await MainActor.run {
            NotificationCenter.default.post(
                name: .modelsMeterOpenRoute,
                object: nil,
                userInfo: ["route": route ?? "dashboard", "provider": provider ?? "chatgpt"]
            )
        }
    }
}

@main
struct ModelsMeterApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = AppModel()
    @AppStorage("language", store: UserDefaults(suiteName: MeterL10n.group)) private var language = "system"

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(model)
                .environment(\.locale, (MeterLanguage(rawValue: language) ?? .system).locale)
                .onReceive(NotificationCenter.default.publisher(for: .modelsMeterOpenRoute)) { note in
                    if let id = note.userInfo?["provider"] as? String,
                       let provider = MeterProvider(rawValue: id) { ProviderStore.shared.selected = provider }
                    if let route = note.userInfo?["route"] as? String {
                        model.handle(route: route)
                    }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await model.sceneBecameActive() }
            } else {
                model.sceneBecameInactive()
            }
        }
    }
}
