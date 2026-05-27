package io.github.kgelinas.jalfnotifier;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class PermissionsActivity extends AppCompatActivity {

    private MaterialButton btnGrantNotif;
    private MaterialButton btnGrantLoc;
    private MaterialButton btnGrantBattery;
    private MaterialButton btnContinue;

    private static final int REQ_CODE_NOTIF = 101;
    private static final int REQ_CODE_LOC = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tv_permissions_title).getRootView(), (v, insets) -> {
            Insets insetsValues = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insetsValues.left, insetsValues.top, insetsValues.right, insetsValues.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        btnGrantNotif = findViewById(R.id.btn_grant_notif);
        btnGrantLoc = findViewById(R.id.btn_grant_loc);
        btnGrantBattery = findViewById(R.id.btn_grant_battery);
        btnContinue = findViewById(R.id.btn_permissions_continue);

        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStates();
    }

    private void setupListeners() {
        btnGrantNotif.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_CODE_NOTIF);
            } else {
                Toast.makeText(this, R.string.permissions_btn_granted, Toast.LENGTH_SHORT).show();
            }
        });

        btnGrantLoc.setOnClickListener(v -> ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_CODE_LOC));

        btnGrantBattery.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    @SuppressLint("BatteryLife")
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    // Fallback to standard settings
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, R.string.permissions_btn_granted, Toast.LENGTH_SHORT).show();
            }
        });

        btnContinue.setOnClickListener(v -> {
            AppPrefs.getInstance(this).putBoolean(ApiConstants.KEY_PERMISSIONS_ONBOARDING_DONE, true);

            // Navigate to main activity or login screen depending on cookie state
            SecurePrefs secure = SecurePrefs.get(this);
            String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
            Intent intent;
            if (!fullCookie.isEmpty()) {
                intent = new Intent(this, MainActivity.class);
            } else {
                intent = new Intent(this, LoginActivity.class);
            }
            startActivity(intent);
            finish();
        });
    }

    private void updatePermissionStates() {
        // Notifications
        boolean notifGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        updateButtonState(btnGrantNotif, notifGranted);

        // Location
        boolean locGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        updateButtonState(btnGrantLoc, locGranted);

        // Battery
        boolean batteryGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                batteryGranted = pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        }
        updateButtonState(btnGrantBattery, batteryGranted);
    }

    private void updateButtonState(MaterialButton button, boolean granted) {
        if (granted) {
            button.setText(R.string.permissions_btn_granted);
            button.setEnabled(false);
            button.setIcon(ContextCompat.getDrawable(this, android.R.drawable.checkbox_on_background));
        } else {
            button.setText(R.string.permissions_btn_grant);
            button.setEnabled(true);
            button.setIcon(null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updatePermissionStates();
    }
}
