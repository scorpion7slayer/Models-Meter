package dev.bennett.codexmeter;

/** Apply language preferences in the process used by tiles and complications as well as screens. */
public final class WearApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        java.util.Locale.setDefault(L10n.locale(this));
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        java.util.Locale.setDefault(L10n.locale(this));
        WearSurfaceUpdater.requestAll(this);
    }
}
