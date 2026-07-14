package io.github.kgelinas.jalfnotifier.worker;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Background worker that automatically sets the user to "Appear Offline" (Visible=no)
 * after the inactivity delay has passed.
 */
public class AutoOfflineWorker extends Worker {
    private static final String TAG = "AutoOfflineWorker";

    public AutoOfflineWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        
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

        if (delayMinutes == 0) {
            Log.d(TAG, "Auto-offline setting is disabled, skipping worker");
            return Result.success();
        }

        if (manuallyOffline) {
            Log.d(TAG, "User is already manually set to Appear Offline, skipping worker");
            return Result.success();
        }

        Log.d(TAG, "Executing auto-offline task. Setting user presence to Appear Offline.");

        // Update local SharedPreferences state
        prefs.edit()
                .putBoolean(ApiConstants.KEY_APPEAR_OFFLINE, true)
                .putBoolean(ApiConstants.KEY_WAS_AUTO_OFFLINED, true)
                .apply();

        // Perform the network update on the server
        Map<String, String> fields = new HashMap<>();
        fields.put("Visible", "no");
        fields.put("save", "Enregistrer");

        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = new boolean[1];

        ProfileUpdateTask.updateProfileFields(context, fields, new ProfileUpdateTask.UpdateCallback() {
            @Override
            public void onSuccess() {
                success[0] = true;
                latch.countDown();
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to update profile to offline on server: " + error);
                success[0] = false;
                latch.countDown();
            }
        });

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for server presence update", e);
            return Result.retry();
        }

        if (success[0]) {
            Log.d(TAG, "Auto-offline server update succeeded");
            return Result.success();
        } else {
            // Revert local state so we retry clean next time
            prefs.edit()
                    .putBoolean(ApiConstants.KEY_APPEAR_OFFLINE, false)
                    .putBoolean(ApiConstants.KEY_WAS_AUTO_OFFLINED, false)
                    .apply();
            return Result.retry();
        }
    }
}
