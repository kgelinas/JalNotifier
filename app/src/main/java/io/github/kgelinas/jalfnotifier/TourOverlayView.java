package io.github.kgelinas.jalfnotifier;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Full-screen overlay that dims the background and punches a spotlight hole
 * over the highlighted view. Uses a software layer so PorterDuff.CLEAR works.
 */
public class TourOverlayView extends View {

    private final Paint dimPaint = new Paint();
    private final Paint clearPaint = new Paint();
    private final Paint ringPaint = new Paint();
    private final Paint pulseRingPaint = new Paint();

    /** The spotlight rect in window-absolute coordinates (already transformed to view-local). */
    private RectF spotlightRect = null;
    private float spotlightRadius = 0f;

    /** 0 = rect hole, 1 = circle hole */
    private boolean useCircle = false;

    private float animatedRadius = 0f;
    private ValueAnimator pulseAnimator;

    public TourOverlayView(Context context) {
        super(context);
        init();
    }

    public TourOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        dimPaint.setColor(Color.parseColor("#CC000000")); // 80% black
        dimPaint.setStyle(Paint.Style.FILL);

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);

        int primaryColor = Color.WHITE;
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true);
            primaryColor = tv.data;
        } catch (Exception ignored) {}

        ringPaint.setColor(primaryColor);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dpToPx(3));
        ringPaint.setAntiAlias(true);

        pulseRingPaint.setColor(primaryColor);
        pulseRingPaint.setStyle(Paint.Style.STROKE);
        pulseRingPaint.setStrokeWidth(dpToPx(1.5f));
        pulseRingPaint.setAntiAlias(true);
    }

    /**
     * Sets a rounded-rect spotlight.
     *
     * @param rect    Bounding box in this view's coordinate space (use locateView()).
     * @param cornerR Corner radius; 0 for sharp rect, large value for pill/circle.
     */
    public void setSpotlightRect(RectF rect, float cornerR) {
        this.spotlightRect = rect == null ? null : new RectF(rect);
        this.spotlightRadius = cornerR;
        this.useCircle = false;
        stopPulse();
        invalidate();
    }

    /** Sets a circular spotlight centred on the rect. */
    public void setSpotlightCircle(RectF rect) {
        this.spotlightRect = rect == null ? null : new RectF(rect);
        float hw = rect != null ? Math.max(rect.width(), rect.height()) / 2f + 16f : 0;
        this.spotlightRadius = hw;
        this.useCircle = true;
        stopPulse();
        invalidate();
    }

    /** Clears spotlight (pure dim overlay with no hole). */
    public void clearSpotlight() {
        this.spotlightRect = null;
        stopPulse();
        invalidate();
    }

    /** Starts a subtle pulsing animation on the spotlight. */
    public void startPulse() {
        stopPulse();
        if (spotlightRect == null) return;
        float base = spotlightRadius;
        pulseAnimator = ValueAnimator.ofFloat(base - 6f, base + 6f);
        pulseAnimator.setDuration(900);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(a -> {
            animatedRadius = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw full dim layer
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        // Punch spotlight hole
        if (spotlightRect != null) {
            float r = pulseAnimator != null ? animatedRadius : spotlightRadius;
            if (useCircle) {
                float cx = spotlightRect.centerX();
                float cy = spotlightRect.centerY();
                canvas.drawCircle(cx, cy, r, clearPaint);
                
                // Draw inner solid ring
                canvas.drawCircle(cx, cy, r, ringPaint);
                
                // Draw outer pulsing ring (fades out as it expands)
                if (pulseAnimator != null) {
                    float pulseOffset = dpToPx(8);
                    float outerRadius = r + pulseOffset;
                    float progress = (r - (spotlightRadius - 6f)) / 12f; // 0.0 to 1.0
                    int alpha = (int) ((1.0f - progress) * 128);
                    pulseRingPaint.setAlpha(Math.max(0, Math.min(128, alpha)));
                    canvas.drawCircle(cx, cy, outerRadius, pulseRingPaint);
                }
            } else {
                canvas.drawRoundRect(spotlightRect, r, r, clearPaint);
                
                // Draw inner solid round rect
                canvas.drawRoundRect(spotlightRect, r, r, ringPaint);
                
                // Draw outer pulsing round rect
                if (pulseAnimator != null) {
                    float pulseOffset = dpToPx(8);
                    RectF outerRect = new RectF(
                            spotlightRect.left - pulseOffset,
                            spotlightRect.top - pulseOffset,
                            spotlightRect.right + pulseOffset,
                            spotlightRect.bottom + pulseOffset
                    );
                    float progress = (r - (spotlightRadius - 6f)) / 12f; // 0.0 to 1.0
                    int alpha = (int) ((1.0f - progress) * 128);
                    pulseRingPaint.setAlpha(Math.max(0, Math.min(128, alpha)));
                    canvas.drawRoundRect(outerRect, r + pulseOffset, r + pulseOffset, pulseRingPaint);
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPulse();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (spotlightRect != null) {
            float x = event.getX();
            float y = event.getY();
            boolean inside = false;
            if (useCircle) {
                float cx = spotlightRect.centerX();
                float cy = spotlightRect.centerY();
                float r = pulseAnimator != null ? animatedRadius : spotlightRadius;
                float distance = (float) Math.sqrt(Math.pow(x - cx, 2) + Math.pow(y - cy, 2));
                if (distance <= r) {
                    inside = true;
                }
            } else {
                if (spotlightRect.contains(x, y)) {
                    inside = true;
                }
            }
            if (inside) {
                return false;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Converts a target view's position to coordinates local to this overlay view.
     *
     * @param target  The view to spotlight.
     * @param padding Extra padding (dp) around the target.
     * @return RectF in this view's coordinate space.
     */
    public RectF locateView(View target, int paddingPx) {
        int[] targetLoc = new int[2];
        target.getLocationOnScreen(targetLoc);

        int[] overlayLoc = new int[2];
        getLocationOnScreen(overlayLoc);

        float left = targetLoc[0] - overlayLoc[0] - paddingPx;
        float top = targetLoc[1] - overlayLoc[1] - paddingPx;
        float right = left + target.getWidth() + paddingPx * 2;
        float bottom = top + target.getHeight() + paddingPx * 2;
        return new RectF(left, top, right, bottom);
    }
}
