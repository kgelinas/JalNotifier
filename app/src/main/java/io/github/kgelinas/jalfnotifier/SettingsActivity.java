package io.github.kgelinas.jalfnotifier;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import io.github.kgelinas.jalfnotifier.databinding.ActivitySettingsBinding;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;
    private int aiTapCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets insetsValues = insets
                    .getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(insetsValues.left, insetsValues.top, insetsValues.right, insetsValues.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.settingsToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupObservers();
        setupListeners();

        if (AppTourManager.getInstance().isTourActive()) {
            AppTourManager.getInstance().continueTour(this);
        }
    }

    private void setupObservers() {
        viewModel.quickResponses.observe(this, this::populateQuickResponses);
        viewModel.useImperial.observe(this, binding.switchUseImperial::setChecked);
        viewModel.sendReadReceipts.observe(this, binding.switchReadReceipt::setChecked);
        viewModel.blurNsfw.observe(this, binding.switchBlurNsfw::setChecked);
        viewModel.remoteGeolocation.observe(this, binding.switchGeolocation::setChecked);
    }

    private void setupListeners() {
        binding.btnAddQuickResponse.setOnClickListener(v -> showQuickResponseDialog(null, null, null));
        binding.switchUseImperial.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setUseImperial(isChecked));
        binding.switchReadReceipt
                .setOnCheckedChangeListener((btn, isChecked) -> viewModel.setSendReadReceipts(isChecked));
        binding.switchBlurNsfw.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setBlurNsfw(isChecked));
        binding.switchGeolocation.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setRemoteGeolocation(isChecked));

        binding.navNotifications
                .setOnClickListener(v -> startActivity(new Intent(this, SettingsNotificationActivity.class)));
        binding.navIntelligence
                .setOnClickListener(v -> startActivity(new Intent(this, SettingsIntelligenceActivity.class)));
        binding.navWidget
                .setOnClickListener(v -> startActivity(new Intent(this, SettingsWidgetActivity.class)));

        // Show/hide AI settings based on unlock state
        boolean isAiUnlocked = AppPrefs.getInstance(this).getBoolean(ApiConstants.KEY_AI_UNLOCKED, false);
        binding.navIntelligence.setVisibility(isAiUnlocked ? View.VISIBLE : View.GONE);

        try {
            String currentVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            binding.tvSettingsVersion.setText("JALNotifier v" + currentVer);
        } catch (Exception ignored) {}

        binding.tvSettingsVersion.setOnClickListener(v -> {
            boolean unlocked = AppPrefs.getInstance(this).getBoolean(ApiConstants.KEY_AI_UNLOCKED, false);
            if (unlocked) {
                android.widget.Toast.makeText(this, R.string.settings_ai_already_unlocked, android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            aiTapCount++;
            if (aiTapCount >= 7) {
                AppPrefs.getInstance(this).putBoolean(ApiConstants.KEY_AI_UNLOCKED, true);
                android.widget.Toast.makeText(this, R.string.settings_ai_unlocked, android.widget.Toast.LENGTH_SHORT).show();
                binding.navIntelligence.setVisibility(View.VISIBLE);
                // Scroll back to top smoothly so the user sees the newly unlocked setting
                binding.settingsScrollView.smoothScrollTo(0, 0);
            } else if (aiTapCount >= 4) {
                int stepsRemaining = 7 - aiTapCount;
                String msg = getString(R.string.settings_ai_unlock_steps, stepsRemaining);
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        binding.navCloud.setOnClickListener(v -> startActivity(new Intent(this, SettingsCloudActivity.class)));
        binding.navSecurity.setOnClickListener(v -> startActivity(new Intent(this, SettingsSecurityActivity.class)));
        if (binding.navUpdates != null) {
            binding.navUpdates.setOnClickListener(v -> UpdateManager.checkForUpdates(this, true));
        }

        if (binding.navResetTour != null) {
            binding.navResetTour.setOnClickListener(v -> {
                AppTourManager.resetTour(this);
                android.widget.Toast.makeText(this, R.string.tour_reset_done,
                        android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        if (binding.navLanguage != null) {
            binding.navLanguage.setOnClickListener(v -> {
                if (android.os.Build.VERSION.SDK_INT >= 33) { // TIRAMISU
                    Intent intent = new Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS);
                    intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                } else {
                    startActivity(new Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS));
                }
            });
        }
    }

    private void populateQuickResponses(List<SettingsViewModel.QuickResponse> responses) {
        binding.settingsQuickResponsesContainer.removeAllViews();
        if (responses.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.settings_no_quick_responses);

            tv.setAlpha(0.6f);
            binding.settingsQuickResponsesContainer.addView(tv);
            return;
        }

        for (SettingsViewModel.QuickResponse qr : responses) {
            addQuickResponseRow(qr);
        }
    }

    private void addQuickResponseRow(SettingsViewModel.QuickResponse qr) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        row.setClickable(true);
        row.setFocusable(true);

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);
        row.setOnClickListener(v -> showQuickResponseDialog(qr.index, qr.label, qr.message));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(qr.label);
        TextViewCompat.setTextAppearance(tvLabel, android.R.style.TextAppearance_Material_Body1);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView btnDelete = new ImageView(this);
        btnDelete.setImageResource(android.R.drawable.ic_menu_delete);
        btnDelete.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        TypedValue colorError = new TypedValue();
        if (getTheme().resolveAttribute(com.google.android.material.R.attr.colorError, colorError, true)) {
            btnDelete.setColorFilter(colorError.data);
        }
        btnDelete.setOnClickListener(v -> viewModel.deleteQuickResponse(qr.index));
        header.addView(btnDelete);

        row.addView(header);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(qr.message);
        TextViewCompat.setTextAppearance(tvMsg, android.R.style.TextAppearance_Material_Body2);
        tvMsg.setAlpha(0.7f);
        tvMsg.setPadding(0, dpToPx(4), 0, dpToPx(8));
        row.addView(tvMsg);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        TypedValue outlineVariant = new TypedValue();
        if (getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, outlineVariant, true)) {
            divider.setBackgroundColor(outlineVariant.data);
        }
        divider.setAlpha(0.5f);
        row.addView(divider);

        binding.settingsQuickResponsesContainer.addView(row);
    }

    private void showQuickResponseDialog(Integer index, String currentLabel, String currentMsg) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = dpToPx(20);
        layout.setPadding(p, dpToPx(8), p, 0);

        EditText editLabel = new EditText(this);
        editLabel.setHint(R.string.settings_qr_label_hint);

        if (currentLabel != null)
            editLabel.setText(currentLabel);
        layout.addView(editLabel);

        EditText editMsg = new EditText(this);
        editMsg.setHint(R.string.settings_qr_msg_hint);

        if (currentMsg != null)
            editMsg.setText(currentMsg);
        layout.addView(editMsg);

        int titleResId = (index == null) ? R.string.settings_add_qr_title : R.string.settings_edit_qr_title;
        int btnTextResId = (index == null) ? R.string.add : R.string.save;

        new AlertDialog.Builder(this)
                .setTitle(titleResId)
                .setView(layout)
                .setPositiveButton(btnTextResId, (dialog, which) -> {

                    String label = editLabel.getText().toString().trim();
                    String msg = editMsg.getText().toString().trim();
                    if (!label.isEmpty() && !msg.isEmpty()) {
                        viewModel.saveQuickResponse(index, label, msg);
                    }
                })
                .setNegativeButton(R.string.cancel, null)

                .show();
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
