package dev.scorpion7slayer.modelsmeter;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.os.Bundle;

/** Installs opt-in process and screen lifecycle diagnostics without touching normal app data. */
public final class ModelsMeterApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onCreate() {
        super.onCreate();
        java.util.Locale.setDefault(L10n.locale(this));
        DiagnosticLog.install(this);
        registerActivityLifecycleCallbacks(this);
        DiagnosticLog.info(this, "process", "application_started");
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        java.util.Locale.setDefault(L10n.locale(this));
        WidgetRenderer.updateAll(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        DiagnosticLog.info(this, "process", "memory_trimmed", "level", level,
                "ui_hidden", level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        DiagnosticLog.warn(this, "process", "low_memory");
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle state) {
        DiagnosticLog.info(this, "screen", "created",
                "activity", activity.getClass().getSimpleName(),
                "restored", state != null);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (!activity.getResources().getConfiguration().getLocales().get(0).getLanguage()
                .equals(L10n.locale(activity).getLanguage())) { activity.recreate(); return; }
        DiagnosticLog.info(this, "screen", "resumed",
                "activity", activity.getClass().getSimpleName());
    }

    @Override
    public void onActivityStopped(Activity activity) {
        DiagnosticLog.info(this, "screen", "stopped",
                "activity", activity.getClass().getSimpleName(),
                "changing_configuration", activity.isChangingConfigurations());
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle state) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
