import SwiftUI

struct AboutView: View {
    @Environment(AppModel.self) private var model
    @State private var versionTaps = 0
    @State private var unlockHint: String?
    @State private var showingDiagnostics = false

    private static let diagnosticTaps = 7

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }

    var body: some View {
        List {
            Section {
                VStack(spacing: 12) {
                    Image(systemName: "gauge.with.dots.needle.67percent")
                        .font(.system(size: 52))
                        .foregroundStyle(.tint)
                        .symbolRenderingMode(.hierarchical)
                        .accessibilityHidden(true)
                    Text("Models Meter")
                        .font(.title2.bold())
                    Text("Version \(version)")
                        .foregroundStyle(.secondary)
                    Text("Usage, quotas and new models for ChatGPT, Anthropic, Cursor and OpenCode Go.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    if let unlockHint {
                        Text(unlockHint)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
                .contentShape(Rectangle())
                .onTapGesture(perform: handleVersionTap)
                .accessibilityAddTraits(.isButton)
                .accessibilityHint("Tap repeatedly to unlock diagnostics.")
            }

            Section("Source and credits") {
                Text("Models Meter is an independent fork of Codex Meter.")
                Link("Models Meter on GitHub", destination: URL(string: "https://github.com/scorpion7slayer/Models-Meter")!)
                Link("Theo · scorpion7slayer — Models Meter developer", destination: URL(string: "https://github.com/scorpion7slayer")!)
                Link("Original project · Codex Meter", destination: URL(string: "https://github.com/BenItBuhner/Codex-Meter")!)
                Link("BenIt Buhner — original project developer", destination: URL(string: "https://github.com/BenItBuhner")!)
                Link("That Josh Guy — original project developer and designer", destination: URL(string: "https://tjg.gg")!)
                Link("iOS development — Filip Bukovina", destination: URL(string: "https://github.com/FBukovina")!)
                LabeledContent("License", value: "MIT")
                NavigationLink("Open-source notices") {
                    OpenSourceNoticesView()
                }
                Link("OpenAI", destination: URL(string: "https://openai.com")!)
            }

            Section("Important") {
                Text("Models Meter is not affiliated with OpenAI, Anthropic, Cursor or OpenCode. Provider names and logos belong to their respective owners. Account routes may change without notice.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("About")
        .navigationDestination(isPresented: $showingDiagnostics) {
            DiagnosticsView()
        }
    }

    private func handleVersionTap() {
        versionTaps += 1
        let remaining = Self.diagnosticTaps - versionTaps
        if remaining <= 0 {
            unlockHint = "Diagnostics unlocked."
            model.unlockDiagnostics()
            showingDiagnostics = true
            versionTaps = 0
        } else if remaining <= 3 {
            unlockHint = remaining == 1
                ? "1 more tap for diagnostics."
                : "\(remaining) more taps for diagnostics."
        }
    }
}

struct OpenSourceNoticesView: View {
    private let mitNotice = """
    MIT License

    Copyright (c) 2026 Bennett

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
    """

    var body: some View {
        ScrollView {
            Text(mitNotice)
                .font(.footnote.monospaced())
                .textSelection(.enabled)
                .frame(maxWidth: 760, alignment: .leading)
                .padding(22)
        }
        .navigationTitle("Open-source notices")
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct PrivacyPolicyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Privacy policy")
                    .font(.largeTitle.bold())
                Text("Models Meter has no analytics, advertising, app account or relay server. Authentication and usage requests go directly to the provider you connect. The public Anthropic model catalog comes from models.dev.")
                Text("Credentials")
                    .font(.title3.bold())
                Text("Tokens, API keys and session credentials are stored in Apple Keychain on this device. Login pages use a temporary browser session. Widgets receive only display data, never credentials or account identifiers.")
                Text("Local storage")
                    .font(.title3.bold())
                Text("The last successful usage response and bounded allowance samples are stored locally for offline display, burn charts, and pace estimates. You can clear history separately; signing out removes credentials, cached account data, history, background requests, and scheduled notifications.")
                Text("Settings exports contain preferences and dashboard choices, never credentials or usage samples.")
                Text("Demo mode")
                    .font(.title3.bold())
                Text("Demo mode is entirely local and does not contact OpenAI.")
                Text("Last updated September 7, 2026")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: 700, alignment: .leading)
            .padding(22)
        }
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
    }
}
