package io.github.kgelinas.jalfnotifier.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView supporting pinch-to-zoom, panning while zoomed and double-tap to
 * toggle zoom. Works by scaling/translating the view itself (clipped by its
 * parent), so any scaleType (e.g. centerCrop) keeps working.
 * Single taps are forwarded to the parent's click listener.
 */
public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float scale = 1f;

    public ZoomableImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector d) {
                float newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * d.getScaleFactor()));
                float factor = newScale / scale;
                // Zoom around the focal point
                float fx = d.getFocusX() - getWidth() / 2f;
                float fy = d.getFocusY() - getHeight() / 2f;
                setTranslationX(getTranslationX() - (fx - getTranslationX()) * (factor - 1));
                setTranslationY(getTranslationY() - (fy - getTranslationY()) * (factor - 1));
                applyScale(newScale);
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                ViewParent p = getParent();
                if (p instanceof View) ((View) p).performClick();
                else performClick();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (scale > MIN_SCALE) {
                    resetZoom();
                } else {
                    float fx = e.getX() - getWidth() / 2f;
                    float fy = e.getY() - getHeight() / 2f;
                    setTranslationX(-fx * (DOUBLE_TAP_SCALE - 1));
                    setTranslationY(-fy * (DOUBLE_TAP_SCALE - 1));
                    applyScale(DOUBLE_TAP_SCALE);
                }
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                if (scale <= MIN_SCALE) return false;
                setTranslationX(getTranslationX() - dx);
                setTranslationY(getTranslationY() - dy);
                clampTranslation();
                return true;
            }
        });
    }

    private void applyScale(float s) {
        scale = s;
        setScaleX(s);
        setScaleY(s);
        if (s <= MIN_SCALE) {
            setTranslationX(0);
            setTranslationY(0);
        }
        clampTranslation();
    }

    private void clampTranslation() {
        float maxX = getWidth() * (scale - 1) / 2f;
        float maxY = getHeight() * (scale - 1) / 2f;
        setTranslationX(Math.max(-maxX, Math.min(maxX, getTranslationX())));
        setTranslationY(Math.max(-maxY, Math.min(maxY, getTranslationY())));
    }

    public void resetZoom() {
        applyScale(MIN_SCALE);
    }

    public boolean isZoomed() {
        return scale > MIN_SCALE;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Events arrive in the view's (scaled) local coords; convert to raw-ish parent space
        MotionEvent ev = MotionEvent.obtain(event);
        ev.transform(getMatrix());
        scaleDetector.onTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);
        ev.recycle();

        // While pinching or zoomed, stop the ViewPager/AppBar from stealing the gesture
        boolean block = event.getPointerCount() > 1 || scale > MIN_SCALE;
        ViewParent p = getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(block);
        return true;
    }
}
