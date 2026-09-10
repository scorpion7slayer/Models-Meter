package dev.scorpion7slayer.modelsmeter;

import java.util.Locale;

/** Relative dates use the app language, including when the system uses an unsupported language. */
public final class LocalizedTime {
    private LocalizedTime() { }
    public static String relative(long time) { return relative(time, System.currentTimeMillis(), 60000); }
    public static String relative(long time, long now, long resolution) {
        boolean french = Locale.getDefault().getLanguage().equals("fr");
        long distance = Math.abs(time - now);
        if (distance < 60000) return french ? "à l’instant" : "just now";
        long count; String unit;
        if (distance < 3600000) { count = distance / 60000; unit = french ? "min" : "minute"; }
        else if (distance < 86400000) { count = distance / 3600000; unit = french ? "h" : "hour"; }
        else { count = distance / 86400000; unit = french ? "j" : "day"; }
        String duration = count + " " + unit + (!french && count > 1 ? "s" : "");
        return time > now ? (french ? "dans " : "in ") + duration : french ? "il y a " + duration : duration + " ago";
    }
}
