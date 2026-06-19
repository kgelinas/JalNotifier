package io.github.kgelinas.jalfnotifier;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String GITHUB_RELEASE_URL = "https://api.github.com/repos/kgelinas/JalNotifier/releases/latest";

    public static void checkForUpdates(final Activity activity, final boolean showUpToDateToast) {
        AlertDialog progressDialog = null;
        if (showUpToDateToast) {
            ProgressBar progressBar = new ProgressBar(activity);
            progressBar.setIndeterminate(true);
            int p = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, activity.getResources().getDisplayMetrics()));
            progressBar.setPadding(p, p, p, p);
            progressDialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_checking)
                    .setView(progressBar)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        }

        final AlertDialog finalProgressDialog = progressDialog;

        Request req = new Request.Builder()
                .url(GITHUB_RELEASE_URL)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        JalfNotifierApplication.httpClient().newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to check for updates", e);
                activity.runOnUiThread(() -> {
                    if (finalProgressDialog != null && finalProgressDialog.isShowing()) {
                        finalProgressDialog.dismiss();
                    }
                    if (showUpToDateToast) {
                        String msg = activity.getString(R.string.update_failed, e.getMessage());
                        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (finalProgressDialog != null) {
                        activity.runOnUiThread(finalProgressDialog::dismiss);
                    }

                    if (!r.isSuccessful() || r.body() == null) {
                        activity.runOnUiThread(() -> {
                            if (showUpToDateToast) {
                                String msg = activity.getString(R.string.update_failed, "HTTP " + r.code());
                                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }

                    String jsonStr = NetworkUtils.responseToString(r);
                    JSONObject json = new JSONObject(jsonStr);
                    String tagName = json.optString("tag_name", "");
                    JSONArray assets = json.optJSONArray("assets");

                    String downloadUrl = null;
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }

                    final String finalDownloadUrl = downloadUrl;
                    String currentVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;

                    if (isNewerVersion(currentVersion, tagName) && finalDownloadUrl != null) {
                        activity.runOnUiThread(() -> showUpdateAvailableDialog(activity, tagName, finalDownloadUrl));
                    } else {
                        activity.runOnUiThread(() -> {
                            if (showUpToDateToast) {
                                Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking for updates", e);
                    activity.runOnUiThread(() -> {
                        if (showUpToDateToast) {
                            String msg = activity.getString(R.string.update_failed, e.getMessage());
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private static boolean isNewerVersion(String current, String remote) {
        if (current == null || remote == null) return false;
        String cleanCurrent = current.replaceAll("[^0-9.]", "");
        String cleanRemote = remote.replaceAll("[^0-9.]", "");
        if (cleanCurrent.isEmpty() || cleanRemote.isEmpty()) return false;
        
        String[] currentParts = cleanCurrent.split("\\.");
        String[] remoteParts = cleanRemote.split("\\.");
        int length = Math.max(currentParts.length, remoteParts.length);
        for (int i = 0; i < length; i++) {
            int c = i < currentParts.length && !currentParts[i].isEmpty() ? Integer.parseInt(currentParts[i]) : 0;
            int r = i < remoteParts.length && !remoteParts[i].isEmpty() ? Integer.parseInt(remoteParts[i]) : 0;
            if (r > c) return true;
            if (c > r) return false;
        }
        return false;
    }

    private static void showUpdateAvailableDialog(final Activity activity, final String version, final String downloadUrl) {
        String msg = activity.getString(R.string.update_available_message, version);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(msg)
                .setPositiveButton(R.string.add, (dialog, which) -> downloadAndInstallApk(activity, downloadUrl)) // Reuses standard positive action
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void downloadAndInstallApk(final Activity activity, final String downloadUrl) {
        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        int p = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, activity.getResources().getDisplayMetrics()));
        progressBar.setPadding(p, p, p, p);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_downloading)
                .setView(progressBar)
                .setCancelable(false)
                .create();
        progressDialog.show();

        Request req = new Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        JalfNotifierApplication.httpClient().newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to download update", e);
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    String msg = activity.getString(R.string.update_download_failed, e.getMessage());
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        activity.runOnUiThread(() -> {
                            progressDialog.dismiss();
                            String msg = activity.getString(R.string.update_download_failed, "HTTP " + r.code());
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    File apkFile = new File(activity.getCacheDir(), "update.apk");
                    ResponseBody body = r.body();
                    long totalBytes = body.contentLength();

                    try (InputStream is = body.byteStream();
                         FileOutputStream fos = new FileOutputStream(apkFile)) {
                        byte[] buffer = new byte[8192];
                        long bytesRead = 0;
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                            bytesRead += read;
                            if (totalBytes > 0) {
                                final int progress = (int) ((bytesRead * 100) / totalBytes);
                                activity.runOnUiThread(() -> progressBar.setProgress(progress));
                            }
                        }
                    }

                    activity.runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(activity, R.string.update_download_complete, Toast.LENGTH_SHORT).show();
                        checkInstallPermissionAndLaunch(activity, apkFile);
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error writing update APK file", e);
                    activity.runOnUiThread(() -> {
                        progressDialog.dismiss();
                        String msg = activity.getString(R.string.update_download_failed, e.getMessage());
                        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private static void checkInstallPermissionAndLaunch(final Activity activity, final File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_install_permission_title)
                    .setMessage(R.string.update_install_permission_message)
                    .setPositiveButton(R.string.add, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        intent.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            launchInstaller(activity, apkFile);
        }
    }

    private static void launchInstaller(Activity activity, File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(activity, "io.github.kgelinas.jalfnotifier.fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching package installer", e);
            Toast.makeText(activity, "Error starting installation: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
