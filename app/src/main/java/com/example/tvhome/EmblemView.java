package com.example.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** A simple original badge: rounded square with a gradient and a play triangle. */
public class EmblemView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path tri = new Path();
    private int c1;
    private int c2;

    public EmblemView(Context ctx, int c1, int c2) {
        super(ctx);
        this.c1 = c1;
        this.c2 = c2;
    }

    public void setColors(int a, int b) {
        c1 = a;
        c2 = b;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        rect.set(0, 0, w, h);
        paint.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, w * 0.28f, w * 0.28f, paint);
        paint.setShader(null);
        paint.setColor(0xFFFFFFFF);
        tri.reset();
        tri.moveTo(w * 0.38f, h * 0.28f);
        tri.lineTo(w * 0.38f, h * 0.72f);
        tri.lineTo(w * 0.75f, h * 0.50f);
        tri.close();
        canvas.drawPath(tri, paint);
    }
}
