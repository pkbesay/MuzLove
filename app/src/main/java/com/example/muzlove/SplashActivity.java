package com.example.muzlove;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_MS = 1700;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable next = this::continueStartup;
    private boolean routed;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_splash);

        View logo = findViewById(R.id.splashLogo);
        View title = findViewById(R.id.splashTitle);
        View tagline = findViewById(R.id.splashTagline);

        logo.setAlpha(0f);
        logo.setScaleX(0.86f);
        logo.setScaleY(0.86f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).start();
        title.setAlpha(0f);
        title.animate().alpha(1f).setStartDelay(280).setDuration(600).start();
        tagline.setAlpha(0f);
        tagline.animate().alpha(1f).setStartDelay(480).setDuration(600).start();

        handler.postDelayed(next, SPLASH_MS);
    }

    private void continueStartup() {
        if (isFinishing() || isDestroyed() || routed) return;

        StorageManager storage = new StorageManager(this);
        AuthSessionStore sessions = new AuthSessionStore(this);
        String refreshToken = sessions.getRefreshToken();

        // Существующие локальные установки продолжают работать без обязательной регистрации на сервере.
        if (refreshToken == null || refreshToken.isEmpty()) {
            openNext(storage.isRegistered() ? MainActivity.class : RegisterActivity.class, null);
            return;
        }
        if (!NetworkStatus.hasInternet(this)) {
            openNext(storage.isRegistered() ? MainActivity.class : RegisterActivity.class,
                    "Нет интернета. Открыт локальный режим; музыка и сохранённые данные доступны.");
            return;
        }

        io.execute(() -> {
            Class<?> target = storage.isRegistered() ? MainActivity.class : RegisterActivity.class;
            String notice = null;
            try {
                JSONObject refreshed = AuthApi.refresh(this, refreshToken);
                sessions.saveSession(refreshed); // сервер ротирует refresh token при каждом обновлении
                target = MainActivity.class;
            } catch (AuthApi.ApiException e) {
                if (e.statusCode == 401 || e.statusCode == 403) {
                    sessions.clear();
                    notice = "Сессия завершилась. При необходимости войдите снова через Настройки.";
                } else {
                    notice = "Сервер временно недоступен. Приложение откроется в локальном режиме.";
                }
            } catch (Exception e) {
                notice = "Нет связи с сервером. Приложение откроется в локальном режиме.";
            }
            final Class<?> destination = target;
            final String message = notice;
            handler.post(() -> openNext(destination, message));
        });
    }

    private void openNext(Class<?> target, String notice) {
        if (isFinishing() || isDestroyed() || routed) return;
        routed = true;
        startActivity(new Intent(this, target));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        if (notice != null) Toast.makeText(this, notice, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(next);
        io.shutdownNow();
        super.onDestroy();
    }
}
