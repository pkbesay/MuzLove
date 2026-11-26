package com.example.muzlove;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    static final String EXTRA_FROM_SETTINGS = "auth_from_settings";
    static final String EXTRA_START_LOGIN = "auth_start_login";

    private StorageManager storage;
    private EditText etName, etUser, etEmail, etPassword;
    private Button btnStart;
    private TextView title, subtitle, switchMode;
    private boolean loginMode;
    private boolean fromSettings;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_register);

        View scroll = findViewById(R.id.regScroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), scroll);
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);

        storage = new StorageManager(this);
        etName = findViewById(R.id.etRegName);
        etUser = findViewById(R.id.uzerName);
        etEmail = findViewById(R.id.etAuthEmail);
        etPassword = findViewById(R.id.etAuthPassword);
        btnStart = findViewById(R.id.btnRegister);
        title = findViewById(R.id.tvAuthTitle);
        subtitle = findViewById(R.id.tvAuthSubtitle);
        switchMode = findViewById(R.id.tvAuthSwitch);
        fromSettings = getIntent().getBooleanExtra(EXTRA_FROM_SETTINGS, false);

        btnStart.setOnClickListener(v -> submit());
        switchMode.setOnClickListener(v -> setMode(!loginMode));
        etPassword.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
        setMode(getIntent().getBooleanExtra(EXTRA_START_LOGIN, false));
    }

    private void setMode(boolean login) {
        loginMode = login;
        etName.setVisibility(login ? View.GONE : View.VISIBLE);
        etUser.setVisibility(login ? View.GONE : View.VISIBLE);
        title.setText(login ? "С возвращением" : "Добро пожаловать");
        subtitle.setText(login
                ? "Войдите в свой аккаунт MUZLOVE"
                : "Создайте аккаунт, чтобы пользоваться MUZLOVE на разных устройствах");
        etEmail.setHint(login ? "Email или @username" : "Электронная почта");
        etEmail.setInputType(login
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        btnStart.setText(login ? "Войти" : "Создать аккаунт");
        switchMode.setText(login ? "Нет аккаунта? Создать" : "Уже есть аккаунт? Войти");
    }

    private void submit() {
        String identifier = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (identifier.isEmpty()) {
            etEmail.setError(loginMode ? "Введите email или username" : "Введите email");
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Введите пароль");
            etPassword.requestFocus();
            return;
        }

        if (loginMode) {
            setBusy(true);
            io.execute(() -> {
                try {
                    JSONObject response = AuthApi.login(this, identifier, password);
                    completeLogin(response, null, null);
                } catch (Exception e) {
                    showFailure(e);
                }
            });
            return;
        }

        String name = Username.cleanName(etName.getText());
        if (name.isEmpty()) {
            etName.setError("Введите имя");
            etName.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
            etEmail.setError("Проверьте адрес электронной почты");
            etEmail.requestFocus();
            return;
        }
        if (password.length() < 8) {
            etPassword.setError("Пароль должен содержать не менее 8 символов");
            etPassword.requestFocus();
            return;
        }

        String username;
        if (Username.normalize(etUser.getText()).isEmpty()) {
            username = Username.fromName(name, new Random());
        } else {
            username = Username.parse(etUser.getText());
            if (username == null) {
                etUser.setError(Username.ERROR);
                etUser.requestFocus();
                return;
            }
        }

        setBusy(true);
        final String finalName = name;
        final String finalUsername = username;
        io.execute(() -> {
            try {
                AuthApi.register(this, finalName, finalUsername, identifier, password);
            } catch (Exception e) {
                showFailure(e);
                return;
            }
            try {
                JSONObject response = AuthApi.login(this, finalUsername, password);
                completeLogin(response, finalName, finalUsername);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false);
                    setMode(true);
                    etEmail.setText(finalUsername);
                    Toast.makeText(this,
                            "Аккаунт создан. Автоматический вход не удался — войдите с username и паролем.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void completeLogin(JSONObject response, String fallbackName, String fallbackUsername) {
        try {
            new AuthSessionStore(this).saveSession(response);
        } catch (Exception e) {
            showFailure(e);
            return;
        }

        String name = AuthApi.userValue(response, "name");
        String username = AuthApi.userValue(response, "username");
        if (name == null || name.isEmpty()) name = fallbackName;
        if (name == null || name.isEmpty()) name = storage.getName();
        if (username == null || !Username.isValid(username)) username = fallbackUsername;
        if (username == null || !Username.isValid(username)) username = storage.getUsername();

        storage.setName(name);
        storage.setUsername(username);
        storage.setRegistered(true);

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (fromSettings) {
                finish();
            } else {
                Intent intent = new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    private void showFailure(Exception error) {
        String message;
        if (error instanceof AuthApi.ApiException) {
            int code = ((AuthApi.ApiException) error).statusCode;
            if (code == 401) message = "Неверный email, username или пароль.";
            else if (code == 409) message = "Такой email или username уже занят.";
            else if (code == 400 || code == 422) message = "Проверьте введённые данные.";
            else message = "Ошибка сервера: " + error.getMessage();
        } else if (error instanceof IOException) {
            message = "Не удалось подключиться к серверу. Проверьте Wi-Fi и попробуйте ещё раз.";
        } else {
            message = error.getMessage() == null ? "Не удалось выполнить вход." : error.getMessage();
        }
        final String result = message;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false);
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        });
    }

    private void setBusy(boolean busy) {
        btnStart.setEnabled(!busy);
        btnStart.setText(busy ? "Подождите..." : (loginMode ? "Войти" : "Создать аккаунт"));
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
