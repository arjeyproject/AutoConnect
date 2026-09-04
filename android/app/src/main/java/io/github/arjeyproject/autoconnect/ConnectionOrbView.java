package io.github.arjeyproject.autoconnect;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * AutoConnect connection dial.
 *
 * A calm, concentric dial: a static track, one state coloured progress ring, a soft halo,
 * a glass core and a power glyph. Motion is deliberately restrained - a slow rotation while
 * the tunnel is being built, a gentle breath while protected, nothing when idle and nothing
 * at all when the system has animations disabled.
 */
public final class ConnectionOrbView extends View {
    private static final int IDLE = 0;
    private static final int BUSY = 1;
    private static final int LIVE = 2;
    private static final int STOPPING = 3;
    private static final int FAULT = 4;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();

    private ValueAnimator motion;
    private int state = IDLE;
    private float phase;
    private String text = "";

    private int trackColor;
    private int coreColor;
    private int labelColor;
    private int captionColor;

    public ConnectionOrbView(Context context) { this(context, null); }

    public ConnectionOrbView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        caption.setTextAlign(Paint.Align.CENTER);
        readPalette();
    }

    private void readPalette() {
        trackColor = ContextCompat.getColor(getContext(), R.color.orb_track);
        coreColor = ContextCompat.getColor(getContext(), R.color.orb_core);
        labelColor = ContextCompat.getColor(getContext(), R.color.text);
        captionColor = ContextCompat.getColor(getContext(), R.color.orb_label);
    }

    /** Mirrors the tunnel state machine used by the VPN service. */
    public void setConnectionState(String value, String buttonText) {
        int next;
        if ("connected".equals(value)) next = LIVE;
        else if ("disconnecting".equals(value)) next = STOPPING;
        else if ("starting".equals(value) || "smart-testing".equals(value) || "scanning".equals(value)
                || "securing".equals(value) || "reconnecting".equals(value)) next = BUSY;
        else if ("error".equals(value) || "blocked".equals(value)) next = FAULT;
        else next = IDLE;
        boolean changed = next != state;
        state = next;
        text = buttonText == null ? "" : buttonText;
        if (changed) restartMotion();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.38f;
        if (radius <= 0) return;

        int start = stateColor(true);
        int end = stateColor(false);
        float alpha = isEnabled() ? 1f : 0.45f;

        // Halo - communicates state without shouting.
        float breath = state == LIVE ? 1f + 0.03f * sin(phase) : state == BUSY ? 1f + 0.02f * sin(phase) : 1f;
        float haloRadius = radius * 1.62f * breath;
        fill.setShader(new RadialGradient(cx, cy, haloRadius,
                new int[]{argb(start, (int) (46 * alpha)), argb(start, (int) (16 * alpha)), argb(start, 0)},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, haloRadius, fill);
        fill.setShader(null);

        // Outward pulse while protected.
        if (state == LIVE) {
            ring.setShader(null);
            ring.setStrokeWidth(radius * 0.02f);
            for (int i = 0; i < 2; i++) {
                float p = (phase + i * 0.5f) % 1f;
                ring.setColor(argb(start, (int) ((1f - p) * 52 * alpha)));
                canvas.drawCircle(cx, cy, radius * (1.06f + p * 0.5f), ring);
            }
        }

        // Track.
        float stroke = radius * 0.085f;
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        ring.setShader(null);
        ring.setColor(argb(trackColor, (int) (255 * alpha)));
        ring.setStrokeWidth(stroke);
        canvas.drawArc(bounds, 0f, 360f, false, ring);

        // State ring.
        ring.setShader(new SweepGradient(cx, cy, new int[]{start, end, start}, new float[]{0f, 0.5f, 1f}));
        ring.setStrokeWidth(stroke);
        float sweep = state == BUSY ? 110f : state == STOPPING ? 90f : state == IDLE ? 232f : 360f;
        float rotation = state == BUSY ? phase * 360f : state == STOPPING ? -phase * 360f : state == LIVE ? phase * 24f : -90f;
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        canvas.drawArc(bounds, state == IDLE ? 64f : 0f, sweep, false, ring);
        canvas.restore();
        ring.setShader(null);

        // Glass core.
        float core = radius * 0.78f;
        fill.setShader(new LinearGradient(cx, cy - core, cx, cy + core,
                new int[]{blend(coreColor, start, 0.10f), coreColor}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, core, fill);
        fill.setShader(null);
        ring.setColor(argb(start, (int) (58 * alpha)));
        ring.setStrokeWidth(Math.max(1f, radius * 0.014f));
        canvas.drawCircle(cx, cy, core, ring);
        ring.setColor(argb(start, (int) (20 * alpha)));
        canvas.drawCircle(cx, cy, core * 0.93f, ring);

        // Power glyph.
        float glyphRadius = radius * 0.24f;
        float glyphCy = cy - radius * 0.20f;
        glyph.setColor(argb(start, (int) (255 * alpha)));
        glyph.setStrokeWidth(Math.max(4f, radius * 0.055f));
        bounds.set(cx - glyphRadius, glyphCy - glyphRadius, cx + glyphRadius, glyphCy + glyphRadius);
        canvas.drawArc(bounds, -66f, 312f, false, glyph);
        canvas.drawLine(cx, glyphCy - glyphRadius * 1.34f, cx, glyphCy - glyphRadius * 0.12f, glyph);
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Label + caption. No letter spacing: Persian glyphs must stay joined.
        label.setColor(argb(labelColor, (int) (255 * alpha)));
        label.setTextSize(radius * 0.155f);
        label.getFontMetrics(metrics);
        float baseline = cy + radius * 0.30f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, cx, baseline, label);

        caption.setColor(argb(captionColor, (int) (255 * alpha)));
        caption.setTextSize(radius * 0.085f);
        canvas.drawText(getContext().getString(R.string.tap_to_secure), cx, baseline + radius * 0.19f, caption);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) press(0.965f, 90);
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) press(1f, 200);
        return super.onTouchEvent(event);
    }

    private void press(float scale, long duration) {
        animate().cancel();
        if (ValueAnimator.areAnimatorsEnabled()) animate().scaleX(scale).scaleY(scale).setDuration(duration).start();
        else { setScaleX(scale); setScaleY(scale); }
    }

    private void restartMotion() {
        stopMotion();
        if (!isShown() || !ValueAnimator.areAnimatorsEnabled() || state == FAULT || state == IDLE) {
            phase = 0f;
            invalidate();
            return;
        }
        motion = ValueAnimator.ofFloat(0f, 1f);
        motion.setDuration(state == BUSY ? 1500 : state == STOPPING ? 1000 : 3600);
        motion.setRepeatCount(ValueAnimator.INFINITE);
        motion.setInterpolator(state == LIVE ? new AccelerateDecelerateInterpolator() : new LinearInterpolator());
        motion.addUpdateListener(animation -> { phase = (Float) animation.getAnimatedValue(); invalidate(); });
        motion.start();
    }

    private void stopMotion() { if (motion != null) { motion.cancel(); motion = null; } }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); readPalette(); restartMotion(); }
    @Override protected void onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) restartMotion(); else stopMotion();
    }

    private int stateColor(boolean startEdge) {
        int id;
        if (state == LIVE) id = startEdge ? R.color.orb_live_start : R.color.orb_live_end;
        else if (state == BUSY || state == STOPPING) id = startEdge ? R.color.orb_busy_start : R.color.orb_busy_end;
        else if (state == FAULT) id = startEdge ? R.color.orb_fault_start : R.color.orb_fault_end;
        else id = startEdge ? R.color.orb_idle_start : R.color.orb_idle_end;
        return ContextCompat.getColor(getContext(), id);
    }

    private static float sin(float phase) { return (float) Math.sin(phase * Math.PI * 2); }
    private static int argb(int color, int alpha) { return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color)); }
    private static int blend(int base, int over, float amount) {
        return Color.rgb(
                (int) (Color.red(base) + (Color.red(over) - Color.red(base)) * amount),
                (int) (Color.green(base) + (Color.green(over) - Color.green(base)) * amount),
                (int) (Color.blue(base) + (Color.blue(over) - Color.blue(base)) * amount));
    }
}
