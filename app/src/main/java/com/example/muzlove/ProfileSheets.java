package com.example.muzlove;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.Button;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Шторки и диалоги профиля: правка имени и @username, фото, спонсорство, «О приложении».
 *  Цвета — по теме приложения. Создаётся в MainActivity.onCreate, dismissAll() — в onDestroy. Главный поток. */
final class ProfileSheets {

    private static final String NO_APP = "Нет подходящего приложения";

    private final MainActivity a;
    private final AppDialogs dialogs;
    private final List<Sheet> openSheets = new ArrayList<>();

    ProfileSheets(MainActivity a) {
        this.a = a;
        this.dialogs = a.dialogs();
    }

    void dismissAll() {
        for (Sheet s : new ArrayList<>(openSheets)) s.dismiss();
        openSheets.clear();
    }

    private boolean gone() { return a.isFinishing() || a.isDestroyed(); }

    private Sheet sheet(CharSequence title) {
        Sheet s = Sheet.create(a, !a.isLightTheme(), title);
        openSheets.add(s);
        s.onDismiss(() -> openSheets.remove(s));
        return s;
    }

    // ======================= имя и @username =======================
    /** Диалог из двух полей (профиль и «Изменить профиль» в настройках). Пустой @username — из имени. */
    void showEditProfile() {
        if (gone()) return;
        StorageManager st = a.appState().storage();
        AlertDialog.Builder b = dialogs.builder(dialogs.dark());
        Context c = b.getContext();
        float d = c.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(20 * d), Math.round(8 * d), Math.round(20 * d), 0);

        AppCompatEditText name = new AppCompatEditText(c);
        name.setHint("Имя");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        name.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Username.NAME_MAX)});
        name.setText(st.getName());
        name.setSelection(name.length());
        box.addView(name, Sheet.matchWrap());

        AppCompatEditText user = new AppCompatEditText(c);
        user.setHint("@username");
        user.setSingleLine(true);
        user.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        user.setImeOptions(EditorInfo.IME_ACTION_DONE);
        user.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        user.setText(st.getUsername());
        LinearLayout.LayoutParams ulp = Sheet.matchWrap();
        ulp.topMargin = Math.round(8 * d);
        box.addView(user, ulp);

        TextView note = new TextView(c);
        note.setText("Имя пользователя: 3–20 латинских букв, цифр, _ или . Если оставить пустым, оно создастся из имени.");
        note.setTextSize(12);
        TypedArray ta = c.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        ColorStateList muted = ta.getColorStateList(0);
        ta.recycle();
        if (muted != null) note.setTextColor(muted);
        note.setPadding(0, Math.round(8 * d), 0, Math.round(4 * d));
        box.addView(note, Sheet.matchWrap());

        AlertDialog dlg = b.setTitle("Изменить профиль")
                .setView(box)
                .setPositiveButton("Сохранить", null)       // обработчик ниже: с ошибкой диалог остаётся
                .setNegativeButton("Отмена", null)
                .create();
        if (dlg.getWindow() != null)
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Runnable submit = () -> {
            String n = Username.cleanName(name.getText());
            if (n.isEmpty()) {
                name.setError("Введите имя");
                name.requestFocus();
                return;
            }
            String u;
            if (Username.normalize(user.getText()).isEmpty()) {
                u = Username.fromName(n, new Random());
            } else {
                u = Username.parse(user.getText());
                if (u == null) {
                    user.setError(Username.ERROR);
                    user.requestFocus();
                    return;
                }
            }
            dlg.dismiss();
            st.setName(n);
            st.setUsername(u);
            a.onProfileChanged();
        };
        dlg.setOnShowListener(x -> {
            Button pb = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pb != null) pb.setOnClickListener(v -> submit.run());
        });
        user.setOnEditorActionListener((v, id, e) -> {
            if (id != EditorInfo.IME_ACTION_DONE) return false;
            submit.run();
            return true;
        });
        dialogs.show(dlg);
        name.requestFocus();
    }

    // ======================= фото профиля =======================
    /** Фото уже есть: заменить или удалить. */
    void showAvatarMenu() {
        if (gone()) return;
        Sheet s = sheet(null);
        s.addRow(R.drawable.ic_profile, "Выбрать новое фото", null, false, () -> { s.dismiss(); a.pickAvatar(); });
        s.addRow(R.drawable.ic_trash, "Удалить фото", null, false, () -> { s.dismiss(); a.removeAvatar(); });
        s.show();
    }

    // ======================= спонсорство =======================
    /** «Поддержать MUZLOVE»: рассказать друзьям и, если владелец вписал ссылку, поддержать проект. */
    void showSponsor() {
        if (gone()) return;
        Sheet s = sheet("Поддержать MUZLOVE");
        s.addCaption("MUZLOVE бесплатный и без рекламы. Если приложение вам нравится, расскажите о нём друзьям — "
                + "для нас это лучшая поддержка.");
        s.addRow(R.drawable.ic_social, "Рассказать друзьям", null, false, () -> { s.dismiss(); shareApp(); });
        if (!AppLinks.DONATE_CARD.isEmpty()) {
            s.addCaption("Поддержать разработчика можно переводом на карту — любой суммой из приложения вашего банка.");
            s.addRow(R.drawable.ic_heart, "Карта " + AppLinks.DONATE_CARD, "Копировать", false,
                    () -> { s.dismiss(); copyCard(); });
        }
        if (!AppLinks.SPONSOR_URL.isEmpty()) {
            s.addRow(R.drawable.ic_heart, "Поддержать проект", null, false, () -> { s.dismiss(); openSponsorPage(); });
        }
        s.show();
    }

    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Слушаю музыку в MUZLOVE — бесплатном плеере без рекламы. Присоединяйся!");
        startSafe(Intent.createChooser(send, "Рассказать друзьям"));
    }

    /** Копирует номер карты без пробелов — так его проще вставить в банковском приложении. */
    private void copyCard() {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Номер карты", AppLinks.DONATE_CARD.replace(" ", "")));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            Toast.makeText(a, "Номер карты скопирован", Toast.LENGTH_SHORT).show();
    }

    private void openSponsorPage() {
        startSafe(new Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.SPONSOR_URL)));
    }

    private void startSafe(Intent i) {
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, NO_APP, Toast.LENGTH_SHORT).show();
        }
    }

    // ======================= о приложении =======================
    void showAbout() {
        dialogs.message(dialogs.dark(), "MUZLOVE",
                "Версия " + versionName(a) + "\n\nБесплатный музыкальный плеер без рекламы: слушайте треки онлайн, "
                        + "загружайте свою музыку и собирайте плейлисты.");
    }

    /** Версия из PackageManager; «—», если прочитать не удалось. */
    @SuppressWarnings("deprecation")
    static String versionName(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            String v = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? pm.getPackageInfo(c.getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName
                    : pm.getPackageInfo(c.getPackageName(), 0).versionName;
            return v != null ? v : "—";
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "—";
        }
    }
}
