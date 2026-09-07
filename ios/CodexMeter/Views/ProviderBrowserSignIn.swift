import CodexMeterCore
import SwiftUI
import WebKit

struct ProviderBrowserSignIn: View {
    let provider: MeterProvider
    let modelKey: String
    @Environment(\.dismiss) private var dismiss
    @State private var status = ""
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !status.isEmpty { Text(status).font(.caption).padding(10) }
                LoginBrowser(provider: provider, modelKey: modelKey, status: $status) { dismiss() }
            }.navigationTitle(provider.title)
                .toolbar { Button(MeterL10n.text("Cancel", "Annuler")) { dismiss() } }
        }
    }
}

private struct LoginBrowser: UIViewRepresentable {
    let provider: MeterProvider
    let modelKey: String
    @Binding var status: String
    let completed: () -> Void
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        let web = WKWebView(frame: .zero, configuration: configuration)
        web.navigationDelegate = context.coordinator
        web.load(URLRequest(url: URL(string: provider == .anthropic ? "https://claude.ai/login" : "https://cursor.com/dashboard")!))
        return web
    }
    func updateUIView(_ web: WKWebView, context: Context) { }
    static func dismantleUIView(_ web: WKWebView, coordinator: Coordinator) { coordinator.task?.cancel(); web.stopLoading(); web.navigationDelegate = nil }
    @MainActor final class Coordinator: NSObject, WKNavigationDelegate {
        let parent: LoginBrowser
        var attempted = ""
        var connecting = false
        var task: Task<Void, Never>?
        init(_ parent: LoginBrowser) { self.parent = parent }
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            decisionHandler(navigationAction.request.url?.scheme == "https" ? .allow : .cancel)
        }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard !connecting else { return }
            parent.status = webView.url?.host ?? ""
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
                guard let self, !self.connecting else { return }
                let host = self.parent.provider == .anthropic ? "claude.ai" : "cursor.com"
                let name = self.parent.provider == .anthropic ? "sessionKey" : "WorkosCursorSessionToken"
                guard let cookie = cookies.first(where: { $0.name == name && ($0.domain == host || $0.domain == "." + host) }),
                      !cookie.value.isEmpty, cookie.value != self.attempted else { return }
                self.attempted = cookie.value; self.connecting = true
                self.parent.status = MeterL10n.text("Connecting…", "Connexion…")
                self.task = Task { @MainActor in
                    do {
                        try await ProviderStore.shared.connect(self.parent.provider, token: cookie.value, modelKey: self.parent.modelKey)
                        self.parent.completed()
                    } catch {
                        self.parent.status = MeterL10n.translate(error.localizedDescription)
                        self.connecting = false
                    }
                }
            }
        }
    }
}
