package dev.scorpion7slayer.modelsmeter;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** One independently encrypted credential per provider; never included in exports or Wear sync. */
final class ProviderCredentials {
    private static final String PREFS = "models_meter_provider_credentials";
    private ProviderCredentials() { }
    static synchronized void save(Context context, Provider provider, JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(provider));
        JSONObject envelope = new JSONObject()
                .put("iv", Base64.getEncoder().encodeToString(cipher.getIV()))
                .put("data", Base64.getEncoder().encodeToString(cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8))));
        if (!context.getSharedPreferences(PREFS, 0).edit().putString(provider.id, envelope.toString()).commit())
            throw new Exception("Could not save credentials.");
    }
    static synchronized JSONObject load(Context context, Provider provider) {
        try {
            String raw = context.getSharedPreferences(PREFS, 0).getString(provider.id, "");
            if (raw.isEmpty()) return null;
            JSONObject envelope = new JSONObject(raw);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(provider), new GCMParameterSpec(128,
                    Base64.getDecoder().decode(envelope.getString("iv"))));
            return new JSONObject(new String(cipher.doFinal(Base64.getDecoder().decode(envelope.getString("data"))), StandardCharsets.UTF_8));
        } catch (Exception ignored) { return null; }
    }
    static synchronized void clear(Context context, Provider provider) {
        context.getSharedPreferences(PREFS, 0).edit().remove(provider.id).commit();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
            store.deleteEntry("models_meter_" + provider.id);
        } catch (Exception ignored) { }
    }
    private static SecretKey key(Provider provider) throws Exception {
        String alias = "models_meter_" + provider.id;
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(alias)) return (SecretKey) store.getKey(alias, null);
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
