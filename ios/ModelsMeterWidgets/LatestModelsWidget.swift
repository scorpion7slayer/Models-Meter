import AppIntents
import ModelsMeterCore
import SwiftUI
import WidgetKit

enum WidgetProvider: String, AppEnum {
    case chatgpt, anthropic, cursor
    case opencodeGo = "opencode-go"
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Provider")
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .chatgpt: "ChatGPT", .anthropic: "Anthropic · Claude", .cursor: "Cursor", .opencodeGo: "OpenCode Go"
    ]
    var provider: MeterProvider { MeterProvider(rawValue: rawValue) ?? .chatgpt }
}

struct ModelsConfiguration: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Latest models"
    @Parameter(title: "Provider", default: .chatgpt) var provider: WidgetProvider
    @Parameter(title: "Appearance", default: .system) var appearance: WidgetAppearance
    @Parameter(title: "Accent", default: .blue) var accent: WidgetAccent
    @Parameter(title: "Surface opacity", default: .soft) var surfaceOpacity: WidgetSurfaceOpacity
}

struct ModelsEntry: TimelineEntry {
    let date: Date
    let provider: WidgetProvider
    let snapshot: ProviderSnapshot?
    var style: MeterWidgetConfigurationIntent = .preview
}

struct ModelsTimeline: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> ModelsEntry {
        ModelsEntry(date: .now, provider: .chatgpt, snapshot: nil)
    }
    func snapshot(for configuration: ModelsConfiguration, in context: Context) async -> ModelsEntry { entry(configuration) }
    func timeline(for configuration: ModelsConfiguration, in context: Context) async -> Timeline<ModelsEntry> {
        Timeline(entries: [entry(configuration)], policy: .after(.now.addingTimeInterval(900)))
    }
    private func entry(_ configuration: ModelsConfiguration) -> ModelsEntry {
        let provider = configuration.provider
        let data = UserDefaults(suiteName: MeterL10n.group)?.data(forKey: "provider_" + provider.rawValue)
        let snapshot = data.flatMap { try? JSONDecoder().decode(ProviderSnapshot.self, from: $0) }
        return ModelsEntry(date: .now, provider: provider, snapshot: snapshot,
            style: MeterWidgetConfigurationIntent(appearance: configuration.appearance,
                accent: configuration.accent, surfaceOpacity: configuration.surfaceOpacity))
    }
}

struct LatestModelsWidget: Widget {
    let kind = "ModelsMeter.LatestModels"
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: ModelsConfiguration.self, provider: ModelsTimeline()) { entry in
            MeterWidgetContainer(configuration: entry.style) {
                ModelsWidgetView(entry: entry)
            }
        }
        .configurationDisplayName("Latest models")
        .description("Latest model names from your chosen provider.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge])
    }
}

private struct ModelsWidgetView: View {
    let entry: ModelsEntry
    @Environment(\.widgetFamily) private var family
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "gauge.with.dots.needle.67percent")
                Text(entry.provider.provider.title).lineLimit(1)
            }.font(.caption).foregroundStyle(.secondary)
            Text(MeterL10n.text("Latest models", "Derniers modèles")).font(.headline)
            if let snapshot = entry.snapshot, !snapshot.models.isEmpty {
                ForEach(snapshot.models.prefix(family == .systemLarge || family == .systemExtraLarge ? 9 : 3)) { model in
                    Text(model.name).font(.subheadline.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.75)
                }
                Spacer(minLength: 0)
                if let checked = snapshot.checkedAt { Text(checked, style: .relative).font(.caption2).foregroundStyle(.secondary) }
            } else {
                Text(MeterL10n.text("Open the app to load models", "Ouvrir l’app pour charger les modèles"))
                    .font(.caption).foregroundStyle(.secondary)
            }
        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .widgetURL(URL(string: "modelsmeter://models?provider=" + entry.provider.rawValue))
    }
}
