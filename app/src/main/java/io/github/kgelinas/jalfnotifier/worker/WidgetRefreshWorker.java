package io.github.kgelinas.jalfnotifier.worker;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class WidgetRefreshWorker extends Worker {
    private static final String TAG = "WidgetRefreshWorker";

    public WidgetRefreshWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Worker running periodic widget refresh");
        Context context = getApplicationContext();
        try {
            ContactsWidgetProvider.doBackgroundRefresh(context);

            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, ContactsWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_contacts_list);
            for (int appWidgetId : appWidgetIds) {
                ContactsWidgetProvider.updateWidget(context, appWidgetManager, appWidgetId);
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in WidgetRefreshWorker", e);
            return Result.failure();
        }
    }
}
