package io.github.kgelinas.jalfnotifier.util;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Global lifecycle observer targeting the entire application process.
 * Reliably schedules background presence timers when leaving the app and
 * seamlessly brings the user back online when returning.
 */
public class AppLifecycleObserver implements DefaultLifecycleObserver {
    private static final String TAG = "AppLifecycleObserver";
    private static final String WORK_TAG = "AutoOfflineWork";
    private final Context context;

    public AppLifecycleObserver(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        Log.d(TAG, "Application came to foreground. Cancelling auto-offline tasks.");
        
        // 1. Cancel any pending auto-offline background work
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);

        // 2. Restore presence to online ONLY if they were set to offline automatically by the timer
        AppPrefs prefs = AppPrefs.getInstance(context);
        boolean wasAutoOfflined = prefs.getBoolean(ApiConstants.KEY_WAS_AUTO_OFFLINED, false);
        
        if (wasAutoOfflined) {
            Log.d(TAG, "Automatically restoring user presence to online.");
            
            // Revert state
            prefs.edit()
                    .putBoolean(ApiConstants.KEY_APPEAR_OFFLINE, false)
                    .putBoolean(ApiConstants.KEY_WAS_AUTO_OFFLINED, false)
                    .apply();

            // Notify server to show as visible again
            Map<String, String> fields = new HashMap<>();
            fields.put("Visible", "yes");
            fields.put("save", "Enregistrer");
            ProfileUpdateTask.updateProfileFields(context, fields, null);
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        Log.d(TAG, "Application went to background. Evaluating auto-offline task.");
        
        AppPrefs prefs = AppPrefs.getInstance(context);
        int delayMinutes;
        if (prefs.contains(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES)) {
            delayMinutes = prefs.getInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES, 60);
        } else if (prefs.contains(ApiConstants.KEY_AUTO_OFFLINE_DELAY_HOURS)) {
            int oldHours = prefs.getInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_HOURS, 1);
            delayMinutes = oldHours * 60;
            prefs.edit().putInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES, delayMinutes).apply();
        } else {
            delayMinutes = 60;
            prefs.edit().putInt(ApiConstants.KEY_AUTO_OFFLINE_DELAY_MINUTES, delayMinutes).apply();
        }
        boolean manuallyOffline = prefs.getBoolean(ApiConstants.KEY_APPEAR_OFFLINE, false);

        // ONLY schedule the background task if:
        // - Inactivity timer is enabled (> 0)
        // - User did NOT manually enable Appear Offline (Ghost Mode)
        if (delayMinutes > 0 && !manuallyOffline) {
            Log.d(TAG, "Scheduling auto-offline worker to run in " + delayMinutes + " minute(s).");
            
            OneTimeWorkRequest offlineRequest = new OneTimeWorkRequest.Builder(AutoOfflineWorker.class)
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .addTag(WORK_TAG)
                    .build();

            WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_TAG,
                    ExistingWorkPolicy.REPLACE,
                    offlineRequest
            );
        } else {
            Log.d(TAG, "Auto-offline worker schedule skipped. Delay: " + delayMinutes + " minute(s), Manually offline: " + manuallyOffline);
        }
    }
}
