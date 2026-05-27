package io.github.kgelinas.jalfnotifier;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.kgelinas.jalfnotifier.databinding.ActivitySettingsCloudBinding;
import io.github.kgelinas.jalfnotifier.sync.SettingsSyncManager;

public class SettingsCloudActivity extends AppCompatActivity {

    private ActivitySettingsCloudBinding binding;
    private SettingsViewModel viewModel;

    // ── SAF launchers ─────────────────────────────────────────────────────────

    /** Opens a file picker to choose a destination for the JSON export. */
    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        performExport(uri);
                    }
                }
            });

    /** Opens a file picker to select a JSON file to import. */
    private final ActivityResultLauncher<Intent> importLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        showImportConfirmation(uri);
                    }
                }
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsCloudBinding.inflate(getLayoutInflater());
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

        setupLocalBackupButtons();
        setupDriveButtons();
        setupObservers();
    }

    // ── Local JSON export / import ────────────────────────────────────────────

    private void setupLocalBackupButtons() {
        binding.btnExportJson.setOnClickListener(v -> launchExportPicker());
        binding.btnImportJson.setOnClickListener(v -> launchImportPicker());
    }

    private void launchExportPicker() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "jalf_backup_" + timestamp + ".json";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        exportLauncher.launch(intent);
    }

    private void launchImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Accept both .json and general files (SAF handles filtering)
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "*/*"});
        importLauncher.launch(intent);
    }

    private void performExport(@NonNull Uri uri) {
        JalfNotifierApplication.IO_EXECUTOR.execute(() -> {
            try {
                JSONObject json = SettingsSyncManager.createBackupJson(this);
                byte[] bytes = json.toString(2).getBytes(StandardCharsets.UTF_8);

                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("Cannot open output stream");
                    os.write(bytes);
                    os.flush();
                }

                runOnUiThread(() ->
                        Toast.makeText(this, R.string.backup_export_success, Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                runOnUiThread(() ->
                        Snackbar.make(binding.getRoot(),
                                getString(R.string.backup_export_failed, e.getMessage()),
                                Snackbar.LENGTH_LONG).show());
            }
        });
    }

    private void showImportConfirmation(@NonNull Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(R.string.backup_import_confirm_msg)
                .setPositiveButton(R.string.btn_restore, (dialog, which) -> performImport(uri))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performImport(@NonNull Uri uri) {
        JalfNotifierApplication.IO_EXECUTOR.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new Exception("Cannot open input stream");
                    bytes = is.readAllBytes();
                }

                String jsonStr = new String(bytes, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonStr);
                SettingsSyncManager.applyBackupJson(this, json);

                runOnUiThread(() ->
                        Toast.makeText(this, R.string.backup_import_success, Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                runOnUiThread(() ->
                        Snackbar.make(binding.getRoot(),
                                getString(R.string.backup_import_failed, e.getMessage()),
                                Snackbar.LENGTH_LONG).show());
            }
        });
    }

    // ── Google Drive ──────────────────────────────────────────────────────────

    private void setupDriveButtons() {
        binding.btnGoogleSignin.setOnClickListener(v -> {
            io.github.kgelinas.jalfnotifier.sync.GoogleDriveManager.getInstance(this).signIn(this,
                    new io.github.kgelinas.jalfnotifier.sync.GoogleDriveManager.SignInCallback() {
                        @Override
                        public void onSuccess(String email) {
                            viewModel.setGoogleAccount(email);
                        }

                        @Override
                        public void onFailure(String message) {
                            Snackbar.make(binding.getRoot(),
                                    getString(R.string.settings_cloud_signin_failed, message),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    });
        });

        binding.btnGoogleSignout.setOnClickListener(v -> {
            io.github.kgelinas.jalfnotifier.sync.GoogleDriveManager.getInstance(this).signOut();
            viewModel.setGoogleAccount(null);
        });

        binding.btnBackupDrive.setOnClickListener(v -> viewModel.backup());

        binding.btnRestoreDrive.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_cloud_restore_title)
                        .setMessage(R.string.settings_cloud_restore_msg)
                        .setPositiveButton(R.string.btn_restore, (dialog, which) -> viewModel.restore())
                        .setNegativeButton(R.string.cancel, null)
                        .show()
        );
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private void setupObservers() {
        viewModel.googleAccount.observe(this, account -> {
            if (account != null) {
                binding.btnGoogleSignin.setVisibility(android.view.View.GONE);
                binding.layoutSyncActions.setVisibility(android.view.View.VISIBLE);
                binding.tvGoogleAccount.setText(account);
            } else {
                binding.btnGoogleSignin.setVisibility(android.view.View.VISIBLE);
                binding.layoutSyncActions.setVisibility(android.view.View.GONE);
            }
        });

        viewModel.lastSyncTime.observe(this, timeStr -> {
            if (timeStr != null && !timeStr.isEmpty()) {
                binding.tvLastSync.setText(getString(R.string.settings_cloud_last_backup, timeStr));
            } else {
                binding.tvLastSync.setText(R.string.settings_cloud_no_backup);
            }
        });

        viewModel.syncStatus.observe(this, statusMsg -> {
            if (statusMsg != null && !statusMsg.isEmpty()) {
                Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.autoBackupEnabled.observe(this, enabled ->
                binding.switchAutoBackup.setChecked(enabled != null && enabled));

        binding.switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.setAutoBackupEnabled(isChecked));
    }

    // ── Menu ──────────────────────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
