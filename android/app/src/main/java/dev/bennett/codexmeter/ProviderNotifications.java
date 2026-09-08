package dev.bennett.codexmeter;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

final class ProviderNotifications {
    private static SharedPreferences prefs(Context context, Provider provider) {
        return context.getSharedPreferences("models_meter_alerts_" + provider.id, 0);
    }
    static void clear(Context context, Provider provider) {
        prefs(context, provider).edit().clear().apply();
        ProviderResetReceiver.schedule(context, provider, null);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (int i = 0; i < 7; i++) manager.cancel(provider.id, 76000 + i);
    }
    static void models(Context context, Provider provider, ModelCatalogSnapshot previous, ModelCatalogSnapshot next) {
        if (previous == null || !ResetAlertPreferences.newModelsEnabled(context)) return;
        List<String> names = new ArrayList<>();
        for (CodexModelCatalog.Model model : next.models) {
            long seen = next.discoveredAt(model);
            if (seen > previous.checkedAt) names.add(model.name);
        }
        if (!names.isEmpty()) post(context, provider, 76000,
                L10n.text(context, "New models", "Nouveaux modèles"), String.join(", ", names));
    }
    static void usage(Context context, Provider provider, UsageSnapshot previous, UsageSnapshot next) {
        ProviderResetReceiver.schedule(context, provider, next);
        if (previous == null || !ResetAlertPreferences.enabled(context)) return;
        UsageWindow[] old = {previous.fiveHour, previous.weekly, previous.monthly};
        UsageWindow[] fresh = {next.fiveHour, next.weekly, next.monthly};
        String[] names = {L10n.text(context, "5-hour", "5 heures"), L10n.text(context, "Weekly", "Hebdomadaire"), L10n.text(context, "Monthly", "Mensuel")};
        for (int i = 0; i < fresh.length; i++) {
            UsageWindow window = fresh[i];
            if (window == null || old[i] == null) continue;
            String metric = ResetAlertPreferences.getMetric(context);
            if (i == 0 && ResetAlertPreferences.METRIC_WEEKLY.equals(metric)
                    || i > 0 && ResetAlertPreferences.METRIC_FIVE_HOUR.equals(metric)) continue;
            long reset = window.effectiveResetAtMillis(next.fetchedAtMillis);
            if (window.remainingPercent() <= ResetAlertPreferences.getThreshold(context)
                    && old[i].remainingPercent() > ResetAlertPreferences.getThreshold(context)
                    && prefs(context, provider).getLong("low_" + i, Long.MIN_VALUE) != reset) {
                if (post(context, provider, 76001 + i, names[i],
                        L10n.text(context, "Allowance running low", "Quota bientôt épuisé")))
                    prefs(context, provider).edit().putLong("low_" + i, reset).apply();
            } else if (ResetAlertPreferences.unexpectedRefillsEnabled(context)
                    && old[i].usedPercent - window.usedPercent >= 10) {
                post(context, provider, 76001 + i, names[i],
                        L10n.text(context, "Allowance replenished", "Quota rechargé"));
            }
        }
    }
    static boolean post(Context context, Provider provider, int id, String title, String body) {
        if (!ResetAlertPreferences.enabled(context)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return false;
        boolean silent = ResetAlertPreferences.STYLE_SILENT.equals(ResetAlertPreferences.getStyle(context));
        boolean alarm = ResetAlertPreferences.STYLE_ALARM.equals(ResetAlertPreferences.getStyle(context));
        String channel = silent ? "provider_updates_silent" : alarm ? "provider_updates_alarm" : "provider_updates";
        NotificationChannel settings = new NotificationChannel(channel,
                L10n.text(context, "Provider alerts", "Alertes des fournisseurs"),
                silent ? NotificationManager.IMPORTANCE_LOW : alarm ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT);
        if (silent) { settings.setSound(null, null); settings.enableVibration(false); }
        else if (alarm) {
            settings.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                    new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            settings.enableVibration(true);
        }
        manager.createNotificationChannel(settings);
        Intent open = new Intent(context, MainActivity.class).putExtra("provider", provider.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, provider.ordinal() * 10 + id,
                open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            manager.notify(provider.id, id, new android.app.Notification.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification).setContentTitle(dev.bennett.codexmeter.Translations.t(provider.label + " · " + title))
                    .setContentText(dev.bennett.codexmeter.Translations.t(body)).setStyle(new android.app.Notification.BigTextStyle().bigText(dev.bennett.codexmeter.Translations.t(body)))
                    .setContentIntent(pending).setAutoCancel(true).build());
            return true;
        } catch (SecurityException ignored) { return false; }
    }
}
