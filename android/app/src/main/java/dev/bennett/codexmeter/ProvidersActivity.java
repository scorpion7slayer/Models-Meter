package dev.bennett.codexmeter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** Account setup happens on the phone; the watch and widgets receive only rendered data. */
public final class ProvidersActivity extends AppCompatActivity {
    @Override protected void attachBaseContext(Context context) { super.attachBaseContext(L10n.localized(context)); }
    @Override protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this); super.onCreate(state); build();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (request == 101 && result == RESULT_OK) build();
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }
    private void build() {
        boolean dark = Ui.isDark(this);
        LinearLayout content = Ui.installPage(this, L10n.text(this, "Providers", "Fournisseurs"), true).content;
        for (Provider provider : Provider.values()) {
            boolean connected = ProviderRepository.connected(this, provider);
            content.addView(Ui.actionRow(this, provider.label,
                    connected ? L10n.text(this, "Connected · tap to manage", "Connecté · toucher pour gérer")
                    : L10n.text(this, "Connect account", "Connecter le compte"),
                    R.drawable.ic_models_meter, view -> {
                        if (provider == Provider.CHATGPT) {
                            startActivity(new Intent(this, SettingsActivity.class)); return;
                        }
                        if (connected) new AlertDialog.Builder(this).setTitle(dev.bennett.codexmeter.Translations.t(provider.label))
                                .setItems(new String[]{L10n.text(this, "Reconnect", "Reconnecter"),
                                        L10n.text(this, "Disconnect", "Déconnecter")}, (dialog, which) -> {
                                    if (which == 0) connect(provider);
                                    else { ProviderRepository.disconnect(this, provider); build(); }
                                }).show();
                        else connect(provider);
                    }));
        }
        content.addView(Ui.text(this, L10n.text(this,
                "Credentials stay encrypted on this device. Connect each provider separately. Reset credits are a ChatGPT feature.",
                "Les identifiants restent chiffrés sur cet appareil. Connectez chaque fournisseur séparément. Les crédits de réinitialisation sont propres à ChatGPT."), 14, Ui.secondaryText(dark)));
    }
    private void connect(Provider provider) {
        boolean dark = Ui.isDark(this);
        LinearLayout content = Ui.installPage(this, provider.label, true).content;
        String help = switch (provider) {
            case ANTHROPIC -> L10n.text(this,
                    "Use your Claude sessionKey cookie or Claude Code OAuth access token to read your subscription quotas. An Anthropic API key does not grant access to Claude subscription quotas.",
                    "Utilisez le cookie sessionKey de Claude ou le jeton OAuth de Claude Code pour lire les quotas de votre abonnement. Une clé API Anthropic ne donne pas accès aux quotas de l’abonnement Claude.");
            case CURSOR -> L10n.text(this,
                    "Use the WorkosCursorSessionToken cookie from your Cursor account for subscription usage. Optionally add a Cursor user API key for the Cloud Agents model catalog.",
                    "Utilisez le cookie WorkosCursorSessionToken de votre compte Cursor pour les quotas. Ajoutez une clé API utilisateur Cursor pour le catalogue des modèles Cloud Agents (facultatif).");
            default -> L10n.text(this, "Enter your OpenCode Go API key from your OpenCode workspace.",
                    "Saisissez la clé API OpenCode Go de votre espace OpenCode.");
        };
        content.addView(Ui.text(this, help, 15, Ui.mainText(dark)));
        EditText credential = field(provider == Provider.OPENCODE_GO ? "API key" : "Session / OAuth token");
        content.addView(credential);
        EditText modelKey = field(L10n.text(this, "Cursor API key (optional)", "Clé API Cursor (facultatif)"));
        if (provider == Provider.CURSOR) content.addView(modelKey);
        if (provider != Provider.OPENCODE_GO) {
            Button browser = Ui.nativePrimaryButton(this, L10n.text(this, "Sign in to ", "Se connecter à ") + provider.label);
            browser.setOnClickListener(view -> startActivityForResult(new Intent(this, ProviderSignInActivity.class)
                    .putExtra("provider", provider.id).putExtra("model_key", modelKey.getText().toString()), 101));
            content.addView(browser, 0);
        }
        Button save = Ui.nativePrimaryButton(this, L10n.text(this, "Connect", "Connecter"));
        content.addView(save);
        save.setOnClickListener(view -> {
            String token = credential.getText().toString(); String key = modelKey.getText().toString();
            save.setEnabled(false); save.setText(dev.bennett.codexmeter.Translations.t(L10n.text(this, "Connecting…", "Connexion…")));
            new Thread(() -> {
                try {
                    ProviderRepository.connect(getApplicationContext(), provider, token, key);
                    AppPreferences.completeOnboarding(getApplicationContext());
                    RefreshScheduler.schedulePeriodic(getApplicationContext());
                    WidgetRenderer.updateAll(getApplicationContext());
                    runOnUiThread(() -> { if (!isDestroyed()) { credential.setText(dev.bennett.codexmeter.Translations.t("")); modelKey.setText(dev.bennett.codexmeter.Translations.t("")); build(); } });
                } catch (Exception error) {
                    runOnUiThread(() -> { if (!isDestroyed()) {
                        save.setEnabled(true); save.setText(dev.bennett.codexmeter.Translations.t(L10n.text(this, "Connect", "Connecter")));
                        new AlertDialog.Builder(this).setTitle(dev.bennett.codexmeter.Translations.t(provider.label)).setMessage(dev.bennett.codexmeter.Translations.t(error.getMessage()))
                                .setPositiveButton(android.R.string.ok, null).show();
                    } });
                }
            }, "provider-connect").start();
        });
    }
    private EditText field(String hint) {
        EditText field = new EditText(this); field.setHint(dev.bennett.codexmeter.Translations.t(hint)); field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setSaveEnabled(false);
        return field;
    }
}
