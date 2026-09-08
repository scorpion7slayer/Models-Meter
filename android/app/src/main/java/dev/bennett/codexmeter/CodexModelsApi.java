package dev.bennett.codexmeter;

import android.content.Context;
import android.os.SystemClock;
import java.net.URI;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;

/** Best-effort side refresh under UsageApi.NETWORK_LOCK, shared by foreground and workers. */
final class CodexModelsApi {
    // Codex protocol compatibility version (openai/codex rust-v0.153.3), not Meter's version.
    private static final String URL =
            "https://chatgpt.com/backend-api/codex/models?client_version=0.153.3";
    private static volatile String lastAccount;
    private static volatile long nextCheck;

    private CodexModelsApi() { }

    static void clearThrottle() {
        lastAccount = null;
        nextCheck = 0;
    }

    static void refreshLocked(Context context, AuthTokens tokens) throws Exception {
        if (tokens.accountId.isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (tokens.accountId.equals(lastAccount) && now < nextCheck) return;
        lastAccount = tokens.accountId;
        nextCheck = now + 15 * 60 * 1000L;
        HttpsURLConnection connection = (HttpsURLConnection) URI.create(URL).toURL().openConnection();
        try {
            UsageApi.applyHeaders(connection, tokens);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new Exception("Codex model catalog refresh failed (HTTP " + status + ").");
            }
            List<CodexModelCatalog.Model> models = CodexModelCatalog.parse(
                    OAuthClient.readBody(connection, status));
            // A sign-out may have happened while the request was in flight.
            AuthTokens current = SecureTokenStore.load(context);
            if (current == null || !tokens.accountId.equals(current.accountId)) return;
            ModelCatalogStore.save(context, tokens.accountId, models, System.currentTimeMillis());
            LatestModelsWidget.updateAll(context);
            ResetNotificationManager.onModelsUpdated(context, tokens.accountId, models);
        } finally {
            connection.disconnect();
        }
    }
}
