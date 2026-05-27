package io.github.kgelinas.jalfnotifier;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class JalfNotificationWorker extends Worker {

    public JalfNotificationWorker(@NonNull Context context,
                                   @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        JalfNotificationTask task = new JalfNotificationTask(getApplicationContext());
        boolean success = task.execute(true);
        return success ? Result.success() : Result.failure();
    }
}
