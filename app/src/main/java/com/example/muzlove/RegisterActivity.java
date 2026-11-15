package com.example.muzlove;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Random;

public class RegisterActivity extends AppCompatActivity {

    private StorageManager storage;
    private EditText etName, etUser;
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        // под системные панели и клавиатуру: отступы вручную, содержимое прокручивается
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_register);

        View scroll = findViewById(R.id.regScroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        // фон экрана тёмный: значки панелей светлые
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), scroll);
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);

        storage = new StorageManager(this);
        etName = findViewById(R.id.etRegName);
        etUser = findViewById(R.id.uzerName);
        btnStart = findViewById(R.id.btnRegister);

        btnStart.setOnClickListener(v -> submit());
        // «Готово» на клавиатуре отправляет форму; «Далее» на имени переходит к @username само
        etUser.setOnEditorActionListener((tv, actionId, e) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
    }

    private void submit() {
        String name = Username.cleanName(etName.getText());
        if (name.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, введите имя", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }
        String user;
        if (Username.normalize(etUser.getText()).isEmpty()) {
            user = Username.fromName(name, new Random());          // не указали — из имени
        } else {
            user = Username.parse(etUser.getText());
            if (user == null) {
                Toast.makeText(this, Username.ERROR, Toast.LENGTH_LONG).show();
                etUser.requestFocus();
                return;
            }
        }
        btnStart.setEnabled(false);                                 // двойное нажатие не откроет экран дважды
        storage.setName(name);
        storage.setUsername(user);
        storage.setRegistered(true);

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
