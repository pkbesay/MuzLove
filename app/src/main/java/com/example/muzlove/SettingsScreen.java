package com.example.muzlove;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

/** Экран «Настройки»: строки с текущим состоянием. */
final class SettingsScreen {

    private final MainActivity a;

    SettingsScreen(MainActivity a) { this.a = a; }

    void fill(LinearLayout list) {
        PlayerEngine engine = a.engine();
        StorageManager st = a.appState().storage();
        PlayerSheets sheets = a.playerSheets();
        boolean dark = !a.isLightTheme();

        list.removeAllViews();
        if (new AuthSessionStore(a).hasRefreshToken()) {
            list.addView(a.settingRow("Выйти из аккаунта", "Подключён", this::confirmLogout));
        } else {
            list.addView(a.settingRow("Войти / создать аккаунт", "Не подключён", this::openAccount));
        }
        int cachedTracks = OfflineTrackCache.countCached(a);
        list.addView(a.settingRow("Офлайн-музыка",
                cachedTracks + " " + Texts.plural(cachedTracks, "трек сохранён", "трека сохранено", "треков сохранено"),
                () -> a.dialogs().message(dark, "Офлайн-музыка",
                        "Сохранено для прослушивания без интернета: " + cachedTracks + " треков. " +
                        "Онлайн-треки автоматически кэшируются после успешного воспроизведения. " +
                        "Кэш ограничен примерно 250 МБ и очищается при полном сбросе приложения.")));
        list.addView(a.settingRow("Тёмная тема", dark ? "Вкл" : "Выкл", a::toggleTheme));
        list.addView(a.settingRow("Звуковая волна", a.isWaveform() ? "Вкл" : "Выкл", a::toggleWaveform));
        list.addView(a.settingRow("Уведомление плеера", notificationsOn() ? "Вкл" : "Выкл", this::onNotificationsTap));
        list.addView(a.settingRow("Эквалайзер", engine.equalizer().isEnabled() ? "Вкл" : "Выкл",
                () -> sheets.showEqualizer(!a.isLightTheme())));
        list.addView(a.settingRow("Таймер сна", sheets.sleepState(), () -> sheets.showSleepTimer(!a.isLightTheme())));
        list.addView(a.settingRow("Изменить профиль", st.getName(), a.profileSheets()::showEditProfile));
        int queries = st.getSearchHistory().size();
        list.addView(a.settingRow("Очистить историю поиска",
                queries > 0 ? queries + " " + Texts.plural(queries, "запрос", "запроса", "запросов") : "Пусто",
                this::clearSearchHistory));
        list.addView(a.settingRow("Сбросить приложение", "", this::confirmReset));
        list.addView(a.settingRow("О приложении", ProfileSheets.versionName(a), a.profileSheets()::showAbout));
    }

    private void openAccount() {
        Intent intent = new Intent(a, RegisterActivity.class)
                .putExtra(RegisterActivity.EXTRA_FROM_SETTINGS, true)
                .putExtra(RegisterActivity.EXTRA_START_LOGIN, true);
        a.startActivity(intent);
    }

    private void confirmLogout() {
        a.dialogs().confirm(!a.isLightTheme(), "Выйти из аккаунта?",
                "Аккаунт будет отключён на этом устройстве. Любимые треки, плейлисты, загруженная музыка и настройки останутся.",
                "Выйти", this::logoutAccount);
    }

    private void logoutAccount() {
        AuthSessionStore sessions = new AuthSessionStore(a);
        new Thread(() -> {
            boolean revoked = false;
            try {
                String access = sessions.getAccessToken();
                if (access == null) {
                    String refresh = sessions.getRefreshToken();
                    if (refresh != null) {
                        JSONObject updated = AuthApi.refresh(a, refresh);
                        sessions.saveSession(updated);
                        access = sessions.getAccessToken();
                    }
                }
                if (access != null) {
                    try {
                        AuthApi.logout(a, access);
                        revoked = true;
                    } catch (AuthApi.ApiException e) {
                        if (e.statusCode != 401) throw e;
                        String refresh = sessions.getRefreshToken();
                        if (refresh != null) {
                            JSONObject updated = AuthApi.refresh(a, refresh);
                            sessions.saveSession(updated);
                            AuthApi.logout(a, sessions.getAccessToken());
                            revoked = true;
                        }
                    }
                }
            } catch (Exception ignored) {
                // При отсутствии сети всё равно очищаем токены на этом устройстве.
            }
            sessions.clear();
            final boolean serverRevoked = revoked;
            a.runOnUiThread(() -> {
                a.toast(serverRevoked
                        ? "Вы вышли из аккаунта. Музыка и плейлисты сохранены."
                        : "Локальный выход выполнен. Серверная сессия завершится по сроку действия.");
                a.refreshSettings();
            });
        }, "MuzLove-logout").start();
    }

    // ---------- уведомление плеера ----------
    private boolean notificationsOn() {
        return NotificationManagerCompat.from(a).areNotificationsEnabled();
    }

    private void onNotificationsTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            StorageManager st = a.appState().storage();
            boolean mayAsk = !st.wasNotificationsAsked()
                    || ActivityCompat.shouldShowRequestPermissionRationale(a, Manifest.permission.POST_NOTIFICATIONS);
            if (mayAsk) {
                st.setNotificationsAsked();
                a.requestNotificationPermission();
                return;
            }
        }
        openNotificationSettings();
    }

    private void openNotificationSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName());
        } else {
            i = detailsIntent();
        }
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                a.startActivity(detailsIntent());
            } catch (ActivityNotFoundException | SecurityException e2) {
                Toast.makeText(a, "Нет подходящего приложения", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Intent detailsIntent() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.getPackageName(), null));
    }

    // ---------- история поиска ----------
    private void clearSearchHistory() {
        StorageManager st = a.appState().storage();
        Runnable clear = () -> {
            st.clearSearchHistory();
            a.toast("История поиска очищена");
            a.refreshSettings();
        };
        if (st.getSearchHistory().isEmpty()) { a.toast("История поиска пуста"); return; }
        a.dialogs().confirm(!a.isLightTheme(), "Очистить историю поиска?",
                "Недавние запросы будут удалены.", "Очистить", clear);
    }

    // ---------- сброс ----------
    private void confirmReset() {
        a.dialogs().confirm(!a.isLightTheme(), "Сбросить приложение?",
                "Будут удалены любимые треки, плейлисты, загруженная музыка и профиль. Это действие нельзя отменить.",
                "Сбросить", this::resetApp);
    }

    private void resetApp() {
        PlayerEngine engine = a.engine();
        engine.stop();
        Uploader.cancelPending();
        new AuthSessionStore(a).clear();
        a.appState().wipe();
        engine.resetToDefaults();
        PlayerSheets.sleepChoice = 0;
        a.onAppReset();
        Intent i = new Intent(a, RegisterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }
}
