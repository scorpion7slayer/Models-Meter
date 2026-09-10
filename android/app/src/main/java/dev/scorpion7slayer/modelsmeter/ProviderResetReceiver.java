package dev.scorpion7slayer.modelsmeter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Inexact, provider-scoped reset reminders. No network request or credentials in alarm intents. */
public final class ProviderResetReceiver extends BroadcastReceiver {
    static void scheduleAll(Context context) {
        for (Provider provider : Provider.values()) if (provider != Provider.CHATGPT)
            schedule(context, provider, ProviderRepository.usage(context, provider));
    }
    static void schedule(Context context, Provider provider, UsageSnapshot usage) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        UsageWindow[] windows = usage == null ? new UsageWindow[3]
                : new UsageWindow[]{usage.fiveHour, usage.weekly, usage.monthly};
        for (int index = 0; index < windows.length; index++) {
            PendingIntent pending = pending(context, provider, index);
            alarms.cancel(pending);
            UsageWindow window = windows[index];
            if (window == null || !ResetAlertPreferences.enabled(context)
                    || !ProviderRepository.connected(context, provider) || !selected(context, index)) continue;
            long reset = window.effectiveResetAtMillis(usage.fetchedAtMillis);
            if (reset > System.currentTimeMillis()) {
                // Android may defer reminders in Doze; refreshing remains the source of truth.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reset, pending);
            }
        }
    }
    private static boolean selected(Context context, int index) {
        String metric = ResetAlertPreferences.getMetric(context);
        return index == 0 ? !ResetAlertPreferences.METRIC_WEEKLY.equals(metric)
                : !ResetAlertPreferences.METRIC_FIVE_HOUR.equals(metric);
    }
    private static PendingIntent pending(Context context, Provider provider, int index) {
        Intent intent = new Intent(context, ProviderResetReceiver.class)
                .setAction("models-meter.reset." + provider.id + "." + index)
                .putExtra("provider", provider.id).putExtra("window", index);
        return PendingIntent.getBroadcast(context, 77000 + provider.ordinal() * 10 + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Provider provider = Provider.from(intent.getStringExtra("provider"));
        int index = intent.getIntExtra("window", -1);
        if (provider == Provider.CHATGPT || index < 0 || index > 2
                || !ResetAlertPreferences.enabled(context) || !selected(context, index)) return;
        UsageSnapshot usage = ProviderRepository.usage(context, provider);
        if (usage == null) return;
        UsageWindow window = new UsageWindow[]{usage.fiveHour, usage.weekly, usage.monthly}[index];
        if (window == null) return;
        long reset = window.effectiveResetAtMillis(usage.fetchedAtMillis);
        long now = System.currentTimeMillis();
        if (reset <= 0 || reset > now || now - reset > 6 * 60 * 60 * 1000L) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("models_meter_alerts_" + provider.id, 0);
        if (prefs.getLong("reset_" + index, 0) == reset) return;
        if (ProviderNotifications.post(context, provider, 76004 + index,
                L10n.text(context, "Scheduled allowance reset", "Réinitialisation prévue du quota"),
                L10n.text(context, "Your allowance should be available again. Open Models Meter to refresh.",
                        "Votre quota devrait être à nouveau disponible. Ouvrez Models Meter pour l’actualiser.")))
            prefs.edit().putLong("reset_" + index, reset).apply();
        RefreshScheduler.scheduleImmediate(context);
    }
}
