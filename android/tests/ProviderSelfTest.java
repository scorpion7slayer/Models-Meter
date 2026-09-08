package dev.bennett.codexmeter;

import java.util.Locale;
import org.json.JSONObject;

/** Contract fixtures: absent quotas, fractional percentage units, and billing-cycle mapping. */
public final class ProviderSelfTest {
    public static void main(String[] args) throws Exception {
        long now = 1800000000000L;
        UsageSnapshot claude = ProviderUsageParser.parse(Provider.ANTHROPIC, new JSONObject("""
                {"five_hour":{"utilization":42,"resets_at":"2027-01-15T08:00:00Z"},"seven_day":null}
                """), now);
        assert claude.fiveHour.usedPercent == 42;
        assert claude.weekly == null && claude.monthly == null && claude.resetCreditsAvailable == -1;
        UsageSnapshot inactive = ProviderUsageParser.parse(Provider.ANTHROPIC,
                new JSONObject("{\"five_hour\":null,\"seven_day\":null}"), now);
        assert inactive.fiveHour == null && inactive.weekly == null;
        UsageSnapshot cursor = ProviderUsageParser.parse(Provider.CURSOR, new JSONObject("""
                {"billingCycleStart":"2026-09-01T00:00:00Z","billingCycleEnd":"2026-10-01T00:00:00Z",
                 "individualUsage":{"plan":{"used":2500,"limit":5000,"totalPercentUsed":0.6}}}
                """), now);
        assert cursor.fiveHour == null && cursor.weekly == null && cursor.monthly.usedPercent == 1;
        assert cursor.monthly.windowSeconds == 2592000;
        UsageSnapshot go = ProviderUsageParser.parse(Provider.OPENCODE_GO, new JSONObject("""
                {"rollingUsage":{"usagePercent":25,"resetInSec":7200},
                 "weeklyUsage":{"usagePercent":0.6,"resetInSec":3600},"monthlyUsage":null}
                """), now);
        assert go.fiveHour.usedPercent == 25 && go.weekly.usedPercent == 1 && go.monthly == null;
        assert go.fiveHour.resetAtEpochSeconds == now / 1000 + 7200;
        UsageSnapshot nextGo = ProviderUsageParser.parse(Provider.OPENCODE_GO, new JSONObject("""
                {"usage":{"rolling":{"percentUsed":42,"resetInSec":60}}}
                """), now);
        assert nextGo.fiveHour.usedPercent == 42 && nextGo.weekly == null;
        for (Provider provider : new Provider[]{Provider.ANTHROPIC, Provider.CURSOR, Provider.OPENCODE_GO}) {
            try { ProviderUsageParser.parse(provider, new JSONObject("{\"error\":\"unauthorized\"}"), now); throw new AssertionError(provider); }
            catch (IllegalArgumentException expected) { }
        }
        for (String invalid : new String[]{"{\"five_hour\":true}", "{\"five_hour\":\"bad\"}"}) {
            try { ProviderUsageParser.parse(Provider.ANTHROPIC, new JSONObject(invalid), now); throw new AssertionError(invalid); }
            catch (IllegalArgumentException expected) { }
        }
        assert LanguageChoice.resolve("system", "fr-BE").equals("fr");
        assert LanguageChoice.resolve("system", "en-US").equals("en");
        assert LanguageChoice.resolve("system", "de-DE").equals("en");
        assert LanguageChoice.resolve("fr", "de-DE").equals("fr");
        assert LanguageChoice.resolve("en", "fr-FR").equals("en");
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            assert Translations.t("Settings").equals("Réglages");
            assert LocalizedTime.relative(now - 720000, now, 60000).equals("il y a 12 min");
            assert LocalizedTime.relative(now + 720000, now, 60000).equals("dans 12 min");
            Locale.setDefault(Locale.ENGLISH);
            assert Translations.t("Settings").equals("Settings");
            assert LocalizedTime.relative(now - 720000, now, 60000).equals("12 minutes ago");
        } finally { Locale.setDefault(before); }
        System.out.println("Provider contract and localization checks passed.");
    }
}
