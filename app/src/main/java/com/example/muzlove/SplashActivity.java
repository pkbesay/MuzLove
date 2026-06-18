package com.example.muzlove;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_MS = 1700;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_splash);

        // мягкое появление логотипа и текста
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

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            StorageManager storage = new StorageManager(this);
            Class<?> target = storage.isRegistered() ? MainActivity.class : RegisterActivity.class;
            
            startActivity(new Intent(SplashActivity.this, target));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, SPLASH_MS);
    }
}
