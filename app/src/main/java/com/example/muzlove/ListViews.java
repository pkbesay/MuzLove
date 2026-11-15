package com.example.muzlove;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Строки и блоки над списком треков (медиатека, поиск, плейлист) в цветах текущей темы.
 *  Только главный поток. */
final class ListViews {

    final Context c;
    final boolean light;
    final int text, muted;

    ListViews(Context c, boolean light, int text, int muted) {
        this.c = c;
        this.light = light;
        this.text = text;
        this.muted = muted;
    }

    int dp(float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** Рябь под тему: bounded — для строки, иначе круглая (кнопка-иконка). */
    static RippleDrawable ripple(boolean light, boolean bounded) {
        return new RippleDrawable(ColorStateList.valueOf(light ? 0x1F000000 : 0x33FFFFFF), null,
                bounded ? new ColorDrawable(Color.WHITE) : null);
    }

    /** Рябь со скруглёнными углами (для кнопок с фоном-«таблеткой»). onDark — кнопка тёмная или цветная. */
    static RippleDrawable rippleRounded(boolean onDark, float radiusPx) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(onDark ? 0x33FFFFFF : 0x1F000000), null, mask);
    }

    /** Квадрат с тонкой рамкой (карточка «Новый плейлист», строки-действия). */
    static GradientDrawable outline(boolean light, float radiusPx, float strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(light ? 0x08000000 : 0x0DFFFFFF);
        g.setStroke(Math.max(1, Math.round(strokePx)), light ? 0x33000000 : 0x40FFFFFF);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Строка-действие: рамка 48dp с иконкой + подпись («Новый плейлист», «Загрузить трек»). */
    View actionRow(int icon, String label, Runnable onClick) {
        LinearLayout row = baseRow(onClick, null);
        FrameLayout box = new FrameLayout(c);
        box.setBackground(outline(light, dp(10), dp(1.5f)));
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setColorFilter(text);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        row.addView(box, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextColor(text);
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(14);
        row.addView(tv, lp);
        row.setContentDescription(label);
        return row;
    }

    /** Строка плейлиста: градиент 48dp со значком, название, описание. */
    View playlistRow(Playlist p, String desc, Runnable onClick, Runnable onLongClick) {
        LinearLayout row = baseRow(onClick, onLongClick);
        FrameLayout thumb = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{p.c1, p.c2});
        g.setCornerRadius(dp(10));
        thumb.setBackground(g);
        ImageView iv = new ImageView(c);
        iv.setImageResource(R.drawable.ic_playlist);
        iv.setColorFilter(Color.WHITE);
        iv.setAlpha(0.9f);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        thumb.addView(iv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        row.addView(thumb, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(c);
        name.setText(Playlists.name(p.name));
        name.setTextColor(text);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        TextView ds = new TextView(c);
        ds.setText(desc);
        ds.setTextColor(muted);
        ds.setTextSize(13);
        ds.setSingleLine(true);
        ds.setEllipsize(TextUtils.TruncateAt.END);
        ds.setPadding(0, dp(2), 0, 0);
        texts.addView(name);
        texts.addView(ds);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(14);
        row.addView(texts, lp);
        row.setContentDescription(Playlists.name(p.name) + ", " + desc);
        return row;
    }

    /** Пустое состояние по центру: необязательный заголовок, пояснение и кнопка. */
    View emptyState(String title, String message, String button, int buttonIcon, Runnable onClick) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(48), dp(24), dp(24));
        if (title != null) {
            TextView t = new TextView(c);
            t.setText(title);
            t.setTextColor(text);
            t.setTextSize(18);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setGravity(Gravity.CENTER);
            box.addView(t, wrap());
        }
        TextView m = new TextView(c);
        m.setText(message);
        m.setTextColor(muted);
        m.setTextSize(15);
        m.setGravity(Gravity.CENTER);
        m.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams mlp = wrap();
        if (title != null) mlp.topMargin = dp(8);
        box.addView(m, mlp);
        if (button != null) {
            LinearLayout.LayoutParams blp = wrap();
            blp.topMargin = dp(20);
            box.addView(accentButton(button, buttonIcon, onClick), blp);
        }
        return box;
    }

    /** Кнопка-«таблетка» с фирменным градиентом и белым текстом. */
    View accentButton(String label, int icon, Runnable onClick) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(46));
        b.setPadding(dp(22), dp(10), dp(24), dp(10));
        b.setBackgroundResource(R.drawable.btn_accent);
        b.setForeground(rippleRounded(true, dp(16)));
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(label);
        b.setOnClickListener(v -> onClick.run());
        if (icon != 0) {
            ImageView iv = new ImageView(c);
            iv.setImageResource(icon);
            iv.setColorFilter(Color.WHITE);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
            ilp.rightMargin = dp(8);
            b.addView(iv, ilp);
        }
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        b.addView(tv);
        return b;
    }

    private LinearLayout baseRow(Runnable onClick, Runnable onLongClick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(ripple(light, true));
        row.setOnClickListener(v -> onClick.run());
        if (onLongClick != null) row.setOnLongClickListener(v -> { onLongClick.run(); return true; });
        return row;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
