package io.github.kgelinas.jalfnotifier;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.checkbox.MaterialCheckBox;

import io.github.kgelinas.jalfnotifier.databinding.ActivitySettingsNotificationBinding;

import java.util.Map;
import java.util.Set;

public class SettingsNotificationActivity extends AppCompatActivity {

    private ActivitySettingsNotificationBinding binding;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsNotificationBinding.inflate(getLayoutInflater());
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

        viewModel.sexesMetadata.observe(this, meta -> {
            if (meta != null && binding.settingsSexesContainer.getChildCount() == 0)
                populateSexes(meta);
        });

        populateContactTypes();

        if (AppTourManager.getInstance().isTourActive()) {
            AppTourManager.getInstance().continueTour(this);
        }
    }

    private void populateContactTypes() {
        binding.settingsContactTypesContainer.removeAllViews();

        String[] types = {"contacts", "bookmarks", "notified"};
        int[] titles = {
            R.string.filter_contact_type_contacts,
            R.string.filter_contact_type_bookmarks,
            R.string.filter_contact_type_notified
        };
        int[] icons = {
            R.drawable.ic_star_filled,
            R.drawable.ic_bookmark,
            R.drawable.ic_notifications_active
        };

        for (int i = 0; i < types.length; i++) {
            final String typeKey = types[i];
            String titleStr = getString(titles[i]);
            int iconRes = icons[i];

            View cardView = getLayoutInflater().inflate(R.layout.item_settings_notification_sex, binding.settingsContactTypesContainer, false);

            ImageView typeIcon = cardView.findViewById(R.id.sex_icon);
            TextView typeName = cardView.findViewById(R.id.sex_name);
            ImageView expandArrow = cardView.findViewById(R.id.expand_arrow);
            LinearLayout headerLayout = cardView.findViewById(R.id.header_layout);
            LinearLayout expandContainer = cardView.findViewById(R.id.expand_container);

            com.google.android.material.materialswitch.MaterialSwitch switchMessages = cardView.findViewById(R.id.switch_messages);
            com.google.android.material.materialswitch.MaterialSwitch switchVisits = cardView.findViewById(R.id.switch_visits);
            com.google.android.material.materialswitch.MaterialSwitch switchPokes = cardView.findViewById(R.id.switch_pokes);
            com.google.android.material.materialswitch.MaterialSwitch switchFavorites = cardView.findViewById(R.id.switch_favorites);
            com.google.android.material.materialswitch.MaterialSwitch switchPhotos = cardView.findViewById(R.id.switch_photos);

            typeName.setText(titleStr);
            typeIcon.setImageResource(iconRes);

            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
            int color = typedValue.data;
            typeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(color));

            switchMessages.setChecked(viewModel.getContactTypeCategoryFilter(typeKey, "chat"));
            switchVisits.setChecked(viewModel.getContactTypeCategoryFilter(typeKey, "visit"));
            switchPokes.setChecked(viewModel.getContactTypeCategoryFilter(typeKey, "poke"));
            switchFavorites.setChecked(viewModel.getContactTypeCategoryFilter(typeKey, "favorite"));
            switchPhotos.setChecked(viewModel.getContactTypeCategoryFilter(typeKey, "photo"));

            switchMessages.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setContactTypeCategoryFilter(typeKey, "chat", isChecked));
            switchVisits.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setContactTypeCategoryFilter(typeKey, "visit", isChecked));
            switchPokes.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setContactTypeCategoryFilter(typeKey, "poke", isChecked));
            switchFavorites.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setContactTypeCategoryFilter(typeKey, "favorite", isChecked));
            switchPhotos.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setContactTypeCategoryFilter(typeKey, "photo", isChecked));

            headerLayout.setOnClickListener(v -> {
                boolean isExpanded = expandContainer.getVisibility() == View.VISIBLE;
                if (isExpanded) {
                    expandContainer.setVisibility(View.GONE);
                    expandArrow.animate().rotation(0f).setDuration(200).start();
                } else {
                    expandContainer.setVisibility(View.VISIBLE);
                    expandArrow.animate().rotation(180f).setDuration(200).start();
                }
            });

            binding.settingsContactTypesContainer.addView(cardView);
        }
    }

    private void populateSexes(Map<String, String> sexes) {
        binding.settingsSexesContainer.removeAllViews();
        if (sexes == null) return;

        for (Map.Entry<String, String> entry : sexes.entrySet()) {
            String link = entry.getKey();
            String desc = entry.getValue();
            String iconUrl = MetadataManager.getInstance().resolveIcon(link);

            View cardView = getLayoutInflater().inflate(R.layout.item_settings_notification_sex, binding.settingsSexesContainer, false);

            ImageView sexIcon = cardView.findViewById(R.id.sex_icon);
            TextView sexName = cardView.findViewById(R.id.sex_name);
            ImageView expandArrow = cardView.findViewById(R.id.expand_arrow);
            LinearLayout headerLayout = cardView.findViewById(R.id.header_layout);
            LinearLayout expandContainer = cardView.findViewById(R.id.expand_container);

            com.google.android.material.materialswitch.MaterialSwitch switchMessages = cardView.findViewById(R.id.switch_messages);
            com.google.android.material.materialswitch.MaterialSwitch switchVisits = cardView.findViewById(R.id.switch_visits);
            com.google.android.material.materialswitch.MaterialSwitch switchPokes = cardView.findViewById(R.id.switch_pokes);
            com.google.android.material.materialswitch.MaterialSwitch switchFavorites = cardView.findViewById(R.id.switch_favorites);
            com.google.android.material.materialswitch.MaterialSwitch switchPhotos = cardView.findViewById(R.id.switch_photos);

            sexName.setText(desc);
            if (iconUrl != null && !iconUrl.isEmpty()) {
                Glide.with(this).load(iconUrl).into(sexIcon);
            } else {
                sexIcon.setImageResource(R.drawable.ic_default_avatar);
            }

            switchMessages.setChecked(viewModel.getNotificationFilter(desc, "chat"));
            switchVisits.setChecked(viewModel.getNotificationFilter(desc, "visit"));
            switchPokes.setChecked(viewModel.getNotificationFilter(desc, "poke"));
            switchFavorites.setChecked(viewModel.getNotificationFilter(desc, "favorite"));
            switchPhotos.setChecked(viewModel.getNotificationFilter(desc, "photo"));

            switchMessages.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setNotificationFilter(desc, "chat", isChecked));
            switchVisits.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setNotificationFilter(desc, "visit", isChecked));
            switchPokes.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setNotificationFilter(desc, "poke", isChecked));
            switchFavorites.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setNotificationFilter(desc, "favorite", isChecked));
            switchPhotos.setOnCheckedChangeListener((btn, isChecked) -> viewModel.setNotificationFilter(desc, "photo", isChecked));

            headerLayout.setOnClickListener(v -> {
                boolean isExpanded = expandContainer.getVisibility() == View.VISIBLE;
                if (isExpanded) {
                    expandContainer.setVisibility(View.GONE);
                    expandArrow.animate().rotation(0f).setDuration(200).start();
                } else {
                    expandContainer.setVisibility(View.VISIBLE);
                    expandArrow.animate().rotation(180f).setDuration(200).start();
                }
            });

            binding.settingsSexesContainer.addView(cardView);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
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
