package dev.bennett.codexmeter;

import java.util.Locale;

/** The first system language is honored only when it is French or English. */
public final class LanguageChoice {
    private LanguageChoice() { }
    public static String resolve(String choice, String system) {
        String language = "system".equals(choice) ? system : choice;
        return language != null && language.toLowerCase(Locale.ROOT).matches("fr([_-].*)?") ? "fr" : "en";
    }
}
