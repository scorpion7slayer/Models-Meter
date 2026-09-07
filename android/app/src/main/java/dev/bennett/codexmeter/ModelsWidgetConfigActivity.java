package dev.bennett.codexmeter;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

public final class ModelsWidgetConfigActivity extends AppCompatActivity {
    @Override protected void attachBaseContext(Context context) { super.attachBaseContext(L10n.localized(context)); }
    @Override protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this); super.onCreate(state); setResult(RESULT_CANCELED);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        LinearLayout content = Ui.installPage(this, L10n.text(this, "Models widget", "Widget des modèles"), true).content;
        content.addView(Ui.text(this, L10n.text(this, "Provider", "Fournisseur"), 18, Ui.mainText(Ui.isDark(this))));
        Spinner provider = Ui.spinner(this, Provider.labels(), Ui.isDark(this));
        provider.setSelection(ProviderRepository.widgetProvider(this, id).ordinal());
        content.addView(provider);
        content.addView(Ui.text(this, L10n.text(this,
                "Resize the widget on your home screen. It shows more model names as it grows, starting at 2 × 1 cells.",
                "Redimensionnez le widget sur l’écran d’accueil. Il affiche davantage de modèles quand vous l’agrandissez, à partir de 2 × 1 cases."), 14, Ui.secondaryText(Ui.isDark(this))));
        Button save = Ui.nativePrimaryButton(this, L10n.text(this, "Save", "Enregistrer"));
        content.addView(save);
        save.setOnClickListener(view -> {
            ProviderRepository.setWidgetProvider(this, id, Provider.values()[provider.getSelectedItemPosition()]);
            LatestModelsWidget.updateAll(this);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        });
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
