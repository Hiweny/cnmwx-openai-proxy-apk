package com.hiweny.freeapiopenai;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 轻量动态渐变背景，参考 RikkaHub 的 MeshGradientBackground 思路重写：
 * 底层线性渐变 + 多个缓慢移动的径向柔光光斑。
 */
public class MeshGradientView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase = 0f;
    private ValueAnimator animator;

    public MeshGradientView(Context context) {
        super(context);
        start();
    }

    private void start() {
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(18000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        paint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{Color.rgb(9, 13, 28), Color.rgb(18, 30, 55), Color.rgb(10, 18, 35)},
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, w, h, paint);

        drawBlob(canvas, w * (0.18f + 0.05f * sin(phase)), h * (0.18f + 0.04f * cos(phase * 0.8f)),
                Math.max(w, h) * 0.55f, Color.argb(95, 59, 130, 246));
        drawBlob(canvas, w * (0.78f + 0.06f * cos(phase * 0.9f)), h * (0.30f + 0.05f * sin(phase * 1.1f)),
                Math.max(w, h) * 0.48f, Color.argb(80, 168, 85, 247));
        drawBlob(canvas, w * (0.52f + 0.04f * sin(phase * 1.2f)), h * (0.82f + 0.04f * cos(phase)),
                Math.max(w, h) * 0.50f, Color.argb(70, 20, 184, 166));

        paint.setShader(null);
        paint.setColor(Color.argb(135, 8, 13, 26));
        canvas.drawRect(0, 0, w, h, paint);
    }

    private void drawBlob(Canvas canvas, float cx, float cy, float radius, int color) {
        paint.setShader(new RadialGradient(
                cx, cy, radius,
                new int[]{color, Color.TRANSPARENT},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private float sin(float v) {
        return (float) Math.sin(v);
    }

    private float cos(float v) {
        return (float) Math.cos(v);
    }
}
