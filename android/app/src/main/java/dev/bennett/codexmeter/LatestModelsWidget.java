package dev.bennett.codexmeter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import java.util.LinkedHashMap;
import java.util.Map;

/** Responsive 2 × 1 and larger catalog with a provider choice for each widget. */
public final class LatestModelsWidget extends AppWidgetProvider {
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, LatestModelsWidget.class)))
            update(context, manager, id);
    }
    private static void update(Context context, AppWidgetManager manager, int id) {
        Context localized = L10n.localized(context);
        Bundle options = manager.getAppWidgetOptions(id);
        if (Build.VERSION.SDK_INT >= 31) {
            Map<SizeF, RemoteViews> sizes = new LinkedHashMap<>();
            for (SizeF size : new SizeF[]{new SizeF(110, 40), new SizeF(180, 110),
                    new SizeF(250, 180), new SizeF(300, 300), new SizeF(400, 500)})
                sizes.put(size, buildViews(localized, id, (int) size.getHeight()));
            manager.updateAppWidget(id, new RemoteViews(sizes));
        } else manager.updateAppWidget(id, buildViews(localized, id,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)));
    }
    static RemoteViews buildViews(Context context) { return buildViews(L10n.localized(context), -1, 180); }
    static RemoteViews buildViews(Context context, int id, int height) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_latest_models);
        Provider provider = ProviderRepository.widgetProvider(context, id);
        ModelCatalogSnapshot snapshot = ProviderRepository.catalog(context, provider);
        boolean connected = ProviderRepository.connected(context, provider);
        views.setTextViewText(R.id.latest_models_header,dev.bennett.codexmeter.Translations.t( provider.label + " · "
                + L10n.text(context, "Latest models", "Derniers modèles")));
        views.removeAllViews(R.id.latest_models_rows);
        int capacity = Math.max(1, Math.min(20, (height - (height >= 110 ? 66 : 26)) / 24));
        if (snapshot != null) for (int i = 0; i < Math.min(capacity, snapshot.models.size()); i++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_model_row);
            row.setTextViewText(R.id.model_row_name,dev.bennett.codexmeter.Translations.t( snapshot.models.get(i).name));
            row.setTextViewTextSize(R.id.model_row_name, android.util.TypedValue.COMPLEX_UNIT_SP, height < 110 ? 13 : 15);
            views.addView(R.id.latest_models_rows, row);
        }
        boolean hasModels = snapshot != null && !snapshot.models.isEmpty();
        views.setViewVisibility(R.id.latest_models_summary, hasModels ? View.GONE : View.VISIBLE);
        String summary = !connected ? L10n.text(context, "Connect in the app", "Connecter dans l’app")
                : provider == Provider.CURSOR && snapshot == null
                ? L10n.text(context, "Add a Cursor API key", "Ajouter une clé API Cursor")
                : L10n.text(context, "Waiting for models", "En attente des modèles");
        views.setTextViewText(R.id.latest_models_summary,dev.bennett.codexmeter.Translations.t( summary));
        views.setViewVisibility(R.id.latest_models_checked, height >= 110 ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.latest_models_checked,dev.bennett.codexmeter.Translations.t( snapshot == null ? "Models Meter"
                : L10n.text(context, "Checked ", "Vérifié ")
                    + dev.bennett.codexmeter.LocalizedTime.relative(snapshot.checkedAt,
                        System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)));
        Intent open = new Intent(context, MainActivity.class)
                .setAction("dev.scorpion7slayer.modelsmeter.OPEN_MODELS." + id)
                .putExtra("provider", provider.id).putExtra(MainActivity.EXTRA_OPEN_MODELS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.latest_models_widget, PendingIntent.getActivity(context, id,
                open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
        RefreshScheduler.schedulePeriodic(context); RefreshScheduler.scheduleImmediate(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        update(context, manager, id);
    }
    @Override public void onRestored(Context context, int[] oldIds, int[] newIds) {
        if (oldIds == null || newIds == null) return;
        for (int index = 0; index < Math.min(oldIds.length, newIds.length); index++) {
            if (oldIds[index] == newIds[index]) continue;
            ProviderRepository.setWidgetProvider(context, newIds[index], ProviderRepository.widgetProvider(context, oldIds[index]));
            ProviderRepository.deleteWidget(context, oldIds[index]);
        }
        updateAll(context);
    }
    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) ProviderRepository.deleteWidget(context, id);
    }
}
