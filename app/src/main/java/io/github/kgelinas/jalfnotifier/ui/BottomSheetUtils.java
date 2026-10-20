package io.github.kgelinas.jalfnotifier.ui;

import android.content.DialogInterface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class BottomSheetUtils {
    public static void setupFullHeight(@NonNull BottomSheetDialog bottomSheetDialog) {
        try {
            BottomSheetBehavior<?> behavior = bottomSheetDialog.getBehavior();
            if (behavior != null) {
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        } catch (Exception ignored) {}

        bottomSheetDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                expand(bottomSheetDialog);
            }
        });
    }

    public static void expand(@Nullable BottomSheetDialog bottomSheetDialog) {
        if (bottomSheetDialog == null) return;
        try {
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                BottomSheetBehavior<?> behavior = bottomSheetDialog.getBehavior();
                if (behavior != null) {
                    behavior.setSkipCollapsed(true);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }
            if (!bottomSheetDialog.isShowing()) {
                bottomSheetDialog.show();
            }
        } catch (Exception ignored) {}
    }
}
