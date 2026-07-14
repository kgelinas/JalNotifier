package io.github.kgelinas.jalfnotifier.util;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton orchestrator for the first-time in-app onboarding tour.
 */
public class AppTourManager {

    private static final String PREFS_TOUR_DONE = "TOUR_DONE_V1";
    private static final String PREFS_NAME = ApiConstants.PREFS_NAME;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile AppTourManager instance;

    public static AppTourManager getInstance() {
        if (instance == null) {
            synchronized (AppTourManager.class) {
                if (instance == null) {
                    instance = new AppTourManager();
                }
            }
        }
        return instance;
    }

    private AppTourManager() {}

    // ── State ─────────────────────────────────────────────────────────────────

    private Activity currentActivity;
    private TourOverlayView overlayView;
    private View tooltipRoot;
    private int currentStep = 0;
    private final List<TourStep> steps = new ArrayList<>();
    private boolean isTourRunning = false;

    // ── Public API ────────────────────────────────────────────────────────────

    public void startIfNeeded(@NonNull Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREFS_TOUR_DONE, false)) return;
        start(activity);
    }

    public void start(@NonNull Activity activity) {
        this.currentActivity = activity;
        this.currentStep = 0;
        this.isTourRunning = true;
        buildSteps(activity);
        attachOverlay(activity);
        showStep(0);
    }

    public static void completeTour(@NonNull Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREFS_TOUR_DONE, true)
                .apply();
    }

    public static void resetTour(@NonNull Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREFS_TOUR_DONE, false)
                .apply();
    }

    public static boolean isTourDone(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREFS_TOUR_DONE, false);
    }

    public boolean isTourActive() {
        return isTourRunning && currentActivity != null && currentStep < steps.size();
    }

    public void continueTour(@NonNull Activity activity) {
        this.currentActivity = activity;
        // Detach old overlay elements from previous activity
        if (overlayView != null && overlayView.getParent() != null) {
            ((ViewGroup) overlayView.getParent()).removeView(overlayView);
        }
        if (tooltipRoot != null && tooltipRoot.getParent() != null) {
            ((ViewGroup) tooltipRoot.getParent()).removeView(tooltipRoot);
        }
        overlayView = null;
        tooltipRoot = null;

        showStep(currentStep);
    }

    public void onProfileSheetShown() {
        if (currentActivity == null) return;
        if (currentStep < steps.size()) {
            TourStep step = steps.get(currentStep);
            if (step.inBottomSheet) {
                // Post a delay to wait for the bottom sheet slide-up animation to complete and settle coordinates
                View decorView = currentActivity.getWindow().getDecorView();
                decorView.postDelayed(() -> {
                    if (currentActivity != null && !currentActivity.isFinishing()) {
                        currentActivity.runOnUiThread(() -> showStep(currentStep));
                    }
                }, 400);
            }
        }
    }

    // ── Step Definitions ──────────────────────────────────────────────────────

    private void buildSteps(@NonNull Activity a) {
        steps.clear();

        boolean isTablet = a.getResources().getConfiguration().smallestScreenWidthDp >= 600;
        int navBarId = isTablet ? R.id.navigation_rail : R.id.bottom_navigation;
        int avatarId = isTablet ? R.id.nav_rail_avatar : R.id.toolbar_avatar;
        String step1Body = isTablet 
                ? a.getString(R.string.tour_step1_body_tablet) 
                : a.getString(R.string.tour_step1_body);

        // Step 0 — Persistent notification explanation (informational, no spotlight)
        steps.add(new TourStep(
                a.getString(R.string.tour_notif_title),
                a.getString(R.string.tour_notif_body),
                View.NO_ID,
                SpotlightShape.NONE,
                TooltipPosition.CENTER
        ));

        // Step 1 — Bottom navigation / Navigation Rail (Welcome)
        steps.add(new TourStep(
                a.getString(R.string.tour_step1_title),
                step1Body,
                navBarId,
                SpotlightShape.RECT,
                TooltipPosition.ABOVE
        ));

        // Step 2 — Chat nav item (Messages & Chats)
        steps.add(new TourStep(
                a.getString(R.string.tour_chat_title),
                a.getString(R.string.tour_chat_body),
                R.id.nav_chats,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE
        ));

        // Step 3 — Events nav item (Events & Visits)
        steps.add(new TourStep(
                a.getString(R.string.tour_step3_title),
                a.getString(R.string.tour_step3_body),
                R.id.nav_events,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE
        ));

        // Step 4 — Search nav item (Unified Search)
        steps.add(new TourStep(
                a.getString(R.string.tour_search_title),
                a.getString(R.string.tour_search_body),
                R.id.nav_search,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE
        ));

        // Step 5 — Favorites/Contacts nav item (Contacts & Bookmarks)
        steps.add(new TourStep(
                a.getString(R.string.tour_contacts_title),
                a.getString(R.string.tour_contacts_body),
                R.id.nav_favorites,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE
        ));

        // Step 6 — Toolbar avatar / Nav Rail avatar (Your Profile - interactive)
        steps.add(new TourStep(
                a.getString(R.string.tour_step2_title),
                a.getString(R.string.tour_step2_body),
                avatarId,
                SpotlightShape.CIRCLE,
                TooltipPosition.BELOW,
                false, // inBottomSheet
                true   // isInteractive
        ));

        // Ghost Mode (Appear Offline)
        steps.add(new TourStep(
                a.getString(R.string.tour_ghost_title),
                a.getString(R.string.tour_ghost_body),
                R.id.btn_profile_appear_offline,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE,
                true,  // inBottomSheet
                false  // isInteractive
        ));

        // Read Receipts
        steps.add(new TourStep(
                a.getString(R.string.tour_receipts_title),
                a.getString(R.string.tour_receipts_body),
                R.id.btn_profile_read_receipts,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE,
                true,  // inBottomSheet
                false  // isInteractive
        ));

        // NSFW Blur
        steps.add(new TourStep(
                a.getString(R.string.tour_nsfw_title),
                a.getString(R.string.tour_nsfw_body),
                R.id.btn_profile_nsfw,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE,
                true,  // inBottomSheet
                false  // isInteractive
        ));

        // Geolocation
        steps.add(new TourStep(
                a.getString(R.string.tour_geo_title),
                a.getString(R.string.tour_geo_body),
                R.id.btn_profile_geolocation,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE,
                true,  // inBottomSheet
                false  // isInteractive
        ));

        // Clean up chats
        steps.add(new TourStep(
                a.getString(R.string.tour_cleanup_title),
                a.getString(R.string.tour_cleanup_body),
                R.id.btn_profile_cleanup,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE,
                true,  // inBottomSheet
                false  // isInteractive
        ));

        // Logout
        steps.add(new TourStep(
                a.getString(R.string.tour_logout_title),
                a.getString(R.string.tour_logout_body),
                R.id.btn_profile_logout,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE,
                true,  // inBottomSheet
                false  // isInteractive
        ));

        // Step 7 — Settings in Bottom Sheet (Profile Sheet - interactive)
        steps.add(new TourStep(
                a.getString(R.string.tour_step4_title),
                a.getString(R.string.tour_step4_body),
                R.id.btn_profile_settings,
                SpotlightShape.CIRCLE,
                TooltipPosition.ABOVE,
                true,  // inBottomSheet
                true   // isInteractive
        ));

        // Step 8 — Informational (Filter by Sex & Contact Type)
        steps.add(new TourStep(
                a.getString(R.string.tour_step5_title),
                a.getString(R.string.tour_step5_body),
                View.NO_ID,
                SpotlightShape.NONE,
                TooltipPosition.CENTER
        ));

        // Step 9 — Auto Ghost Mode
        steps.add(new TourStep(
                a.getString(R.string.tour_step6_title),
                a.getString(R.string.tour_step6_body),
                View.NO_ID,
                SpotlightShape.NONE,
                TooltipPosition.CENTER
        ));

        // Step 10 — Quick Responses
        steps.add(new TourStep(
                a.getString(R.string.tour_step7_title),
                a.getString(R.string.tour_step7_body),
                View.NO_ID,
                SpotlightShape.NONE,
                TooltipPosition.CENTER
        ));
    }

    // ── Overlay Management ────────────────────────────────────────────────────

    private void attachOverlay(@NonNull Activity activity) {
        detachOverlay();
    }

    private void updateOverlayParent(TourStep step) {
        ViewGroup targetParent = getTargetParentViewGroup(step);

        if (overlayView != null && overlayView.getParent() == targetParent 
                && tooltipRoot != null && tooltipRoot.getParent() == targetParent) {
            return;
        }

        if (overlayView != null && overlayView.getParent() != null) {
            ((ViewGroup) overlayView.getParent()).removeView(overlayView);
        }
        if (tooltipRoot != null && tooltipRoot.getParent() != null) {
            ((ViewGroup) tooltipRoot.getParent()).removeView(tooltipRoot);
        }

        if (overlayView == null) {
            overlayView = new TourOverlayView(currentActivity);
            overlayView.setClickable(true);
        }
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        targetParent.addView(overlayView, overlayParams);

        if (tooltipRoot == null) {
            tooltipRoot = LayoutInflater.from(currentActivity)
                    .inflate(R.layout.layout_tour_tooltip, targetParent, false);
            setupTooltipButtons();
        }
        
        targetParent.addView(tooltipRoot);

        overlayView.setAlpha(1f);
        tooltipRoot.setAlpha(1f);
    }

    private void setupTooltipButtons() {
        if (tooltipRoot == null) return;
        MaterialButton btnNext = tooltipRoot.findViewById(R.id.btn_tour_next);
        MaterialButton btnBack = tooltipRoot.findViewById(R.id.btn_tour_back);
        MaterialButton btnSkip = tooltipRoot.findViewById(R.id.btn_tour_skip);

        btnNext.setOnClickListener(v -> {
            if (currentStep < steps.size() - 1) {
                advance(1);
            } else {
                finish();
            }
        });

        btnBack.setOnClickListener(v -> advance(-1));
        btnSkip.setOnClickListener(v -> finish());
    }

    private void detachOverlay() {
        restoreOriginalListeners();
        if (overlayView != null && overlayView.getParent() != null) {
            ((ViewGroup) overlayView.getParent()).removeView(overlayView);
        }
        overlayView = null;
        if (tooltipRoot != null && tooltipRoot.getParent() != null) {
            ((ViewGroup) tooltipRoot.getParent()).removeView(tooltipRoot);
        }
        tooltipRoot = null;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void advance(int delta) {
        currentStep += delta;
        currentStep = Math.max(0, Math.min(currentStep, steps.size() - 1));
        showStep(currentStep);
    }

    private void showStep(int index) {
        if (currentActivity == null) return;
        TourStep step = steps.get(index);

        updateOverlayParent(step);

        if (overlayView == null || tooltipRoot == null) return;

        TextView tvTitle = tooltipRoot.findViewById(R.id.tv_tour_title);
        TextView tvBody = tooltipRoot.findViewById(R.id.tv_tour_body);
        TextView tvStep = tooltipRoot.findViewById(R.id.tv_tour_step);
        MaterialButton btnNext = tooltipRoot.findViewById(R.id.btn_tour_next);
        MaterialButton btnBack = tooltipRoot.findViewById(R.id.btn_tour_back);
        LinearLayout dotsContainer = tooltipRoot.findViewById(R.id.tour_dots_container);

        tvTitle.setText(step.title);
        tvBody.setText(step.body);
        tvStep.setText(currentActivity.getString(R.string.tour_step_counter, index + 1, steps.size()));

        btnBack.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
        boolean isLast = (index == steps.size() - 1);
        btnNext.setText(isLast
                ? currentActivity.getString(R.string.tour_done)
                : currentActivity.getString(R.string.tour_next));

        btnNext.setVisibility(step.isInteractive ? View.GONE : View.VISIBLE);

        buildDots(dotsContainer, index, steps.size());
        repositionTooltip(step);
        applySpotlight(step);

        tooltipRoot.setTranslationY(20f);
        tooltipRoot.animate()
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        restoreOriginalListeners();

        if (step.isInteractive) {
            View target = findTargetView(step);
            if (target != null) {
                target.setOnClickListener(v -> {
                    restoreOriginalListeners();
                    if ((step.targetViewId == R.id.toolbar_avatar || step.targetViewId == R.id.nav_rail_avatar) && currentActivity instanceof MainActivity) {
                        ((MainActivity) currentActivity).showProfileSheet();
                    } else if (step.targetViewId == R.id.btn_profile_settings && currentActivity instanceof MainActivity) {
                        // Dismiss the bottom sheet and launch SettingsActivity
                        BottomSheetDialog dialog = ((MainActivity) currentActivity).getCurrentProfileSheet();
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                        currentActivity.startActivity(new android.content.Intent(currentActivity, SettingsActivity.class));
                    } else {
                        target.performClick();
                    }
                    advance(1);
                });
            }
        }
    }

    private void restoreOriginalListeners() {
        if (currentActivity instanceof MainActivity) {
            final MainActivity mainActivity = (MainActivity) currentActivity;
            View avatar = mainActivity.findViewById(R.id.toolbar_avatar);
            if (avatar != null) {
                avatar.setOnClickListener(v -> mainActivity.showProfileSheet());
            }
            View navRail = mainActivity.findViewById(R.id.navigation_rail);
            if (navRail instanceof com.google.android.material.navigationrail.NavigationRailView) {
                View header = ((com.google.android.material.navigationrail.NavigationRailView) navRail).getHeaderView();
                if (header != null) {
                    View navAvatar = header.findViewById(R.id.nav_rail_avatar);
                    if (navAvatar != null) {
                        navAvatar.setOnClickListener(v -> mainActivity.showProfileSheet());
                    }
                }
            }
            BottomSheetDialog dialog = mainActivity.getCurrentProfileSheet();
            if (dialog != null && dialog.isShowing()) {
                Window window = dialog.getWindow();
                if (window != null) {
                    View btnSettings = window.getDecorView().findViewById(R.id.btn_profile_settings);
                    if (btnSettings != null) {
                        btnSettings.setOnClickListener(v -> {
                            dialog.dismiss();
                            mainActivity.startActivity(new android.content.Intent(mainActivity, SettingsActivity.class));
                        });
                    }
                }
            }
        }
    }

    private void buildDots(LinearLayout container, int activeIndex, int total) {
        container.removeAllViews();
        int dotSize = dpToPx(currentActivity, 8);
        int dotMargin = dpToPx(currentActivity, 4);
        int colorActive = resolveAttrColor(currentActivity, com.google.android.material.R.attr.colorPrimary);
        int colorInactive = resolveAttrColor(currentActivity, com.google.android.material.R.attr.colorOutlineVariant);

        for (int i = 0; i < total; i++) {
            View dot = new View(currentActivity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.leftMargin = dotMargin;
            lp.rightMargin = dotMargin;
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.circle_background);
            dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    i == activeIndex ? colorActive : colorInactive));
            container.addView(dot);
        }
    }

    private void repositionTooltip(TourStep step) {
        if (tooltipRoot == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tooltipRoot.getLayoutParams();

        boolean isTablet = currentActivity.getResources().getConfiguration().smallestScreenWidthDp >= 600;
        if (isTablet) {
            // Limit card width on tablets so it doesn't stretch across the wide screen
            lp.width = dpToPx(currentActivity, 400);
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        if (step.position == TooltipPosition.ABOVE) {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dpToPx(currentActivity, 80);
            lp.bottomMargin = 0;
        } else if (step.position == TooltipPosition.CENTER) {
            lp.gravity = Gravity.CENTER;
            lp.topMargin = 0;
            lp.bottomMargin = 0;
        } else { // BELOW — default bottom
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = 0;
            lp.bottomMargin = dpToPx(currentActivity, 80);
        }
        tooltipRoot.setLayoutParams(lp);
    }

    private void applySpotlight(TourStep step) {
        View target = findTargetView(step);
        if (target == null || step.shape == SpotlightShape.NONE) {
            overlayView.clearSpotlight();
            return;
        }

        if (target.getWidth() == 0 || target.getHeight() == 0) {
            target.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    target.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    applySpotlightOnView(target, step);
                }
            });
        } else {
            applySpotlightOnView(target, step);
        }
    }

    private void applySpotlightOnView(@NonNull View target, @NonNull TourStep step) {
        int padding = dpToPx(currentActivity, 12);
        RectF rect = overlayView.locateView(target, padding);

        if (step.shape == SpotlightShape.CIRCLE) {
            overlayView.setSpotlightCircle(rect);
        } else {
            float cornerR = dpToPx(currentActivity, 16);
            overlayView.setSpotlightRect(rect, cornerR);
        }
        overlayView.startPulse();
    }

    private ViewGroup getTargetParentViewGroup(TourStep step) {
        if (step.inBottomSheet && currentActivity instanceof MainActivity) {
            BottomSheetDialog dialog = ((MainActivity) currentActivity).getCurrentProfileSheet();
            if (dialog != null && dialog.isShowing()) {
                Window window = dialog.getWindow();
                if (window != null) {
                    return (ViewGroup) window.getDecorView();
                }
            }
        }
        return (ViewGroup) currentActivity.getWindow().getDecorView();
    }

    private View findTargetView(TourStep step) {
        if (step.targetViewId == View.NO_ID) return null;
        if (step.inBottomSheet && currentActivity instanceof MainActivity) {
            BottomSheetDialog dialog = ((MainActivity) currentActivity).getCurrentProfileSheet();
            if (dialog != null && dialog.isShowing()) {
                Window window = dialog.getWindow();
                if (window != null) {
                    return window.getDecorView().findViewById(step.targetViewId);
                }
            }
        }
        View v = currentActivity.findViewById(step.targetViewId);
        if (v == null && currentActivity instanceof MainActivity) {
            View navRail = currentActivity.findViewById(R.id.navigation_rail);
            if (navRail instanceof com.google.android.material.navigationrail.NavigationRailView) {
                View header = ((com.google.android.material.navigationrail.NavigationRailView) navRail).getHeaderView();
                if (header != null) {
                    v = header.findViewById(step.targetViewId);
                }
            }
        }
        return v;
    }

    private void finish() {
        this.isTourRunning = false;
        if (overlayView != null) {
            overlayView.animate().alpha(0f).setDuration(250).withEndAction(this::detachOverlay).start();
        }
        if (tooltipRoot != null) {
            tooltipRoot.animate().alpha(0f).setDuration(250).start();
        }
        if (currentActivity != null) {
            completeTour(currentActivity);
        }
    }

    // ── Models ────────────────────────────────────────────────────────────────

    private enum SpotlightShape { CIRCLE, RECT, NONE }
    private enum TooltipPosition { ABOVE, BELOW, CENTER }

    private static class TourStep {
        final String title;
        final String body;
        final int targetViewId;
        final SpotlightShape shape;
        final TooltipPosition position;
        final boolean inBottomSheet;
        final boolean isInteractive;

        TourStep(String title, String body, int targetViewId, SpotlightShape shape, TooltipPosition position) {
            this(title, body, targetViewId, shape, position, false, false);
        }

        TourStep(String title, String body, int targetViewId, SpotlightShape shape, TooltipPosition position, boolean inBottomSheet, boolean isInteractive) {
            this.title = title;
            this.body = body;
            this.targetViewId = targetViewId;
            this.shape = shape;
            this.position = position;
            this.inBottomSheet = inBottomSheet;
            this.isInteractive = isInteractive;
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private static int dpToPx(@NonNull Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static int resolveAttrColor(@NonNull Context ctx, int attrRes) {
        android.util.TypedValue tv = new android.util.TypedValue();
        ctx.getTheme().resolveAttribute(attrRes, tv, true);
        return tv.data;
    }
}
