package io.github.kgelinas.jalfnotifier;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.github.kgelinas.jalfnotifier.databinding.ActivitySettingsSecurityBinding;

public class SettingsSecurityActivity extends AppCompatActivity {

    private ActivitySettingsSecurityBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsSecurityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(ApiConstants.PREFS_NAME, MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets insetsValues = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insetsValues.left, insetsValues.top, insetsValues.right, insetsValues.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.securityToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupSettings();

        if (AppTourManager.getInstance().isTourActive()) {
            AppTourManager.getInstance().continueTour(this);
        }
    }

    private void setupSettings() {
        boolean biometricLogin = prefs.getBoolean(ApiConstants.KEY_BIOMETRIC_LOGIN, false);
        boolean biometricLock = prefs.getBoolean(ApiConstants.KEY_BIOMETRIC_LOCK, false);

        binding.switchBiometricLogin.setChecked(biometricLogin);
        binding.switchBiometricLock.setChecked(biometricLock);

        binding.switchBiometricLogin.setOnCheckedChangeListener((view, isChecked) -> {
            if (isChecked) {
                // Confirm with biometric before enabling
                BiometricHelper.showPrompt(this, getString(R.string.settings_security_confirm_access),
                        getString(R.string.settings_security_auth_reason),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(
                                    @NonNull BiometricPrompt.AuthenticationResult result) {
                                prefs.edit().putBoolean(ApiConstants.KEY_BIOMETRIC_LOGIN, true).apply();
                                Toast.makeText(SettingsSecurityActivity.this, R.string.settings_security_auth_enabled,
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                                binding.switchBiometricLogin.setChecked(false);
                                Toast.makeText(SettingsSecurityActivity.this, getString(R.string.settings_security_auth_failed),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                prefs.edit().putBoolean(ApiConstants.KEY_BIOMETRIC_LOGIN, false).apply();
            }
        });

        binding.switchBiometricLock.setOnCheckedChangeListener((view, isChecked) -> {
            prefs.edit().putBoolean(ApiConstants.KEY_BIOMETRIC_LOCK, isChecked).apply();
        });

        // Setup Auto Appear Offline Dropdown
        String[] delayOptions = new String[16];
        delayOptions[0] = getString(R.string.settings_security_auto_offline_disabled);
        delayOptions[1] = getString(R.string.settings_security_auto_offline_5m);
        delayOptions[2] = getString(R.string.settings_security_auto_offline_15m);
        delayOptions[3] = getString(R.string.settings_security_auto_offline_30m);
        for (int i = 1; i <= 12; i++) {
            if (i == 1) {
                delayOptions[i + 3] = getString(R.string.settings_security_auto_offline_hour, i);
            } else {
                delayOptions[i + 3] = getString(R.string.settings_security_auto_offline_hours, i);
            }
        }

        int[] delayMinutesValues = {0, 5, 15, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 660, 720};

        android.widget.ArrayAdapter<String> delayAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, delayOptions);
        binding.editAutoOfflineDelay.setAdapter(delayAdapter);

        // Migrate old settings if necessary
        int savedDelayMinutes;
        if (prefs.contains(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES)) {
            savedDelayMinutes = prefs.getInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES, 60);
        } else if (prefs.contains(ApiConstants.KEY_AUTO_OFFLINE_DELAY_HOURS)) {
            int oldHours = prefs.getInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_HOURS, 1);
            savedDelayMinutes = oldHours * 60;
            prefs.edit().putInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES, savedDelayMinutes).apply();
        } else {
            savedDelayMinutes = 60;
            prefs.edit().putInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES, savedDelayMinutes).apply();
        }

        int selectedIndex = 0;
        for (int i = 0; i < delayMinutesValues.length; i++) {
            if (delayMinutesValues[i] == savedDelayMinutes) {
                selectedIndex = i;
                break;
            }
        }
        binding.editAutoOfflineDelay.setText(delayOptions[selectedIndex], false);

        binding.editAutoOfflineDelay.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < delayMinutesValues.length) {
                prefs.edit().putInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES, delayMinutesValues[position]).apply();
            }

            // Cancel any existing scheduled WorkManager tasks immediately so it reschedules cleanly next time they leave
            androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag("AutoOfflineWork");
        });
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
