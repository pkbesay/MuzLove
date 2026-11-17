package com.example.muzlove;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

/** Полоса прогресса в виде звуковой волны. Перетаскивание = перемотка:
 *  пока палец на волне, она показывает позицию под пальцем, перемотка — одна, при отпускании. */
public class WaveformView extends View {

    public interface OnSeekListener {
        /** Палец опущен: внешние обновления прогресса больше не должны двигать волну. */
        void onStartSeek();
        /** Позиция под пальцем (0..1) — для подписи времени. */
        void onSeekMove(float ratio);
        /** Палец отпущен: перемотать сюда. */
        void onStopSeek(float ratio);
        /** Жест отменён (например, перехватил родитель): перемотки нет. */
        void onCancelSeek();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] bars = new float[50];
    private float progress = 0f;
    private int colorOn = Color.WHITE;
    private int colorOff = 0x33FFFFFF;
    private boolean dragging;
    private OnSeekListener listener;

    public WaveformView(Context c, AttributeSet a) {
        super(c, a);
        setSeed(1);
    }

    public void setOnSeekListener(OnSeekListener l) { listener = l; }

    public void setSeed(int id) {
        long s = id * 9301L + 49297L;
        for (int i = 0; i < bars.length; i++) {
            s = (s * 9301L + 49297L) % 233280L;
            bars[i] = 0.28f + (s / 233280f) * 0.72f;
        }
        invalidate();
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public void setColors(int on, int off) {
        colorOn = on; colorOff = off; invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        int n = bars.length;
        float gap = dp(2);
        float bw = (w - (n - 1) * gap) / n;
        if (bw <= 0) return;
        for (int i = 0; i < n; i++) {
            float bh = Math.max(dp(3), bars[i] * h);
            float left = i * (bw + gap);
            float top = (h - bh) / 2f;
            paint.setColor(((float) i / n) <= progress ? colorOn : colorOff);
            rect.set(left, top, left + bw, top + bh);
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled() || getWidth() <= 0) return false;
        float ratio = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ViewParent p = getParent();
                if (p != null) p.requestDisallowInterceptTouchEvent(true);
                dragging = true;
                setProgress(ratio);
                if (listener != null) { listener.onStartSeek(); listener.onSeekMove(ratio); }
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                setProgress(ratio);
                if (listener != null) listener.onSeekMove(ratio);
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) return false;
                dragging = false;
                setProgress(ratio);
                if (listener != null) listener.onStopSeek(ratio);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                if (listener != null) listener.onCancelSeek();
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
