import SwiftUI
import ModelsMeterCore

struct ContentView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("language", store: UserDefaults(suiteName: MeterL10n.group)) private var language = "system"

    private var selectedLocale: Locale { (MeterLanguage(rawValue: language) ?? .system).locale }

    var body: some View {
        @Bindable var model = model

        NavigationStack {
            DashboardView()
        }
        .sheet(isPresented: $model.isShowingSettings) {
            NavigationStack {
                SettingsView()
            }
            .environment(\.locale, selectedLocale)
        }
        .sheet(isPresented: $model.isShowingReset) {
            NavigationStack {
                ResetCreditView()
            }
            .environment(\.locale, selectedLocale)
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $model.isShowingSignIn) {
            NavigationStack {
                SignInView()
            }
            .environment(\.locale, selectedLocale)
            .interactiveDismissDisabled(model.isAuthenticating)
        }
        .preferredColorScheme(model.settings.appearance.colorScheme)
        .onOpenURL { model.handle(url: $0) }
    }
}

#Preview {
    ContentView()
        .environment(AppModel.preview)
}
