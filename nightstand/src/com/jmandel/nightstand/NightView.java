package com.jmandel.nightstand;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A dim red clock that drifts to avoid burn-in, plus a status line and a transient volume bar. */
class NightView extends View {
    private static final int RED = Color.rgb(110, 0, 0);
    private static final int DIM_RED = Color.rgb(60, 0, 0);
    private static final long VOLUME_SHOWN_MS = 2500;

    private final Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint();
    private final SimpleDateFormat fmt = new SimpleDateFormat("h:mm", Locale.US);
    private String status;
    private int volume, volumeMax;
    private long volumeShownAt;

    NightView(Context ctx) {
        super(ctx);
        setBackgroundColor(Color.BLACK);
        clock.setColor(RED);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setTextAlign(Paint.Align.CENTER);
        small.setColor(DIM_RED);
        small.setTextAlign(Paint.Align.CENTER);
        bar.setColor(DIM_RED);
    }

    void setStatus(String s) {
        status = s;
        invalidate();
    }

    void showVolume(int v, int max) {
        volume = v;
        volumeMax = max;
        volumeShownAt = SystemClock.uptimeMillis();
        invalidate();
        postDelayed(this::invalidate, VOLUME_SHOWN_MS + 50);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        long minute = System.currentTimeMillis() / 60_000;
        // Drift a little each minute so nothing stays lit in one place.
        float dx = (float) Math.sin(minute * 0.7) * w * 0.12f;
        float dy = (float) Math.cos(minute * 0.5) * h * 0.12f;

        clock.setTextSize(Math.min(w, h) * 0.28f);
        c.drawText(fmt.format(new Date()), w / 2f + dx, h / 2f + dy, clock);

        small.setTextSize(Math.min(w, h) * 0.035f);
        if (status != null) {
            String s = status.length() > 48 ? status.substring(0, 47) + "…" : status;
            c.drawText(s, w / 2f + dx, h / 2f + dy + clock.getTextSize() * 0.45f, small);
        }

        if (SystemClock.uptimeMillis() - volumeShownAt < VOLUME_SHOWN_MS && volumeMax > 0) {
            // Horizontal, matching the sideways drag that sets it.
            float barW = w * 0.5f, left = (w - barW) / 2f, y = h * 0.85f, bh = h * 0.012f;
            float filled = barW * volume / volumeMax;
            bar.setStyle(Paint.Style.STROKE);
            c.drawRect(left, y, left + barW, y + bh, bar);
            bar.setStyle(Paint.Style.FILL);
            c.drawRect(left, y, left + filled, y + bh, bar);
        }

        // Redraw at the next minute boundary.
        long now = System.currentTimeMillis();
        removeCallbacks(nextMinute);
        postDelayed(nextMinute, 60_000 - now % 60_000 + 50);
    }

    private final Runnable nextMinute = this::invalidate;
}
