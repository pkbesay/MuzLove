package com.example.muzlove;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_register);

        StorageManager storage = new StorageManager(this);
        EditText etName = findViewById(R.id.etRegName);
        Button btnStart = findViewById(R.id.btnRegister);

        btnStart.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Пожалуйста, введите имя", Toast.LENGTH_SHORT).show();
                return;
            }
            storage.setName(name);
            storage.setRegistered(true);
            
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
