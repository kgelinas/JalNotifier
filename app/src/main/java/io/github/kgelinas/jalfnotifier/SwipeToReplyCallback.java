package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Handle Swipe-to-Reply gesture for triggering AI contextual responses.
 */
public class SwipeToReplyCallback extends ItemTouchHelper.SimpleCallback {

    public interface SwipeReplyListener {
        void onSwipeReply(int position);
    }

    private final SwipeReplyListener listener;
    private final Drawable sparkleIcon;
    private final int iconMargin;
    private final int backgroundColor;

    public SwipeToReplyCallback(Context context, SwipeReplyListener listener) {
        super(0, ItemTouchHelper.RIGHT); // Pull right to reveal reply on left
        this.listener = listener;
        this.sparkleIcon = ContextCompat.getDrawable(context, R.drawable.ic_sparkle_24);
        this.iconMargin = (int) (24 * context.getResources().getDisplayMetrics().density);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true);
        this.backgroundColor = typedValue.data;

        if (sparkleIcon != null) {
            sparkleIcon.setTint(backgroundColor);
        }
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        if (listener != null) {
            listener.onSwipeReply(viewHolder.getAdapterPosition());
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
            boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            View itemView = viewHolder.itemView;

            // Draw sparkly icon if swiping right
            if (dX > 0 && sparkleIcon != null) {
                int itemHeight = itemView.getBottom() - itemView.getTop();
                int iconIntrinsicHeight = sparkleIcon.getIntrinsicHeight();
                int iconIntrinsicWidth = sparkleIcon.getIntrinsicWidth();

                int iconTop = itemView.getTop() + (itemHeight - iconIntrinsicHeight) / 2;
                int iconBottom = iconTop + iconIntrinsicHeight;

                int iconLeft = itemView.getLeft() + iconMargin;
                int iconRight = iconLeft + iconIntrinsicWidth;

                // Scale/Fade the icon based on swipe distance
                float progress = Math.min(1.0f, dX / (itemView.getWidth() / 4f));
                sparkleIcon.setAlpha((int) (progress * 255));

                sparkleIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                sparkleIcon.draw(c);
            }

            // Limit the swipe visual so items don't fly off screen
            float translationX = Math.min(dX, itemView.getWidth() / 3f);
            super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive);
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.5f;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 10; // Prevent accidental escape
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        android.content.SharedPreferences prefs = recyclerView.getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String provider = prefs.getString(ApiConstants.KEY_AI_PROVIDER, "Google Gemini").trim();
        String savedToken = prefs.getString(ApiConstants.KEY_AI_TOKEN, "").trim();
        String model = prefs.getString(ApiConstants.KEY_GEMINI_MODEL, "").trim();

        boolean needsToken = "Google Gemini".equals(provider) || "OpenRouter".equals(provider);
        boolean isConfigured;
        if (!needsToken) {
            isConfigured = !model.isEmpty();
        } else {
            isConfigured = !savedToken.isEmpty() || !ApiConstants.GEMINI_API_KEY.isEmpty();
        }

        if (!isConfigured) {
            return 0; // Disable swipe-to-reply completely
        }
        return super.getSwipeDirs(recyclerView, viewHolder);
    }
}
