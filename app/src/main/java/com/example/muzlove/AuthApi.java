package com.example.muzlove;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Клиент API авторизации. Локальный HTTP-сервер доступен только debug-сборке. */
final class AuthApi {
    private static final String DEBUG_BASE_URL = "https://engineering-automotive-conversation-removal.trycloudflare.com";

    private AuthApi() {}

    static JSONObject register(Context context, String name, String username, String email, String password)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("username", username);
            body.put("email", email);
            body.put("password", password);
        } catch (JSONException e) {
            throw new IOException("Не удалось подготовить регистрацию", e);
        }
        return request(context, "POST", "/api/auth/register", body, null);
    }

    static JSONObject login(Context context, String identifier, String password) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("identifier", identifier);
            body.put("password", password);
        } catch (JSONException e) {
            throw new IOException("Не удалось подготовить вход", e);
        }
        return request(context, "POST", "/api/auth/login", body, null);
    }

    static JSONObject refresh(Context context, String refreshToken) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("refreshToken", refreshToken);
        } catch (JSONException e) {
            throw new IOException("Не удалось обновить сессию", e);
        }
        return request(context, "POST", "/api/auth/refresh", body, null);
    }

    static JSONObject me(Context context, String accessToken) throws IOException {
        return request(context, "GET", "/api/auth/me", null, accessToken);
    }

    static void logout(Context context, String accessToken) throws IOException {
        request(context, "POST", "/api/auth/logout", new JSONObject(), accessToken);
    }

    static JSONObject getSync(Context context, String accessToken) throws IOException {
        return request(context, "GET", "/api/sync", null, accessToken);
    }

    static JSONObject putSync(Context context, JSONObject payload, String accessToken) throws IOException {
        return request(context, "PUT", "/api/sync", payload, accessToken);
    }

    private static JSONObject request(Context context, String method, String path, JSONObject body, String accessToken)
            throws IOException {
        boolean debug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!debug) {
            throw new IOException("Для release-сборки сначала настройте HTTPS. Локальный HTTP разрешён только в debug.");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(DEBUG_BASE_URL + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (accessToken != null && !accessToken.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = stream == null ? "" : readFully(stream);

            JSONObject response;
            try {
                response = responseText.trim().isEmpty() ? new JSONObject() : new JSONObject(responseText);
            } catch (JSONException e) {
                response = new JSONObject();
            }

            if (status < 200 || status >= 300) {
                String message = response.optString("message", "");
                if (message.isEmpty()) message = response.optString("error", "");
                if (message.isEmpty()) message = "Ошибка сервера (HTTP " + status + ")";
                throw new ApiException(status, message);
            }
            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readFully(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    static String userValue(JSONObject response, String key) {
        if (response == null) return null;
        JSONObject user = response.optJSONObject("user");
        if (user == null) user = response;
        String value = user.optString(key, null);
        return value == null || value.trim().isEmpty() || "null".equals(value) ? null : value.trim();
    }

    static final class ApiException extends IOException {
        final int statusCode;
        ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
