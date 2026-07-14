package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FantasyPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnFantasiesSelectedListener {
        void onFantasiesSelected(List<Integer> selectedIds);
    }

    private final List<MainActivity.FantasyCategory> categories;
    private final Set<Integer> selectedIds;
    private final OnFantasiesSelectedListener listener;

    public FantasyPickerBottomSheet(List<MainActivity.FantasyCategory> categories,
            List<Integer> initialSelectedIds,
            OnFantasiesSelectedListener listener) {
        this.categories = categories;
        this.selectedIds = new HashSet<>(initialSelectedIds);
        this.listener = listener;
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        BottomSheetUtils.setupFullHeight(dialog);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_fantasy_picker, container, false);

        LinearLayout containerLayout = view.findViewById(R.id.ll_fantasies_container);

        for (MainActivity.FantasyCategory cat : categories) {
            // Category Header
            TextView header = new TextView(getContext());
            header.setText(cat.name);
            header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            header.setPadding(0, 32, 0, 16);
            containerLayout.addView(header);

            // Fantasy Chips
            ChipGroup chipGroup = new ChipGroup(getContext());
            chipGroup.setChipSpacingVertical(4);
            for (MainActivity.Fantasy fantasy : cat.fantasies) {
                Chip chip = new Chip(getContext());
                chip.setText(fantasy.name);
                chip.setCheckable(true);
                chip.setChecked(selectedIds.contains(fantasy.id));
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedIds.add(fantasy.id);
                    } else {
                        selectedIds.remove(fantasy.id);
                    }
                });
                chipGroup.addView(chip);
            }
            containerLayout.addView(chipGroup);
        }

        view.findViewById(R.id.btn_apply_fantasies).setOnClickListener(v -> {
            if (listener != null) {
                listener.onFantasiesSelected(new ArrayList<>(selectedIds));
            }
            dismiss();
        });

        return view;
    }
}
