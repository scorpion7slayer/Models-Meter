package dev.scorpion7slayer.modelsmeter;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only sanitized phone snapshots enter this store. Credentials remain on the phone. */
final class WearProviders {
    static Provider selected(Context context) {
        return Provider.from(context.getSharedPreferences("models_meter_providers", 0).getString("selected", "chatgpt"));
    }
    static void select(Context context, Provider provider) {
        context.getSharedPreferences("models_meter_providers", 0).edit().putString("selected", provider.id).apply();
        WearSurfaceUpdater.requestAll(context);
    }
    static JSONObject data(Context context, Provider provider) {
        try { return new JSONObject(context.getSharedPreferences("models_meter_providers", 0).getString("data", "{}"))
                .optJSONObject(provider.id); } catch (Exception ignored) { return null; }
    }
    static UsageSnapshot usage(Context context, Provider provider) {
        JSONObject data = data(context, provider);
        return data != null && data.optBoolean("connected") ? UsageSnapshot.fromJson(data.optJSONObject("usage")) : null;
    }
    static boolean apply(Context context, String payload) {
        try {
            JSONObject data = new JSONObject(payload);
            long previous = context.getSharedPreferences("models_meter_providers", 0).getLong("updated", 0);
            if (data.optLong("updated") <= previous) return false;
            context.getSharedPreferences("models_meter_providers", 0).edit().putString("data", payload)
                    .putLong("updated", data.optLong("updated")).apply();
            WearSurfaceUpdater.requestAll(context);
            return true;
        } catch (Exception ignored) { return false; }
    }
    static String modelNames(Context context) {
        JSONObject data = data(context, selected(context));
        JSONArray models = data == null ? null : data.optJSONArray("models");
        if (models == null || models.length() == 0) return L10n.text(context, "Waiting for models", "En attente des modèles");
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(12, models.length()); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) continue;
            if (names.length() > 0) names.append("\n\n");
            names.append(model.optString("name", model.optString("id")));
        }
        return names.toString();
    }
}
