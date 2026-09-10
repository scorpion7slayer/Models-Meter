import ModelsMeterCore
import SwiftUI

struct ProvidersView: View {
    @Environment(AppModel.self) private var model
    @State private var store = ProviderStore.shared
    var body: some View {
        List {
            Section {
                ForEach(MeterProvider.allCases) { provider in
                    if provider == .chatgpt {
                        LabeledContent("ChatGPT", value: model.mode == .live ? MeterL10n.text("Connected", "Connecté") : MeterL10n.text("Connect in Settings", "Connecter dans Réglages"))
                    } else {
                        NavigationLink { ProviderConnectionView(provider: provider) } label: {
                            VStack(alignment: .leading) {
                                Text(provider.title)
                                Text(store.connected(provider) ? MeterL10n.text("Connected", "Connecté") : MeterL10n.text("Connect account", "Connecter le compte"))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            } footer: {
                Text(MeterL10n.text("Credentials stay in this device’s Keychain. Widgets receive only usage and model names.", "Les identifiants restent dans le trousseau de cet appareil. Les widgets reçoivent seulement les quotas et les noms des modèles."))
            }
        }.navigationTitle(MeterL10n.text("Providers", "Fournisseurs"))
    }
}

private struct ProviderConnectionView: View {
    let provider: MeterProvider
    @State private var store = ProviderStore.shared
    @State private var token = ""
    @State private var modelKey = ""
    @State private var browser = false
    @State private var working = false
    @State private var error: String?
    var body: some View {
        Form {
            if provider != .opencodeGo {
                Section { Button(MeterL10n.text("Sign in to ", "Se connecter à ") + provider.title) { browser = true } }
            }
            Section {
                SecureField(provider == .opencodeGo ? "API key" : "Session / OAuth token", text: $token)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                if provider == .cursor {
                    SecureField(MeterL10n.text("Cursor API key (optional)", "Clé API Cursor (facultatif)"), text: $modelKey)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                Button(MeterL10n.text("Connect", "Connecter")) {
                    working = true; error = nil
                    Task {
                        defer { working = false }
                        do { try await store.connect(provider, token: token, modelKey: modelKey); token = ""; modelKey = "" }
                        catch { self.error = MeterL10n.translate(error.localizedDescription) }
                    }
                }.disabled(working || token.isEmpty)
                if working { ProgressView() }
                if store.connected(provider) {
                    Button(MeterL10n.text("Disconnect", "Déconnecter"), role: .destructive) { store.disconnect(provider) }
                }
            } footer: { Text(help) }
            if let error { Section { Text(error).foregroundStyle(.red) } }
        }.navigationTitle(provider.title)
            .sheet(isPresented: $browser) { ProviderBrowserSignIn(provider: provider, modelKey: modelKey) }
    }
    private var help: String {
        switch provider {
        case .anthropic: MeterL10n.text("Use your Claude sessionKey cookie or Claude Code OAuth access token. An Anthropic API key does not provide Claude subscription usage.", "Utilisez le cookie sessionKey de Claude ou le jeton OAuth de Claude Code. Une clé API Anthropic ne fournit pas les quotas de l’abonnement Claude.")
        case .cursor: MeterL10n.text("Use your WorkosCursorSessionToken cookie for subscription usage. A Cursor user API key enables the Cloud Agents model catalog.", "Utilisez le cookie WorkosCursorSessionToken pour les quotas. Une clé API utilisateur Cursor active le catalogue des modèles Cloud Agents.")
        default: MeterL10n.text("Use the OpenCode Go API key from your OpenCode workspace.", "Utilisez la clé API OpenCode Go de votre espace OpenCode.")
        }
    }
}

struct ProviderDashboardView: View {
    let provider: MeterProvider
    @State private var store = ProviderStore.shared
    var body: some View {
        if !store.connected(provider) {
            NavigationLink(MeterL10n.text("Connect ", "Connecter ") + provider.title) { ProvidersView() }
                .buttonStyle(.borderedProminent)
        } else {
            LatestModelsCard(provider: provider)
            if let snapshot = store.snapshots[provider]?.usage {
                ForEach(Array([snapshot.fiveHour, snapshot.weekly, snapshot.monthly].enumerated()), id: \.offset) { index, window in
                    if let window {
                        UsageMeterCard(
                            title: LocalizedStringKey(["5-hour", "Weekly", "Monthly"][index]),
                            systemImage: ["clock", "calendar", "calendar.badge.clock"][index],
                            window: window, accent: [Color.mint, .indigo, .orange][index], fetchedAt: snapshot.fetchedAt)

                    }
                }
                Text(snapshot.fetchedAt, style: .relative).font(.caption).foregroundStyle(.secondary)
            }
            if let error = store.errors[provider] { Text(error).font(.footnote).foregroundStyle(.orange) }
            Button(MeterL10n.text("Refresh", "Actualiser")) { Task { await store.refreshAll() } }
                .buttonStyle(.bordered).disabled(store.refreshing)
        }
    }
}

struct LatestModelsCard: View {
    let provider: MeterProvider
    @State private var store = ProviderStore.shared
    @State private var showAll = false
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(MeterL10n.text("Latest models", "Derniers modèles")).font(.title3.bold())
            let snapshot = store.snapshots[provider]
            if let snapshot, !snapshot.models.isEmpty {
                ForEach(snapshot.models.prefix(3)) { model in
                    Label {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(model.name).font(.headline)
                            Text(provider == .chatgpt ? MeterL10n.text("Available on your account", "Disponible sur votre compte") : MeterL10n.text("Provider catalog", "Catalogue du fournisseur"))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    } icon: { Image(systemName: "gauge.with.dots.needle.67percent") }
                }
                if let checked = snapshot.checkedAt {
                    Text(MeterL10n.text("Checked ", "Vérifié ") + checked.formatted(.relative(presentation: .named, unitsStyle: .wide).locale(MeterL10n.language.locale)))
                        .font(.caption).foregroundStyle(.secondary)
                }
                Button(MeterL10n.text("View all models", "Voir tous les modèles") + " (\(snapshot.models.count))") { showAll = true }
                    .buttonStyle(.bordered).frame(maxWidth: .infinity)
            } else {
                Text(provider == .cursor && !store.hasModelKey(provider) ? MeterL10n.text("Add a Cursor API key in Providers to load models.", "Ajoutez une clé API Cursor dans Fournisseurs pour charger les modèles.") : MeterL10n.text("Waiting for models", "En attente des modèles"))
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            if let error = store.modelErrors[provider] { Text(error).font(.caption).foregroundStyle(.orange) }
        }.frame(maxWidth: .infinity, alignment: .leading).padding(22)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))
            .sheet(isPresented: $showAll) {
                NavigationStack {
                    List {
                        if provider != .chatgpt {
                            Text(MeterL10n.text("Provider catalog. Availability depends on your plan.", "Catalogue du fournisseur. La disponibilité dépend de votre abonnement."))
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                        ForEach(store.snapshots[provider]?.models ?? []) { model in
                            VStack(alignment: .leading) { Text(model.name); if model.name != model.id { Text(model.id).font(.caption).foregroundStyle(.secondary) } }
                        }
                    }.navigationTitle(MeterL10n.text("Latest models", "Derniers modèles"))
                        .toolbar { Button(MeterL10n.text("Done", "Terminé")) { showAll = false } }
                }
            }
    }
}
