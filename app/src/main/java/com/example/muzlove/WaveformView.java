package com.example.muzlove;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Полоса прогресса в виде звуковой волны. Тап/перетаскивание = перемотка.
 *  В простом режиме (setSimpleMode) рисуется обычная тонкая полоса. */
public class WaveformView extends View {

    public interface OnSeek { void onSeek(float ratio); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] bars = new float[50];
    private float progress = 0f;
    private int colorOn = Color.WHITE;
    private int colorOff = 0x33FFFFFF;
    private boolean simple = false;
    private OnSeek listener;

    public WaveformView(Context c, AttributeSet a) {
        super(c, a);
        setSeed(1);
    }

    public void setOnSeek(OnSeek l) { listener = l; }

    public void setSimpleMode(boolean s) { simple = s; invalidate(); }

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

        if (simple) {
            float th = dp(5);
            float top = (h - th) / 2f;
            float r = th / 2f;
            paint.setColor(colorOff);
            canvas.drawRoundRect(new RectF(0, top, w, top + th), r, r, paint);
            paint.setColor(colorOn);
            canvas.drawRoundRect(new RectF(0, top, w * progress, top + th), r, r, paint);
            paint.setColor(colorOn);
            canvas.drawCircle(Math.max(r, Math.min(w - r, w * progress)), h / 2f, dp(7), paint);
            return;
        }

        int n = bars.length;
        float gap = dp(2);
        float bw = (w - (n - 1) * gap) / n;
        for (int i = 0; i < n; i++) {
            float bh = Math.max(dp(3), bars[i] * h);
            float left = i * (bw + gap);
            float top = (h - bh) / 2f;
            paint.setColor(((float) i / n) <= progress ? colorOn : colorOff);
            RectF rect = new RectF(left, top, left + bw, top + bh);
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float ratio = Math.max(0f, Math.min(1f, e.getX() / getWidth()));
            setProgress(ratio);
            if (listener != null) listener.onSeek(ratio);
            return true;
        }
        return super.onTouchEvent(e);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
