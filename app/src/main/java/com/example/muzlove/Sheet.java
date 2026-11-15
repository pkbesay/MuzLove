package com.example.muzlove;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/** Нижняя шторка в стиле MUZLOVE: свой фон со скруглением 20dp сверху, ручка, необязательный
 *  заголовок и подзаголовок, содержимое в вертикальном LinearLayout внутри NestedScrollView
 *  (длинное прокручивается), снизу отступ 24dp.
 *  Палитра по флагу dark: тёмная (#161422 / #FFFFFF / #A09CB8) или светлая (#FFFFFF / #15131F / #5D5A72).
 *  Все вью создаются от ctx (тема шторки: акцент, переключатели, ползунки). Только главный поток. */
final class Sheet {

    final boolean dark;
    /** Палитра: фон, текст, вторичный текст, акцент (отметки, текущий трек), текст на акценте, фон «чипов». */
    final int bg, text, muted, accent, onAccent, chip;
    final Context ctx;
    final BottomSheetDialog dialog;
    /** Сюда добавляется содержимое (вертикальный список). */
    final LinearLayout content;
    final NestedScrollView scroll;
    final TextView title, subtitle;

    private final Activity activity;
    private final List<Runnable> showActions = new ArrayList<>(), dismissActions = new ArrayList<>();

    static Sheet create(Activity a, boolean dark, CharSequence title) { return new Sheet(a, dark, title); }

    private Sheet(Activity a, boolean dark, CharSequence titleText) {
        activity = a;
        this.dark = dark;
        bg = dark ? 0xFF161422 : 0xFFFFFFFF;
        text = dark ? 0xFFFFFFFF : 0xFF15131F;
        muted = dark ? 0xFFA09CB8 : 0xFF5D5A72;
        accent = dark ? 0xFFF4FE52 : 0xFF8B5CF6;
        onAccent = dark ? 0xFF0A0913 : 0xFFFFFFFF;
        chip = dark ? 0x1FFFFFFF : 0xFFF2F1F6;
        dialog = new BottomSheetDialog(a, dark ? R.style.Theme_MuzLove_Sheet_Dark : R.style.Theme_MuzLove_Sheet_Light);
        ctx = dialog.getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        float r = dp(20);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(g);

        View handle = new View(ctx);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(withAlpha(muted, 0x66));
        hg.setCornerRadius(dp(2));
        handle.setBackground(hg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(10);
        hlp.bottomMargin = dp(8);
        root.addView(handle, hlp);

        title = new TextView(ctx);
        title.setTextColor(text);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(20), dp(6), dp(20), dp(2));
        root.addView(title, matchWrap());
        subtitle = new TextView(ctx);
        subtitle.setTextColor(muted);
        subtitle.setTextSize(13);
        subtitle.setPadding(dp(20), 0, dp(20), 0);
        root.addView(subtitle, matchWrap());
        setTitle(titleText);
        setSubtitle(null);

        scroll = new NestedScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, matchWrap());

