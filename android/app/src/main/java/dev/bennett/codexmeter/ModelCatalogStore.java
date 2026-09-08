package dev.bennett.codexmeter;

import android.content.Context;
import java.util.List;
import org.json.JSONObject;

/** The dashboard and widget share a private cache, restricted to the signed-in account. */
final class ModelCatalogStore {
    private static final String PREFS = "models_meter_catalog_v1";

    private ModelCatalogStore() { }

    static ModelCatalogSnapshot load(Context context) {
        AuthTokens tokens = SecureTokenStore.load(context);
        if (tokens == null || tokens.accountId.isEmpty()) return null;
        try {
            String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("catalog", "");
            if (json.isEmpty()) return null;
            ModelCatalogSnapshot snapshot = ModelCatalogSnapshot.fromJson(new JSONObject(json));
            return tokens.accountId.equals(snapshot.accountId) ? snapshot : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void save(Context context, String account, List<CodexModelCatalog.Model> models,
            long now) throws Exception {
        AuthTokens current = SecureTokenStore.load(context);
        if (current == null || !account.equals(current.accountId)) return;
        ModelCatalogSnapshot next = ModelCatalogSnapshot.update(load(context), account, models, now);
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("catalog", next.toJson().toString()).commit()) {
            throw new Exception("Could not save the model catalog on this device.");
        }
        dev.bennett.codexmeter.wear.PhoneWearSync.pushProviders(context);
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        CodexModelsApi.clearThrottle();
    }
}
