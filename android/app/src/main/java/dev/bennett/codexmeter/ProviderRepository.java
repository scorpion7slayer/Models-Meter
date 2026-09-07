package dev.bennett.codexmeter;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Account-scoped usage and catalogs. A failed response never replaces a successful snapshot. */
public final class ProviderRepository {
    private ProviderRepository() { }
    private static final java.util.Map<Provider, String> attempts = new java.util.EnumMap<>(Provider.class);
    private static final java.util.Set<Provider> refreshing = java.util.EnumSet.noneOf(Provider.class);
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("models_meter_providers", 0);
    }
    public static Provider selected(Context context) {
        return Provider.from(preferences(context).getString("selected", "chatgpt"));
    }
    public static void select(Context context, Provider provider) {
        preferences(context).edit().putString("selected", provider.id).apply();
    }
    public static Provider widgetProvider(Context context, int id) {
        return Provider.from(preferences(context).getString("widget_" + id, "chatgpt"));
    }
    public static void setWidgetProvider(Context context, int id, Provider provider) {
        preferences(context).edit().putString("widget_" + id, provider.id).apply();
    }
    public static void deleteWidget(Context context, int id) {
        preferences(context).edit().remove("widget_" + id).apply();
    }
    public static boolean connected(Context context, Provider provider) {
        return provider == Provider.CHATGPT ? SecureTokenStore.isSignedIn(context)
                : ProviderCredentials.load(context, provider) != null;
    }
    public static boolean anyConnected(Context context) {
        for (Provider provider : Provider.values()) if (connected(context, provider)) return true;
        return false;
    }
    public static UsageSnapshot usage(Context context, Provider provider) {
        if (provider == Provider.CHATGPT) return AppPreferences.loadSnapshot(context);
        if (!connected(context, provider)) return null;
        try { return UsageSnapshot.fromJson(new JSONObject(preferences(context).getString(provider.id + "_usage", ""))); }
        catch (Exception ignored) { return null; }
    }
    public static ModelCatalogSnapshot catalog(Context context, Provider provider) {
        if (provider == Provider.CHATGPT) return ModelCatalogStore.load(context);
        if (!connected(context, provider)) return null;
        try { return ModelCatalogSnapshot.fromJson(new JSONObject(preferences(context).getString(provider.id + "_models", ""))); }
        catch (Exception ignored) { return null; }
    }
    static boolean hasModelKey(Context context, Provider provider) {
        JSONObject credentials = ProviderCredentials.load(context, provider);
        return credentials != null && !credentials.optString("model_key", "").isEmpty();
    }
    static String modelError(Context context, Provider provider) {
        return preferences(context).getString(provider.id + "_model_error", "");
    }
    public static String error(Context context, Provider provider) {
        return provider == Provider.CHATGPT ? AppPreferences.getLastError(context)
                : preferences(context).getString(provider.id + "_error", "");
    }
    static void connect(Context context, Provider provider, String credential, String modelKey) throws Exception {
        connect(context, provider, credential, modelKey, () -> false);
    }
    static void connect(Context context, Provider provider, String credential, String modelKey,
            java.util.function.BooleanSupplier cancelled) throws Exception {
        if (provider == Provider.CHATGPT) throw new IllegalArgumentException();
        credential = credential.trim(); modelKey = modelKey.trim();
        if (credential.isEmpty() || credential.contains("\r") || credential.contains("\n")
                || modelKey.contains("\r") || modelKey.contains("\n")) throw new Exception("Enter a valid credential.");
        JSONObject credentials = new JSONObject().put("session", credential).put("model_key", modelKey)
                .put("generation", UUID.randomUUID().toString());
        String attempt = credentials.getString("generation");
        synchronized (ProviderRepository.class) { attempts.put(provider, attempt); }
        // Network validation never holds the lock needed by a UI-thread disconnect.
        UsageSnapshot snapshot = fetchUsage(provider, credentials);
        synchronized (ProviderRepository.class) {
            if (cancelled.getAsBoolean() || !attempt.equals(attempts.get(provider)))
                throw new java.util.concurrent.CancellationException();
            ProviderCredentials.save(context, provider, credentials);
            preferences(context).edit().remove(provider.id + "_models").remove(provider.id + "_error")
                    .remove(provider.id + "_model_error").putString(provider.id + "_usage", snapshot.toJson().toString()).commit();
            select(context, provider);
        }
        refresh(context, provider);
    }
    public static synchronized void disconnect(Context context, Provider provider) {
        if (provider == Provider.CHATGPT) return;
        attempts.remove(provider);
        ProviderCredentials.clear(context, provider);
        preferences(context).edit().remove(provider.id + "_usage").remove(provider.id + "_models")
                .remove(provider.id + "_error").remove(provider.id + "_model_error").apply();
        ProviderNotifications.clear(context, provider);
        WidgetRenderer.updateAll(context);
        dev.bennett.codexmeter.wear.PhoneWearSync.pushAll(context);
    }
    public static UsageSnapshot refreshAll(Context context) throws Exception {
        Exception chatError = null;
        if (SecureTokenStore.isSignedIn(context)) {
            try { UsageApi.refreshAndCache(context); } catch (Exception error) { chatError = error; }
        }
        for (Provider provider : Provider.values())
            if (provider != Provider.CHATGPT && connected(context, provider)) refresh(context, provider);
        WidgetRenderer.updateAll(context);
        dev.bennett.codexmeter.wear.PhoneWearSync.pushAll(context);
        context.sendBroadcast(new android.content.Intent(AppConstants.ACTION_USAGE_UPDATED)
                .setPackage(context.getPackageName()), AppConstants.INTERNAL_PERMISSION);
        if (selected(context) == Provider.CHATGPT && chatError != null) throw chatError;
        if (selected(context) != Provider.CHATGPT && !error(context, selected(context)).isEmpty())
            throw new Exception(error(context, selected(context)));
        return usage(context, selected(context));
    }
    static void refresh(Context context, Provider provider) {
        JSONObject credentials;
        synchronized (ProviderRepository.class) {
            if (!refreshing.add(provider)) return;
            credentials = ProviderCredentials.load(context, provider);
            if (credentials == null) { refreshing.remove(provider); return; }
        }
        try {
            try {
                UsageSnapshot next = fetchUsage(provider, credentials);
                synchronized (ProviderRepository.class) {
                    if (!current(context, provider, credentials)) return;
                    UsageSnapshot previous = usage(context, provider);
                    preferences(context).edit().putString(provider.id + "_usage", next.toJson().toString())
                            .remove(provider.id + "_error").commit();
                    ProviderNotifications.usage(context, provider, previous, next);
                }
            } catch (Exception error) {
                synchronized (ProviderRepository.class) {
                    if (!current(context, provider, credentials)) return;
                    preferences(context).edit().putString(provider.id + "_error", error.getMessage()).apply();
                }
            }
            try {
                ModelCatalogSnapshot cached = catalog(context, provider);
                if (cached != null && System.currentTimeMillis() - cached.checkedAt < 900_000) return;
                List<CodexModelCatalog.Model> models = fetchModels(provider, credentials);
                if (models == null) return; // Cursor's model API needs a separate user API key.
                synchronized (ProviderRepository.class) {
                    if (!current(context, provider, credentials)) return;
                    ModelCatalogSnapshot previous = catalog(context, provider);
                    ModelCatalogSnapshot next = ModelCatalogSnapshot.update(previous,
                            credentials.getString("generation"), models, System.currentTimeMillis());
                    preferences(context).edit().putString(provider.id + "_models", next.toJson().toString())
                            .remove(provider.id + "_model_error").commit();
                    ProviderNotifications.models(context, provider, previous, next);
                }
            } catch (Exception error) {
                synchronized (ProviderRepository.class) {
                    if (current(context, provider, credentials))
                        preferences(context).edit().putString(provider.id + "_model_error", error.getMessage()).apply();
                }
            }
        } finally { synchronized (ProviderRepository.class) { refreshing.remove(provider); } }
    }
    private static boolean current(Context context, Provider provider, JSONObject requested) {
        JSONObject stored = ProviderCredentials.load(context, provider);
        return stored != null && !requested.optString("generation").isEmpty()
                && requested.optString("generation").equals(stored.optString("generation"));
    }
    private static UsageSnapshot fetchUsage(Provider provider, JSONObject credentials) throws Exception {
        String session = credentials.getString("session");
        String raw;
        if (provider == Provider.ANTHROPIC) {
            if (session.startsWith("sk-ant-oat")) {
                raw = get("https://api.anthropic.com/api/oauth/usage", "Authorization", "Bearer " + session, true);
            } else {
                String cookie = "sessionKey=" + session.replaceFirst("^sessionKey=", "");
                JSONArray orgs = new JSONArray(get("https://claude.ai/api/organizations", "Cookie", cookie, false));
                String org = credentials.optString("organization", "");
                if (org.isEmpty()) {
                    if (orgs.length() != 1) throw new Exception("Use a Claude Code OAuth token for an account with multiple organizations.");
                    org = orgs.getJSONObject(0).getString("uuid");
                }
                if (!org.matches("[a-zA-Z0-9-]+")) throw new Exception("Invalid organization.");
                raw = get("https://claude.ai/api/organizations/" + org + "/usage", "Cookie", cookie, false);
            }
        } else if (provider == Provider.CURSOR) {
            raw = get("https://cursor.com/api/usage-summary", "Cookie",
                    "WorkosCursorSessionToken=" + session.replaceFirst("^WorkosCursorSessionToken=", ""), false);
        } else {
            raw = get("https://opencode.ai/zen/go/v1/usage", "Authorization", "Bearer " + session, false);
        }
        return ProviderUsageParser.parse(provider, new JSONObject(raw), System.currentTimeMillis());
    }
    private static List<CodexModelCatalog.Model> fetchModels(Provider provider, JSONObject credentials) throws Exception {
        List<CodexModelCatalog.Model> result = new ArrayList<>();
        if (provider == Provider.ANTHROPIC) {
            // Public provider catalog, explicitly labelled as such in all surfaces.
            JSONObject models = new JSONObject(get("https://models.dev/api.json", null, null, false))
                    .getJSONObject("anthropic").getJSONObject("models");
            List<String> ids = new ArrayList<>();
            models.keys().forEachRemaining(ids::add);
            ids.sort(Comparator.comparing((String id) -> models.optJSONObject(id).optString("release_date", "")).reversed()
                    .thenComparing(Comparator.naturalOrder()));
            for (String id : ids) result.add(new CodexModelCatalog.Model(id, models.getJSONObject(id).optString("name", id)));
        } else {
            String key = credentials.optString("model_key", "");
            if (provider == Provider.CURSOR && key.isEmpty()) return null;
            JSONObject json = new JSONObject(get(provider == Provider.CURSOR ? "https://api.cursor.com/v1/models"
                    : "https://opencode.ai/zen/go/v1/models", provider == Provider.CURSOR ? "Authorization" : null,
                    provider == Provider.CURSOR ? "Bearer " + key : null, false));
            JSONArray models = json.getJSONArray(provider == Provider.CURSOR ? "models" : "data");
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.getJSONObject(i);
                String id = model.getString("id");
                result.add(new CodexModelCatalog.Model(id, model.optString("name", id)));
            }
        }
        return result;
    }
    private static String get(String endpoint, String header, String credential, boolean anthropicOAuth) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ModelsMeter/1.0.1");
        if (header != null) connection.setRequestProperty(header, credential);
        if (anthropicOAuth) connection.setRequestProperty("anthropic-beta", "oauth-2025-04-20");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new Exception(status == 401 || status == 403
                    ? "Connection expired or access denied. Reconnect this provider."
                    : "The provider could not be refreshed (HTTP " + status + ").");
            try (java.io.InputStream stream = connection.getInputStream()) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > 8 * 1024 * 1024) throw new Exception("Provider response is too large.");
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length > 8 * 1024 * 1024) throw new Exception("Provider response is too large.");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