        // если окно шторки рисуется под панелью навигации — поднимаем содержимое над ней
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(0, 0, 0, nav.bottom);
            return insets;
        });

        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        clearContainer();
        BottomSheetBehavior<FrameLayout> b = dialog.getBehavior();
        b.setSkipCollapsed(true);
        b.setState(BottomSheetBehavior.STATE_EXPANDED);
        b.setMaxHeight((int) (a.getResources().getDisplayMetrics().heightPixels * 0.9f));

        dialog.setOnShowListener(d -> {
            clearContainer();
            for (Runnable x : new ArrayList<>(showActions)) x.run();
        });
        dialog.setOnDismissListener(d -> {
            for (Runnable x : new ArrayList<>(dismissActions)) x.run();
        });
    }

    /** Фон контейнера библиотеки прозрачный: виден только наш скруглённый фон. */
    private void clearContainer() {
        View c = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (c != null) c.setBackgroundColor(Color.TRANSPARENT);
    }

    // ---------- жизненный цикл ----------
    void setTitle(CharSequence s) {
        title.setText(s);
        title.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
    }

    void setSubtitle(CharSequence s) {
        subtitle.setText(s);
        subtitle.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
    }

    /** Выполнить, когда шторка показана (можно несколько). */
    void onShow(Runnable r) { showActions.add(r); }
    /** Выполнить при закрытии любым способом (можно несколько). */
    void onDismiss(Runnable r) { dismissActions.add(r); }

    void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try { dialog.show(); } catch (RuntimeException ignored) {}   // окно активности уже ушло
    }

    void dismiss() {
        if (!dialog.isShowing()) return;
        try { dialog.dismiss(); } catch (RuntimeException ignored) {}
    }

    boolean isShowing() { return dialog.isShowing(); }

    // ---------- строители ----------
    /** Строка: иконка 24dp (0 — без иконки) + подпись + справа значение и/или отметка.
     *  onClick == null — строка только для чтения (без отклика на нажатие). */
    View row(int icon, CharSequence label, CharSequence value, boolean checked, Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(20), dp(6), dp(20), dp(6));
        if (icon != 0) {
            ImageView iv = new ImageView(ctx);
            iv.setImageResource(icon);
            iv.setColorFilter(text);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
            ilp.rightMargin = dp(16);
            row.addView(iv, ilp);
        }
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(text);
        tv.setTextSize(15);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!TextUtils.isEmpty(value)) {
            TextView vv = new TextView(ctx);
            vv.setText(value);
            vv.setTextColor(checked ? accent : muted);
            vv.setTextSize(14);
            vv.setMaxLines(1);
            vv.setEllipsize(TextUtils.TruncateAt.END);
            vv.setMaxWidth(dp(170));
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vlp.leftMargin = dp(12);
            row.addView(vv, vlp);
        }
        if (checked) {
            ImageView ck = new ImageView(ctx);
            ck.setImageResource(R.drawable.ic_check);
            ck.setColorFilter(accent);
            ck.setContentDescription("Выбрано");
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(20), dp(20));
            clp.leftMargin = dp(12);
            row.addView(ck, clp);
        }
        if (onClick != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(pressable(false));
            row.setOnClickListener(v -> onClick.run());
        }
        return row;
    }

    View addRow(int icon, CharSequence label, CharSequence value, boolean checked, Runnable onClick) {
        View r = row(icon, label, value, checked, onClick);
        content.addView(r, matchWrap());
        return r;
    }

    /** Шапка трека: обложка-градиент 48dp + название + исполнитель. */
    View trackHeader(Track t) {
        LinearLayout h = new LinearLayout(ctx);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(20), dp(4), dp(20), dp(12));
        View cover = new View(ctx);
        cover.setBackground(cover(t, dp(10)));
        h.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), 0, 0, 0);
        TextView tt = new TextView(ctx);
        tt.setText(t.title);
        tt.setTextColor(text);
        tt.setTextSize(16);
        tt.setTypeface(Typeface.DEFAULT_BOLD);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        TextView ta = new TextView(ctx);
        ta.setText(t.artist);
        ta.setTextColor(muted);
        ta.setTextSize(14);
        ta.setSingleLine(true);
        ta.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(tt);
        info.addView(ta);
        h.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return h;
    }

    View addTrackHeader(Track t) {
        View h = trackHeader(t);
        content.addView(h, matchWrap());
        return h;
    }

    /** Поясняющий текст вторичным цветом. */
    TextView addCaption(CharSequence s) {
        TextView tv = new TextView(ctx);
        tv.setText(s);
        tv.setTextColor(muted);
        tv.setTextSize(14);
        tv.setLineSpacing(0, 1.15f);
        tv.setPadding(dp(20), dp(8), dp(20), dp(8));
        content.addView(tv, matchWrap());
        return tv;
    }

    /** Строка «название — значение» (только чтение, значение может переноситься). */
    View addInfo(CharSequence label, CharSequence value) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setPadding(dp(20), dp(6), dp(20), dp(6));
        TextView l = new TextView(ctx);
        l.setText(label);
        l.setTextColor(muted);
        l.setTextSize(14);
        row.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = new TextView(ctx);
        v.setText(TextUtils.isEmpty(value) ? "—" : value);
        v.setTextColor(text);
        v.setTextSize(15);
        v.setGravity(Gravity.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        vlp.leftMargin = dp(16);
        row.addView(v, vlp);
        content.addView(row, matchWrap());
        return row;
    }

    // ---------- утилиты ----------
    /** ?attr/selectableItemBackground(Borderless) из темы шторки (рябь под тёмный или светлый фон). */
    Drawable pressable(boolean borderless) {
        TypedValue tv = new TypedValue();
        int attr = borderless ? android.R.attr.selectableItemBackgroundBorderless : android.R.attr.selectableItemBackground;
        if (!ctx.getTheme().resolveAttribute(attr, tv, true) || tv.resourceId == 0) return null;
        return ContextCompat.getDrawable(ctx, tv.resourceId);
    }

    int dp(float v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Обложка трека: диагональный градиент его цветов. */
    static GradientDrawable cover(Track t, float radiusPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.c1, t.c2});
        g.setCornerRadius(radiusPx);
        return g;
    }

    static int withAlpha(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }
}
