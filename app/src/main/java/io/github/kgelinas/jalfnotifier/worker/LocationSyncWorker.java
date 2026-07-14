package io.github.kgelinas.jalfnotifier.worker;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;

public class LocationSyncWorker extends Worker {
    private static final String TAG = "LocationSyncWorker";

    public LocationSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        int remoteGeo = prefs.getInt("remote_geolocation_" + myId, 1);
        if (remoteGeo == 0) {
            Log.d(TAG, "Geolocation disabled in JALF profile/app, skipping sync");
            return Result.success();
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        
        try {
            // Check for permissions
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, 
                    android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, 
                    android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Location permission not granted for background sync");
                return Result.failure();
            }

            // Await the location result
            Location location = Tasks.await(fusedLocationClient.getLastLocation(), 10, TimeUnit.SECONDS);
            if (location != null) {
                // Check if update is needed (at least 30 mins since last to be conservative, or 15)
                long lastUpdate = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                        .getLong(ApiConstants.KEY_LAST_LOCATION_UPDATE, 0);
                long now = System.currentTimeMillis();
                
                // Jalf seems to use location for "nearby" searches, so 30 mins is probably fine.
                if (now - lastUpdate < TimeUnit.MINUTES.toMillis(30)) {
                    Log.d(TAG, "Location update skipped: last update was less than 30 mins ago");
                    return Result.success();
                }

                // Send synchronously using the callback to block until completion if possible,
                // or just rely on the task running in background.
                // Since sendLocation is async, we can't easily wait for it here unless we change it.
                // But for a Worker, fire-and-forget is mostly okay as long as we don't spam.
                LocationUpdateTask.sendLocation(context, location.getLatitude(), location.getLongitude(), null);
                return Result.success();
            } else {
                Log.w(TAG, "Could not obtain last known location");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in LocationSyncWorker", e);
            return Result.retry();
        }

        return Result.failure();
    }
}
