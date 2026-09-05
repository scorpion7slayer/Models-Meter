package dev.bennett.codexmeter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

/** A compact, resizable view of the same account catalog used by the dashboard. */
public final class LatestModelsWidget extends AppWidgetProvider {
    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, LatestModelsWidget.class))) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_latest_models);
        ModelCatalogSnapshot snapshot = ModelCatalogStore.load(context);
        boolean signedIn = SecureTokenStore.isSignedIn(context);
        int[] rows = {R.id.latest_model_one, R.id.latest_model_two, R.id.latest_model_three};
        for (int i = 0; i < rows.length; i++) {
            boolean visible = snapshot != null && i < snapshot.models.size();
            views.setViewVisibility(rows[i], visible ? View.VISIBLE : View.GONE);
            if (visible) views.setTextViewText(rows[i], snapshot.models.get(i).name);
        }
        String summary;
        if (!signedIn) summary = context.getString(R.string.latest_models_sign_in);
        else if (snapshot == null) summary = context.getString(R.string.latest_models_waiting);
        else if (snapshot.models.isEmpty()) summary = context.getString(R.string.latest_models_empty);
        else summary = context.getString(R.string.latest_models_widget_hint);
        views.setTextViewText(R.id.latest_models_summary, summary);
        views.setTextViewText(R.id.latest_models_checked, snapshot == null ? "Models Meter"
                : context.getString(R.string.latest_models_checked,
                        android.text.format.DateUtils.getRelativeTimeSpanString(snapshot.checkedAt,
                                System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)));
        Intent open = new Intent(context, MainActivity.class)
                .setAction("dev.scorpion7slayer.modelsmeter.OPEN_MODELS")
                .putExtra(MainActivity.EXTRA_OPEN_MODELS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.latest_models_widget,
                PendingIntent.getActivity(context, 74516, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, buildViews(context));
        RefreshScheduler.schedulePeriodic(context);
        RefreshScheduler.scheduleImmediate(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        manager.updateAppWidget(id, buildViews(context));
    }
}
