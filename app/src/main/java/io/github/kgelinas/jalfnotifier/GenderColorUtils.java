package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;

public class GenderColorUtils {

    public static int resolveThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    public static void applyGenderTint(Context context, MaterialCardView cardView, View leftSection, View rightSection, String sexIconUrl) {
        if (context == null || cardView == null) return;

        Integer tintColor = null;
        if (sexIconUrl != null && !sexIconUrl.isEmpty()) {
            if (sexIconUrl.contains("_sex_1_") || sexIconUrl.endsWith("/1")) {
                tintColor = ContextCompat.getColor(context, R.color.gender_male);
            } else if (sexIconUrl.contains("_sex_2_") || sexIconUrl.endsWith("/2")) {
                tintColor = ContextCompat.getColor(context, R.color.gender_female);
            } else if (sexIconUrl.contains("_sex_4_") || sexIconUrl.endsWith("/4")) {
                tintColor = ContextCompat.getColor(context, R.color.gender_couple);
            } else if (sexIconUrl.contains("_sex_64_") || sexIconUrl.endsWith("/64")) {
                tintColor = ContextCompat.getColor(context, R.color.gender_couple_male);
            } else if (sexIconUrl.contains("_sex_32_") || sexIconUrl.endsWith("/32")) {
                tintColor = ContextCompat.getColor(context, R.color.gender_couple_female);
            } else if (sexIconUrl.contains("_sex_16_") || sexIconUrl.endsWith("/16") ||
                       sexIconUrl.contains("_sex_8_") || sexIconUrl.endsWith("/8")) {
                tintColor = ContextCompat.getColor(context, R.color.gender_trans);
            }
        }

        if (tintColor != null) {
            if (leftSection != null) {
                leftSection.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            if (rightSection != null) {
                rightSection.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
            cardView.setCardBackgroundColor(ColorStateList.valueOf(tintColor));
        } else {
            // Restore default colors
            int defaultSurface = resolveThemeColor(context, com.google.android.material.R.attr.colorSurface);
            int defaultSurfaceVariant = resolveThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant);
            
            if (leftSection != null) {
                leftSection.setBackgroundColor(defaultSurfaceVariant);
            }
            if (rightSection != null) {
                rightSection.setBackgroundColor(defaultSurface);
            }
            cardView.setCardBackgroundColor(ColorStateList.valueOf(defaultSurface));
        }
    }
}
