package com.example.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * Full-screen ambient backdrop: dark gradient, two coloured glows that change with the selected
 * platform, and a few slowly rising particles. Redraws about 16 times a second to stay light on
 * the TV's processor.
 */
public class AmbientView extends View {

    private static final int PARTICLES = 36;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArgbEvaluator evaluator = new ArgbEvaluator();
    private final Random rnd = new Random();

    // 4 colours: background top-left, background bottom-right, glow 1, glow 2
    private final int[] cur = {0xFF070C18, 0xFF03060D, 0x442563EB, 0x30F59E0B};
    private final int[] from = new int[4];
    private final int[] to = new int[4];

    private Shader bgShader;
    private Shader glow1;
    private Shader glow2;
    private int w;
    private int h;
    private ValueAnimator anim;
    private boolean running;

    private final float[] px = new float[PARTICLES];
    private final float[] py = new float[PARTICLES];
    private final float[] ps = new float[PARTICLES];
    private final float[] pv = new float[PARTICLES];
    private final int[] pa = new int[PARTICLES];

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, 60);
        }
    };

    public AmbientView(Context c) {
        super(c);
    }

    /** Jump straight to a theme (no animation). */
    public void setThemeNow(int[] t) {
        if (anim != null) anim.cancel();
        System.arraycopy(t, 0, cur, 0, 4);
        rebuild();
        invalidate();
    }

    /** Fade to a new theme. */
    public void setTheme(int[] t) {
        if (anim != null) anim.cancel();
        System.arraycopy(cur, 0, from, 0, 4);
        System.arraycopy(t, 0, to, 0, 4);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(700);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                for (int i = 0; i < 4; i++) {
                    cur[i] = (Integer) evaluator.evaluate(f, from[i], to[i]);
                }
                rebuild();
            }
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw;
        h = nh;
        float unit = Math.max(1f, w / 960f);
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = rnd.nextFloat() * h;
            ps[i] = (rnd.nextFloat() * 1.6f + 0.6f) * unit;
            pv[i] = (rnd.nextFloat() * 0.5f + 0.2f) * unit * 1.6f;
            pa[i] = 30 + rnd.nextInt(90);
        }
        rebuild();
    }

    private void rebuild() {
        if (w == 0 || h == 0) return;
        bgShader = new LinearGradient(0, 0, w, h, cur[0], cur[1], Shader.TileMode.CLAMP);
        float r = w * 0.55f;
        glow1 = new RadialGradient(w * 0.04f, h * 0.02f, r, cur[2], cur[2] & 0x00FFFFFF,
                Shader.TileMode.CLAMP);
        glow2 = new RadialGradient(w * 0.98f, h * 1.0f, r, cur[3], cur[3] & 0x00FFFFFF,
                Shader.TileMode.CLAMP);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        post(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(frame);
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bgShader == null) return;
        paint.setShader(bgShader);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(glow1);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(glow2);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        for (int i = 0; i < PARTICLES; i++) {
            py[i] -= pv[i];
            if (py[i] < 0) {
                py[i] = h;
                px[i] = rnd.nextFloat() * w;
            }
            paint.setColor(Color.argb(pa[i], 255, 255, 255));
            canvas.drawCircle(px[i], py[i], ps[i], paint);
        }
    }
}
