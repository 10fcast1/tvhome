package com.example.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * A procedurally drawn sunset landscape (sky, glowing sun, three layers of hills) that
 * takes on the colour of whichever app is focused. Original artwork, no image files.
 */
public class ScenicView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accent = 0xFF3A4A8A;
    private int w, h;

    private Shader sky, sun, scrim;
    private Path far, mid, near;
    private int cFar, cMid, cNear;
    private ValueAnimator running;

    public ScenicView(Context c) {
        super(c);
    }

    public void setAccent(int color) {
        accent = color;
        rebuild();
        invalidate();
    }

    public void animateTo(int target) {
        if (running != null) running.cancel();
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), accent, target);
        va.setDuration(450);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                accent = (Integer) a.getAnimatedValue();
                rebuild();
                invalidate();
            }
        });
        running = va;
        va.start();
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw;
        h = nh;
        rebuild();
    }

    private void rebuild() {
        if (w == 0 || h == 0) return;
        int top = AppRepo.mix(accent, 0xFF0B1030, 0.72f);
        int middle = AppRepo.mix(accent, 0xFF5B3A8A, 0.50f);
        int horizon = AppRepo.mix(accent, 0xFFFFA45C, 0.55f);

        sky = new LinearGradient(0, 0, 0, h * 0.78f,
                new int[]{top, middle, horizon}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        sun = new RadialGradient(w * 0.72f, h * 0.50f, w * 0.34f,
                new int[]{0xAAFFFFFF, 0x33FFD9A0, 0x00FFD9A0}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        scrim = new LinearGradient(0, h * 0.30f, 0, h,
                0x00000000, 0xCC000000, Shader.TileMode.CLAMP);

        cFar = AppRepo.mix(horizon, 0xFF2A2350, 0.62f);
        cMid = AppRepo.mix(cFar, 0xFF000000, 0.35f);
        cNear = AppRepo.mix(cMid, 0xFF000000, 0.50f);

        far = hill(h * 0.60f, h * 0.07f, 0f);
        mid = hill(h * 0.70f, h * 0.06f, 0.5f);
        near = hill(h * 0.80f, h * 0.05f, 1f);
    }

    private Path hill(float base, float amp, float phase) {
        Path p = new Path();
        p.moveTo(0, h);
        p.lineTo(0, base);
        p.cubicTo(w * 0.25f, base - amp * (1f + phase), w * 0.45f, base + amp * 0.6f, w * 0.62f, base - amp * 0.4f);
        p.cubicTo(w * 0.80f, base - amp * 1.2f, w * 0.92f, base + amp * 0.3f, w, base - amp * 0.2f);
        p.lineTo(w, h);
        p.close();
        return p;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sky == null) return;
        paint.setShader(sky);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(sun);
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(null);
        paint.setColor(cFar);
        canvas.drawPath(far, paint);
        paint.setColor(cMid);
        canvas.drawPath(mid, paint);
        paint.setColor(cNear);
        canvas.drawPath(near, paint);

        paint.setShader(scrim);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }
}
