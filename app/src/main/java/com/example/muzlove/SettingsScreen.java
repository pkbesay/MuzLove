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

/** Экран «Настройки»: все строки с текущим состоянием справа. Строит их MainActivity.setupSettingsContent()
 *  при открытии экрана, после любого изменения и в onResume (состояние уведомлений меняют в системе). */
final class SettingsScreen {

    private final MainActivity a;

    SettingsScreen(MainActivity a) { this.a = a; }

    void fill(LinearLayout list) {
        PlayerEngine engine = a.engine();
        StorageManager st = a.appState().storage();
        PlayerSheets sheets = a.playerSheets();
        boolean dark = !a.isLightTheme();

        list.removeAllViews();
        list.addView(a.settingRow("Тёмная тема", dark ? "Вкл" : "Выкл", a::toggleTheme));
        list.addView(a.settingRow("Звуковая волна", a.isWaveform() ? "Вкл" : "Выкл", a::toggleWaveform));
        list.addView(a.settingRow("Уведомление плеера", notificationsOn() ? "Включено" : "Выключено", this::onNotificationsTap));
        list.addView(a.settingRow("Эквалайзер", engine.equalizer().isEnabled() ? "Вкл" : "Выкл",
                () -> sheets.showEqualizer(!a.isLightTheme())));
        list.addView(a.settingRow("Таймер сна", sheets.sleepState(), () -> sheets.showSleepTimer(!a.isLightTheme())));
        list.addView(a.settingRow("Изменить профиль", st.getName(), a.profileSheets()::showEditProfile));
        list.addView(a.settingRow("Очистить историю поиска", String.valueOf(st.getSearchHistory().size()), this::clearSearchHistory));
        list.addView(a.settingRow("Сбросить приложение", "", this::confirmReset));
        list.addView(a.settingRow("О приложении", ProfileSheets.versionName(a), a.profileSheets()::showAbout));
    }

    // ---------- уведомление плеера ----------
    private boolean notificationsOn() {
        return NotificationManagerCompat.from(a).areNotificationsEnabled();
    }

    /** Android 13+: пока разрешение можно запросить — запрашиваем; иначе (и ниже) — системные настройки уведомлений. */
    private void onNotificationsTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            StorageManager st = a.appState().storage();
            // после двух отказов система запрос уже не показывает; до первого запроса «обоснования» тоже нет
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
        if (st.getSearchHistory().isEmpty()) { clear.run(); return; }
        a.dialogs().confirm(!a.isLightTheme(), "Очистить историю поиска?",
                "Недавние запросы будут удалены.", "Очистить", clear);
    }

    // ---------- сброс ----------
    private void confirmReset() {
        a.dialogs().confirm(!a.isLightTheme(), "Сбросить приложение?",
                "Будут удалены любимые треки, плейлисты, загруженная музыка и профиль. Это действие нельзя отменить.",
                "Сбросить", this::resetApp);
    }

    /** Всё как при первом запуске: музыка остановлена, файлы и настройки стёрты, режимы плеера по умолчанию,
     *  дальше — регистрация. */
    private void resetApp() {
        PlayerEngine engine = a.engine();
        engine.stop();                          // и уведомление: сервис закроется сам, когда очередь пуста
        Uploader.cancelPending();
        a.appState().wipe();                    // файлы треков и аватар, настройки, данные заново
        engine.resetToDefaults();               // перемешивание, повтор, таймер сна, эквалайзер — в памяти
        PlayerSheets.sleepChoice = 0;
        a.onAppReset();
        Intent i = new Intent(a, RegisterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }
}
