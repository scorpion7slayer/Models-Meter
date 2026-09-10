package dev.scorpion7slayer.modelsmeter;

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
    static void update(Context context, AppWidgetManager manager, int id) {
        Context localized = L10n.localized(context);
        Bundle options = manager.getAppWidgetOptions(id);
        if (Build.VERSION.SDK_INT >= 31) {
            java.util.ArrayList<SizeF> available = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (available != null && !available.isEmpty() && available.size() <= 16) {
                Map<SizeF, RemoteViews> sizes = new LinkedHashMap<>();
                for (SizeF size : available)
                    sizes.put(size, buildViews(localized, id, (int) size.getHeight()));
                manager.updateAppWidget(id, new RemoteViews(sizes));
                return;
            }
        }
        manager.updateAppWidget(id, new RemoteViews(
                buildViews(localized, id, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)),
                buildViews(localized, id, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110))));
    }

    static RemoteViews buildViews(Context context) { return buildViews(L10n.localized(context), -1, 180); }
    static RemoteViews buildViews(Context context, int id, int height) {
        return buildViews(context, id, height, ProviderRepository.widgetProvider(context, id));
    }
    static RemoteViews buildViews(Context context, int id, int height, Provider provider) {
        return buildViews(context, id, height, provider, AppPreferences.loadWidgetOptions(context, id));
    }
    static RemoteViews buildViews(Context context, int id, int height, Provider provider, WidgetOptions options) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_latest_models);
        boolean dark = WidgetRenderer.chooseDark(context, options);
        WidgetRenderer.applySurface(context, views, options, dark);
        views.setTextColor(R.id.latest_models_header, WidgetGraphics.mainTextColor(dark));
        views.setTextColor(R.id.latest_models_summary, WidgetGraphics.mainTextColor(dark));
        views.setTextColor(R.id.latest_models_checked, WidgetGraphics.mainTextColor(dark));
        ModelCatalogSnapshot snapshot = ProviderRepository.catalog(context, provider);
        boolean connected = ProviderRepository.connected(context, provider);
        views.setTextViewText(R.id.latest_models_header,dev.scorpion7slayer.modelsmeter.Translations.t( provider.label + " · "
                + L10n.text(context, "Latest models", "Derniers modèles")));
        views.setContentDescription(android.R.id.background, provider.label + " · " + L10n.text(context, "Latest models", "Derniers modèles"));
        views.removeAllViews(R.id.latest_models_rows);
        float fontScale = context.getResources().getConfiguration().fontScale;
        boolean compact = height < 110;
        int padding = Ui.dp(context, compact ? 4 : 16);
        views.setViewPadding(android.R.id.background, Ui.dp(context, 16), padding, Ui.dp(context, 16), padding);
        views.setViewVisibility(R.id.latest_models_header, compact ? View.GONE : View.VISIBLE);
        int rowHeight = Math.max(24, Math.round(22 * fontScale + 12));
        int capacity = Math.max(1, Math.min(20, (height - Math.round(52 * fontScale + 32)) / rowHeight));
        if (snapshot != null) for (int i = 0; i < Math.min(capacity, snapshot.models.size()); i++) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_model_row);
            row.setTextViewText(R.id.model_row_name,dev.scorpion7slayer.modelsmeter.Translations.t( snapshot.models.get(i).name));
            row.setTextViewTextSize(R.id.model_row_name, android.util.TypedValue.COMPLEX_UNIT_SP, compact ? 13 : 14);
            row.setTextColor(R.id.model_row_name, WidgetGraphics.mainTextColor(dark));
            row.setInt(R.id.model_row_icon, "setColorFilter", WidgetGraphics.accentColor(context, options.accent, dark));
            views.addView(R.id.latest_models_rows, row);
        }
        boolean hasModels = snapshot != null && !snapshot.models.isEmpty();
        views.setViewVisibility(R.id.latest_models_summary, hasModels ? View.GONE : View.VISIBLE);
        String summary = !connected ? L10n.text(context, "Connect in the app", "Connecter dans l’app")
                : provider == Provider.CURSOR && snapshot == null
                ? L10n.text(context, "Add a Cursor API key", "Ajouter une clé API Cursor")
                : L10n.text(context, "Waiting for models", "En attente des modèles");
        views.setTextViewText(R.id.latest_models_summary,dev.scorpion7slayer.modelsmeter.Translations.t( summary));
        views.setViewVisibility(R.id.latest_models_checked, height >= 110 ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.latest_models_checked,dev.scorpion7slayer.modelsmeter.Translations.t( snapshot == null ? "Models Meter"
                : L10n.text(context, "Checked ", "Vérifié ")
                    + dev.scorpion7slayer.modelsmeter.LocalizedTime.relative(snapshot.checkedAt,
                        System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)));
        Intent open = new Intent(context, MainActivity.class)
                .setAction("dev.scorpion7slayer.modelsmeter.OPEN_MODELS." + id)
                .putExtra("provider", provider.id).putExtra(MainActivity.EXTRA_OPEN_MODELS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(android.R.id.background, PendingIntent.getActivity(context, id,
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
            AppPreferences.saveWidgetOptions(context, newIds[index], AppPreferences.loadWidgetOptions(context, oldIds[index]));
            AppPreferences.deleteWidgetOptions(context, oldIds[index]);
            ProviderRepository.deleteWidget(context, oldIds[index]);
        }
        updateAll(context);
    }
    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) {
            ProviderRepository.deleteWidget(context, id);
            AppPreferences.deleteWidgetOptions(context, id);
        }
    }
}
