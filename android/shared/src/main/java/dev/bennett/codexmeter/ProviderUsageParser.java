package dev.bennett.codexmeter;

import java.time.Instant;
import java.util.Collections;
import org.json.JSONObject;

/** Subscription responses, never API billing estimates. Missing windows stay absent. */
public final class ProviderUsageParser {
    private ProviderUsageParser() { }

    public static UsageSnapshot parse(Provider provider, JSONObject json, long now) {
        UsageWindow five = null, week = null, month = null;
        if (provider == Provider.ANTHROPIC) {
            if (!json.has("five_hour") && !json.has("seven_day")) throw invalid();
            five = percentWindow(object(json, "five_hour"), "utilization", 18000, now);
            week = percentWindow(object(json, "seven_day"), "utilization", 604800, now);
        } else if (provider == Provider.CURSOR) {
            JSONObject individual = json.optJSONObject("individualUsage");
            JSONObject plan = individual == null ? null : individual.optJSONObject("plan");
            if (plan == null) throw invalid();
            double percent = plan.optDouble("totalPercentUsed", Double.NaN);
            if (!Double.isFinite(percent) && plan.optDouble("limit", 0) > 0 && plan.has("used"))
                percent = plan.optDouble("used") / plan.optDouble("limit") * 100;
            if (Double.isFinite(percent) && percent >= 0) {
                long end = date(json.optString("billingCycleEnd"));
                long start = date(json.optString("billingCycleStart"));
                month = new UsageWindow((int) Math.round(Math.min(100, percent)), end > start && start > 0
                        ? end - start : 2592000, 0, end);
            }
        } else if (provider == Provider.OPENCODE_GO) {
            JSONObject usage = json.optJSONObject("usage");
            if (usage != null) {
                if (!usage.has("rolling") && !usage.has("weekly") && !usage.has("monthly")) throw invalid();
                five = goWindow(object(usage, "rolling"), 18000, now);
                week = goWindow(object(usage, "weekly"), 604800, now);
                month = goWindow(object(usage, "monthly"), 2592000, now);
            } else {
                if (!json.has("rollingUsage")) throw invalid();
                five = goWindow(object(json, "rollingUsage"), 18000, now);
                week = goWindow(object(json, "weeklyUsage"), 604800, now);
                month = goWindow(object(json, "monthlyUsage"), 2592000, now);
            }
        } else throw invalid();
        boolean limited = (five != null && five.usedPercent >= 100)
                || (week != null && week.usedPercent >= 100) || (month != null && month.usedPercent >= 100);
        return new UsageSnapshot(provider.label, !limited, limited, five, week, month,
                Collections.emptyList(), null, -1, now);
    }

    private static UsageWindow goWindow(JSONObject json, long seconds, long now) {
        if (json == null) return null;
        double percent = json.optDouble("percentUsed", Double.NaN);
        if (!Double.isFinite(percent)) percent = json.optDouble("usagePercent", Double.NaN);
        if (!Double.isFinite(percent)) percent = json.optDouble("percent", Double.NaN);
        // All current Go response variants report percentage units, including values below 1.
        if (!Double.isFinite(percent) || percent < 0) throw invalid();
        long after = json.optLong("resetInSec", 0);
        long reset = date(json.optString("resetsAt"));
        return new UsageWindow((int) Math.round(Math.min(100, percent)), seconds, after,
                reset > 0 ? reset : after > 0 ? now / 1000 + after : 0);
    }

    private static UsageWindow percentWindow(JSONObject json, String key, long seconds, long now) {
        if (json == null) return null;
        double value = json.optDouble(key, Double.NaN);
        if (!Double.isFinite(value) || value < 0) throw invalid();
        return new UsageWindow((int) Math.round(Math.min(100, value)), seconds, 0, date(json.optString("resets_at")));
    }

    private static JSONObject object(JSONObject parent, String key) {
        Object value = parent.opt(key);
        if (value == null || value == JSONObject.NULL) return null;
        if (!(value instanceof JSONObject)) throw invalid();
        return (JSONObject) value;
    }

    private static long date(String value) {
        try { return Instant.parse(value).getEpochSecond(); }
        catch (Exception ignored) { return 0; }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("The provider returned an unsupported usage response.");
    }
}
