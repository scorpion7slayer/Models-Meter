package dev.bennett.codexmeter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Models visible in the account's Codex picker; usage buckets are not a catalog. */
public final class CodexModelCatalog {
    public static final class Model {
        public final String id;
        public final String name;

        Model(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private CodexModelCatalog() { }

    public static List<Model> parse(String body) throws JSONException {
        JSONArray entries = new JSONObject(body).getJSONArray("models");
        List<Model> models = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String id = entry.getString("slug").trim();
            String visibility = entry.getString("visibility");
            if (!"list".equals(visibility) || id.isEmpty() || !seen.add(id)) continue;
            String name = entry.optString("display_name", "").trim();
            models.add(new Model(id, name.isEmpty() ? id : name));
        }
        return models;
    }

    public static List<Model> additions(Set<String> known, List<Model> models) {
        List<Model> added = new ArrayList<>();
        if (known.isEmpty()) return added; // First nonempty catalog establishes a silent baseline.
        for (Model model : models) if (!known.contains(model.id)) added.add(model);
        return added;
    }
}
