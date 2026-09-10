package dev.scorpion7slayer.modelsmeter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local discovery order, not model release dates (the API supplies no release timestamp). */
public final class ModelCatalogSnapshot {
    public final String accountId;
    public final long baselineAt;
    public final long checkedAt;
    public final List<CodexModelCatalog.Model> models;
    private final Map<String, Long> firstSeen;

    private ModelCatalogSnapshot(String accountId, long baselineAt, long checkedAt,
            List<CodexModelCatalog.Model> models, Map<String, Long> firstSeen) {
        this.accountId = accountId;
        this.baselineAt = baselineAt;
        this.checkedAt = checkedAt;
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
        this.firstSeen = new LinkedHashMap<>(firstSeen);
    }

    public static ModelCatalogSnapshot update(ModelCatalogSnapshot previous, String accountId,
            List<CodexModelCatalog.Model> available, long now) {
        if (previous != null && !previous.accountId.equals(accountId)) previous = null;
        Map<String, Long> seen = previous == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(previous.firstSeen);
        long baseline = previous == null ? 0 : previous.baselineAt;
        if (baseline == 0 && !available.isEmpty()) baseline = now;
        LinkedHashMap<String, CodexModelCatalog.Model> unique = new LinkedHashMap<>();
        for (CodexModelCatalog.Model model : available) {
            if (model.id.isEmpty()) continue;
            seen.putIfAbsent(model.id, now);
            unique.putIfAbsent(model.id, model);
        }
        List<CodexModelCatalog.Model> ordered = new ArrayList<>(unique.values());
        // Stable sort preserves API order within the first baseline or a single discovery batch.
        ordered.sort((left, right) -> Long.compare(seen.get(right.id), seen.get(left.id)));
        return new ModelCatalogSnapshot(accountId, baseline, now, ordered, seen);
    }

    public long discoveredAt(CodexModelCatalog.Model model) {
        long timestamp = firstSeen.getOrDefault(model.id, baselineAt);
        return timestamp > baselineAt ? timestamp : 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject seen = new JSONObject();
        for (Map.Entry<String, Long> entry : firstSeen.entrySet()) {
            seen.put(entry.getKey(), entry.getValue());
        }
        JSONArray entries = new JSONArray();
        for (CodexModelCatalog.Model model : models) {
            entries.put(new JSONObject().put("id", model.id).put("name", model.name));
        }
        return new JSONObject().put("account", accountId).put("baseline_at", baselineAt)
                .put("checked_at", checkedAt).put("first_seen", seen).put("models", entries);
    }

    public static ModelCatalogSnapshot fromJson(JSONObject json) throws JSONException {
        Map<String, Long> seen = new LinkedHashMap<>();
        JSONObject dates = json.getJSONObject("first_seen");
        java.util.Iterator<String> keys = dates.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            seen.put(key, dates.getLong(key));
        }
        List<CodexModelCatalog.Model> models = new ArrayList<>();
        JSONArray entries = json.getJSONArray("models");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            models.add(new CodexModelCatalog.Model(entry.getString("id"), entry.getString("name")));
        }
        return new ModelCatalogSnapshot(json.getString("account"), json.getLong("baseline_at"),
                json.getLong("checked_at"), models, seen);
    }
}
