package com.example.muzlove;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Зашифрованное хранение токенов с ключом, который остаётся в Android Keystore. */
final class AuthSessionStore {
    private static final String PREFS = "muzlove_secure_auth";
    private static final String KEY_ALIAS = "com.example.muzlove.auth.aes";
    private static final String ACCESS = "access_token_enc";
    private static final String REFRESH = "refresh_token_enc";
    private static final String ACCOUNT_ID = "account_id";

    private final SharedPreferences prefs;

    AuthSessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void saveSession(JSONObject response) throws Exception {
        String access = response.optString("accessToken", null);
        String refresh = response.optString("refreshToken", null);
        if (access == null || access.trim().isEmpty() || refresh == null || refresh.trim().isEmpty()) {
            throw new IllegalArgumentException("Сервер не вернул токены авторизации");
        }
        String encryptedAccess = encrypt(access);
        String encryptedRefresh = encrypt(refresh);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(ACCESS, encryptedAccess)
                .putString(REFRESH, encryptedRefresh);
        JSONObject user = response.optJSONObject("user");
        String accountId = user != null ? user.optString("id", null) : null;
        if (accountId != null && !accountId.trim().isEmpty() && !"null".equals(accountId)) {
            editor.putString(ACCOUNT_ID, accountId.trim());
        }
        if (!editor.commit()) {
            throw new IllegalStateException("Не удалось сохранить сессию");
        }
    }

    synchronized String getAccessToken() { return read(ACCESS); }
    synchronized String getRefreshToken() { return read(REFRESH); }
    synchronized String getAccountId() {
        String id = prefs.getString(ACCOUNT_ID, null);
        return id == null || id.trim().isEmpty() ? null : id.trim();
    }

    synchronized boolean hasRefreshToken() {
        String token = read(REFRESH);
        return token != null && !token.isEmpty();
    }

    synchronized void clear() {
        prefs.edit().remove(ACCESS).remove(REFRESH).remove(ACCOUNT_ID).commit();
    }

    private String read(String key) {
        String encrypted = prefs.getString(key, null);
        if (encrypted == null) return null;
        try {
            return decrypt(encrypted);
        } catch (Exception e) {
            // Резервная копия могла перенести ciphertext без исходного ключа Android Keystore.
            clear();
            return null;
        }
    }

    private String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decrypt(String value) throws Exception {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Повреждены данные сессии");
        }
        byte[] iv = Base64.decode(value.substring(0, separator), Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(value.substring(separator + 1), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
