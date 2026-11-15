package com.example.muzlove;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Единственное место, где создаются AlertDialog приложения: тема диалога (тёмная или светлая, фон —
 *  поверхность приложения со скруглением, акцент #8B5CF6) выбирается по теме приложения, поэтому
 *  заголовок, текст, поля ввода, подсказки и кнопки читаются в обеих темах. Открытые диалоги
 *  закрываются в onDestroy активности. Только главный поток. */
final class AppDialogs {

    private final MainActivity a;
    private final List<AlertDialog> open = new ArrayList<>();

    AppDialogs(MainActivity a) { this.a = a; }

    private boolean gone() { return a.isFinishing() || a.isDestroyed(); }

    /** Тема приложения: dark = !isLightTheme(). */
    boolean dark() { return !a.isLightTheme(); }

    /** Построитель диалога в нужной теме. Контекст для вью диалога берите из builder.getContext(). */
    AlertDialog.Builder builder(boolean dark) {
        return new AlertDialog.Builder(a, dark ? R.style.Theme_MuzLove_Alert_Dark : R.style.Theme_MuzLove_Alert_Light);
    }

    /** Показывает диалог и следит за ним (закроется вместе с активностью). */
    void show(AlertDialog d) {
        if (gone()) return;
        open.add(d);
        d.setOnDismissListener(x -> open.remove(d));
        try { d.show(); } catch (RuntimeException e) { open.remove(d); }   // окно активности уже ушло
    }

    void dismissAll() {
        for (AlertDialog d : new ArrayList<>(open)) {
            try { if (d.isShowing()) d.dismiss(); } catch (RuntimeException ignored) {}
        }
        open.clear();
    }

    /** Подтверждение: «Отмена» и positive (выполняет ok). */
    void confirm(boolean dark, String title, String message, String positive, Runnable ok) {
        show(builder(dark)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (x, w) -> ok.run())
                .setNegativeButton("Отмена", null)
                .create());
    }

    /** Простой диалог с одной кнопкой «OK». */
    void message(boolean dark, String title, String message) {
        show(builder(dark).setTitle(title).setMessage(message).setPositiveButton("OK", null).create());
    }

    /** Ввод строки (не длиннее maxLen). allowEmpty == false — кнопка недоступна, пока поле пустое.
     *  ok получает обрезанный по краям текст. */
    void input(boolean dark, String title, String initial, String hint, String positive,
               boolean allowEmpty, int maxLen, Consumer<String> ok) {
        if (gone()) return;
        AlertDialog.Builder b = builder(dark);
        Context c = b.getContext();
        EditText in = new AppCompatEditText(c);
        in.setHint(hint);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        in.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        if (initial != null) {
            in.setText(initial);
            in.setSelection(in.length());
        }
        FrameLayout wrap = new FrameLayout(c);
        int p = Math.round(20 * c.getResources().getDisplayMetrics().density);
        wrap.setPadding(p, p / 2, p, 0);
        wrap.addView(in);
        AlertDialog d = b.setTitle(title)
                .setView(wrap)
                .setPositiveButton(positive, null)      // обработчик ниже: диалог не закрывается с пустым полем
                .setNegativeButton("Отмена", null)
                .create();
        if (d.getWindow() != null)
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        Runnable submit = () -> {
            String v = in.getText().toString().trim();
            if (v.isEmpty() && !allowEmpty) return;
            d.dismiss();
            ok.accept(v);
        };
        d.setOnShowListener(x -> {
            Button pb = d.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pb == null) return;
            pb.setOnClickListener(v -> submit.run());
            if (!allowEmpty) pb.setEnabled(in.getText().toString().trim().length() > 0);
        });
        if (!allowEmpty) {
            in.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c1, int a1) {}
                @Override public void onTextChanged(CharSequence s, int st, int b1, int c1) {}
                @Override public void afterTextChanged(Editable e) {
                    Button pb = d.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (pb != null) pb.setEnabled(e.toString().trim().length() > 0);
                }
            });
        }
        in.setOnEditorActionListener((v, id, e) -> {
            if (id != EditorInfo.IME_ACTION_DONE) return false;
            submit.run();
            return true;
        });
        show(d);
        in.requestFocus();
    }
}
