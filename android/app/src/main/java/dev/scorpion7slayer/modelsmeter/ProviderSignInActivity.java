package dev.scorpion7slayer.modelsmeter;

import android.content.Context;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/** Provider-owned login page. No JavaScript bridge, password interception, or persistent session cookies. */
public final class ProviderSignInActivity extends AppCompatActivity {
    private WebView browser;
    private Provider provider;
    private String attempted = "";
    private boolean connecting;
    private volatile boolean cancelled;
    private TextView status;
    @Override protected void attachBaseContext(Context context) { super.attachBaseContext(L10n.localized(context)); }
    @Override protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this); super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        provider = Provider.from(getIntent().getStringExtra("provider"));
        if (provider != Provider.ANTHROPIC && provider != Provider.CURSOR) { finish(); return; }
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        status = Ui.text(this, provider == Provider.ANTHROPIC ? "claude.ai" : "cursor.com", 14, Ui.mainText(Ui.isDark(this)));
        status.setPadding(16, 20, 16, 12); content.addView(status);
        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true); cookies.setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !"https".equals(request.getUrl().getScheme());
            }
            @Override public void onPageFinished(WebView view, String url) {
                android.net.Uri location = android.net.Uri.parse(url);
                if (!connecting) status.setText(location.getHost());
                captureSession();
            }
        });
        content.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(content);
        // WebView has no incognito mode: remove its session data before and after this bounded login.
        cookies.removeAllCookies(removed -> {
            if (!isFinishing() && !isDestroyed()) browser.loadUrl(provider == Provider.ANTHROPIC
                    ? "https://claude.ai/login" : "https://cursor.com/dashboard");
        });
    }
    private void captureSession() {
        if (connecting || isFinishing()) return;
        String origin = provider == Provider.ANTHROPIC ? "https://claude.ai" : "https://cursor.com";
        String key = provider == Provider.ANTHROPIC ? "sessionKey" : "WorkosCursorSessionToken";
        String header = CookieManager.getInstance().getCookie(origin);
        if (header == null) return;
        for (String pair : header.split(";")) {
            String value = pair.trim();
            if (!value.startsWith(key + "=")) continue;
            String token = value.substring(key.length() + 1);
            if (token.isEmpty() || token.equals(attempted)) return;
            attempted = token; connecting = true;
            status.setText(L10n.text(this, "Connecting…", "Connexion…"));
            new Thread(() -> {
                try {
                    ProviderRepository.connect(getApplicationContext(), provider, token,
                            getIntent().getStringExtra("model_key") == null ? "" : getIntent().getStringExtra("model_key"), () -> cancelled);
                    RefreshScheduler.schedulePeriodic(getApplicationContext());
                    WidgetRenderer.updateAll(getApplicationContext());
                    runOnUiThread(() -> { setResult(RESULT_OK); finish(); });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        connecting = false;
                        status.setText(Translations.t(error.getMessage()));
                    });
                }
            }, "provider-browser-login").start();
            return;
        }
    }
    @Override protected void onDestroy() {
        cancelled = true;
        if (browser != null) {
            browser.stopLoading(); browser.clearCache(true); browser.clearHistory(); browser.destroy();
            CookieManager.getInstance().removeAllCookies(null);
            android.webkit.WebStorage.getInstance().deleteAllData();
        }
        super.onDestroy();
    }
}
