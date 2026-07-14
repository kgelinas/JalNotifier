package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.databinding.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import io.github.kgelinas.jalfnotifier.databinding.ActivitySettingsWidgetBinding;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SettingsWidgetActivity extends AppCompatActivity {

    private ActivitySettingsWidgetBinding binding;
    private SharedPreferences prefs;
    private String myUserId;

    private static final String WORK_TAG = "WidgetRefreshWork";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsWidgetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(ApiConstants.PREFS_NAME, MODE_PRIVATE);
        myUserId = prefs.getString(ApiConstants.KEY_USER_ID, "");

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets insetsValues = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insetsValues.left, insetsValues.top, insetsValues.right, insetsValues.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.widgetToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupSettings();
    }

    private void setupSettings() {
        // Load refresh interval settings
        String[] intervalOptions = {
                getString(R.string.settings_widget_refresh_manual),
                getString(R.string.settings_widget_refresh_15m),
                getString(R.string.settings_widget_refresh_30m),
                getString(R.string.settings_widget_refresh_1h),
                getString(R.string.settings_widget_refresh_2h)
        };
        int[] intervalValues = {0, 15, 30, 60, 120};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, intervalOptions);
        binding.editWidgetRefreshInterval.setAdapter(adapter);

        int savedInterval = prefs.getInt(ApiConstants.KEY_WIDGET_REFRESH_INTERVAL + "_" + myUserId, 0);
        int selectedIndex = 0;
        for (int i = 0; i < intervalValues.length; i++) {
            if (intervalValues[i] == savedInterval) {
                selectedIndex = i;
                break;
            }
        }
        binding.editWidgetRefreshInterval.setText(intervalOptions[selectedIndex], false);

        binding.editWidgetRefreshInterval.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < intervalValues.length) {
                int minutes = intervalValues[position];
                prefs.edit().putInt(ApiConstants.KEY_WIDGET_REFRESH_INTERVAL + "_" + myUserId, minutes).apply();
                scheduleWidgetRefresh(minutes);
            }
        });

        // Load widget sort order settings
        String[] sortOptions = {
                getString(R.string.sort_favorites_default),
                getString(R.string.sort_favorites_timedesc),
                getString(R.string.sort_favorites_timeasc)
        };
        String[] sortValues = {"default", "timedesc", "timeasc"};

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, sortOptions);
        binding.editWidgetSortOrder.setAdapter(sortAdapter);

        String savedSort = prefs.getString(ApiConstants.KEY_WIDGET_SORT_ORDER + "_" + myUserId, "default");
        int selectedSortIndex = 0;
        for (int i = 0; i < sortValues.length; i++) {
            if (sortValues[i].equals(savedSort)) {
                selectedSortIndex = i;
                break;
            }
        }
        binding.editWidgetSortOrder.setText(sortOptions[selectedSortIndex], false);

        binding.editWidgetSortOrder.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < sortValues.length) {
                String sortVal = sortValues[position];
                prefs.edit().putString(ApiConstants.KEY_WIDGET_SORT_ORDER + "_" + myUserId, sortVal).apply();
                triggerWidgetUpdate();
            }
        });

        // Load Online Only
        binding.switchWidgetOnlineOnly.setChecked(prefs.getBoolean(ApiConstants.KEY_WIDGET_FILTER_ONLINE + "_" + myUserId, false));
        binding.switchWidgetOnlineOnly.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(ApiConstants.KEY_WIDGET_FILTER_ONLINE + "_" + myUserId, isChecked).apply();
            triggerWidgetUpdate();
        });

        // Load groups
        Set<String> defaultGroups = new HashSet<>();
        defaultGroups.add("favorite");
        defaultGroups.add("bookmark");
        defaultGroups.add("notified");
        Set<String> activeGroups = prefs.getStringSet(ApiConstants.KEY_WIDGET_FILTER_CONTACT_TYPES + "_" + myUserId, defaultGroups);

        binding.switchGroupFavorites.setChecked(activeGroups.contains("favorite"));
        binding.switchGroupBookmarks.setChecked(activeGroups.contains("bookmark"));
        binding.switchGroupFollowed.setChecked(activeGroups.contains("notified"));

        CompoundButton.OnCheckedChangeListener groupListener = (btn, isChecked) -> {
            Set<String> newGroups = new HashSet<>();
            if (binding.switchGroupFavorites.isChecked()) newGroups.add("favorite");
            if (binding.switchGroupBookmarks.isChecked()) newGroups.add("bookmark");
            if (binding.switchGroupFollowed.isChecked()) newGroups.add("notified");
            prefs.edit().putStringSet(ApiConstants.KEY_WIDGET_FILTER_CONTACT_TYPES + "_" + myUserId, newGroups).apply();
            triggerWidgetUpdate();
        };

        binding.switchGroupFavorites.setOnCheckedChangeListener(groupListener);
        binding.switchGroupBookmarks.setOnCheckedChangeListener(groupListener);
        binding.switchGroupFollowed.setOnCheckedChangeListener(groupListener);

        // Load sexes
        Set<String> activeSexes = prefs.getStringSet(ApiConstants.KEY_WIDGET_FILTER_SEXES + "_" + myUserId, new HashSet<>());
        binding.switchSexHomme.setChecked(activeSexes.contains("Homme"));
        binding.switchSexFemme.setChecked(activeSexes.contains("Femme"));
        binding.switchSexCouple.setChecked(activeSexes.contains("Couple"));
        binding.switchSexTravesti.setChecked(activeSexes.contains("Travesti"));
        binding.switchSexTrans.setChecked(activeSexes.contains("Trans"));
        binding.switchSexCoupleF.setChecked(activeSexes.contains("Couple F"));
        binding.switchSexCoupleH.setChecked(activeSexes.contains("Couple H"));

        CompoundButton.OnCheckedChangeListener sexListener = (btn, isChecked) -> {
            Set<String> newSexes = new HashSet<>();
            if (binding.switchSexHomme.isChecked()) newSexes.add("Homme");
            if (binding.switchSexFemme.isChecked()) newSexes.add("Femme");
            if (binding.switchSexCouple.isChecked()) newSexes.add("Couple");
            if (binding.switchSexTravesti.isChecked()) newSexes.add("Travesti");
            if (binding.switchSexTrans.isChecked()) newSexes.add("Trans");
            if (binding.switchSexCoupleF.isChecked()) newSexes.add("Couple F");
            if (binding.switchSexCoupleH.isChecked()) newSexes.add("Couple H");
            prefs.edit().putStringSet(ApiConstants.KEY_WIDGET_FILTER_SEXES + "_" + myUserId, newSexes).apply();
            triggerWidgetUpdate();
        };

        binding.switchSexHomme.setOnCheckedChangeListener(sexListener);
        binding.switchSexFemme.setOnCheckedChangeListener(sexListener);
        binding.switchSexCouple.setOnCheckedChangeListener(sexListener);
        binding.switchSexTravesti.setOnCheckedChangeListener(sexListener);
        binding.switchSexTrans.setOnCheckedChangeListener(sexListener);
        binding.switchSexCoupleF.setOnCheckedChangeListener(sexListener);
        binding.switchSexCoupleH.setOnCheckedChangeListener(sexListener);
    }

    private void scheduleWidgetRefresh(int minutes) {
        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        wm.cancelAllWorkByTag(WORK_TAG);

        if (minutes > 0) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    WidgetRefreshWorker.class, minutes, TimeUnit.MINUTES)
                    .addTag(WORK_TAG)
                    .setConstraints(constraints)
                    .build();

            wm.enqueueUniquePeriodicWork(
                    WORK_TAG,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
            );
        }
    }

    private void triggerWidgetUpdate() {
        android.appwidget.AppWidgetManager appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, ContactsWidgetProvider.class));
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_contacts_list);
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
