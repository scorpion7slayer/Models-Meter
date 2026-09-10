package dev.scorpion7slayer.modelsmeter;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

/** Shared language policy for activities, background work, and widgets. */
public final class L10n {
    private L10n() { }
    public static String choice(Context context) {
        return context.getSharedPreferences("models_meter_language", 0).getString("choice", "system");
    }
    public static String resolve(String choice, String system) {
        return LanguageChoice.resolve(choice, system);
    }
    public static Locale locale(Context context) {
        return Locale.forLanguageTag(resolve(choice(context), Resources.getSystem().getConfiguration().getLocales().get(0).toLanguageTag()));
    }
    public static Context localized(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        Locale.setDefault(locale(context));
        config.setLocale(locale(context));
        return context.createConfigurationContext(config);
    }
    public static void select(Context context, String choice) {
        if (!choice.equals("fr") && !choice.equals("en")) choice = "system";
        context.getSharedPreferences("models_meter_language", 0).edit().putString("choice", choice).commit();
        Locale.setDefault(locale(context));
    }
    public static String text(Context context, String en, String fr) {
        return "fr".equals(locale(context).getLanguage()) ? fr : en;
    }
}
