package io.github.kgelinas.jalfnotifier;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ContactsWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ContactsWidgetProvider";

    public static final String ACTION_REFRESH = "io.github.kgelinas.jalfnotifier.widget.ACTION_REFRESH";
    public static final String ACTION_CLICK = "io.github.kgelinas.jalfnotifier.widget.ACTION_CLICK";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive action: " + action);

        if (ACTION_REFRESH.equals(action)) {
            // 1. Visually show "Updating..." on the widget immediately
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, ContactsWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

            for (int appWidgetId : appWidgetIds) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.layout_widget_contacts);
                views.setTextViewText(R.id.widget_last_updated, context.getString(R.string.widget_updating));
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
            }

            // 2. Perform refresh on background thread using goAsync()
            final PendingResult pendingResult = goAsync();
            new Thread(() -> {
                try {
                    doBackgroundRefresh(context);
                } catch (Exception e) {
                    Log.e(TAG, "Error in background refresh", e);
                } finally {
                    // 3. Notify widget list that data changed and update all widgets
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_contacts_list);
                    for (int appWidgetId : appWidgetIds) {
                        updateWidget(context, appWidgetManager, appWidgetId);
                    }
                    pendingResult.finish();
                }
            }).start();

        } else if (ACTION_CLICK.equals(action)) {
            String userLink = intent.getStringExtra(ApiConstants.EXTRA_USER_LINK);
            String otherUserId = intent.getStringExtra(ApiConstants.EXTRA_OTHER_USER_ID);
            String otherName = intent.getStringExtra(ApiConstants.EXTRA_OTHER_NAME);

            Log.d(TAG, "Clicked contact: " + otherName + ", link: " + userLink);

            if (userLink != null && !userLink.isEmpty()) {
                Intent activityIntent = new Intent(context, MainActivity.class);
                activityIntent.putExtra(ApiConstants.EXTRA_USER_LINK, userLink);
                activityIntent.putExtra(ApiConstants.EXTRA_OTHER_USER_ID, otherUserId);
                activityIntent.putExtra(ApiConstants.EXTRA_OTHER_NAME, otherName);
                activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(activityIntent);
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.layout_widget_contacts);

        // Header click opens MainActivity
        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent mainPI = PendingIntent.getActivity(context, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_header, mainPI);

        // Refresh button click triggers ACTION_REFRESH broadcast
        Intent refreshIntent = new Intent(context, ContactsWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPI = PendingIntent.getBroadcast(context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPI);

        // Settings button click opens SettingsWidgetActivity
        Intent settingsIntent = new Intent(context, SettingsWidgetActivity.class);
        PendingIntent settingsPI = PendingIntent.getActivity(context, appWidgetId, settingsIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.btn_widget_settings, settingsPI);

        // Bind RemoteViewsService for ListView
        Intent serviceIntent = new Intent(context, ContactsWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_contacts_list, serviceIntent);
        views.setEmptyView(R.id.widget_contacts_list, R.id.widget_contacts_empty);

        // Set up click intent template for ListView rows
        Intent clickIntent = new Intent(context, ContactsWidgetProvider.class);
        clickIntent.setAction(ACTION_CLICK);
        PendingIntent clickPI = PendingIntent.getBroadcast(context, appWidgetId, clickIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setPendingIntentTemplate(R.id.widget_contacts_list, clickPI);

        // Display last updated timestamp
        SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        long lastUpdate = prefs.getLong("WIDGET_LAST_UPDATE_TS", 0);
        String updatedStr;
        if (lastUpdate > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            updatedStr = context.getString(R.string.widget_updated_time, sdf.format(new Date(lastUpdate)));
        } else {
            updatedStr = context.getString(R.string.widget_empty_text);
        }
        views.setTextViewText(R.id.widget_last_updated, updatedStr);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void doBackgroundRefresh(Context context) {
        Log.d(TAG, "Performing background refresh of favorites...");

        SecurePrefs secure = SecurePrefs.get(context);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String myUserId = prefs.getString(ApiConstants.KEY_USER_ID, "");

        if (fullCookie.isEmpty() || suid.isEmpty() || myUserId.isEmpty()) {
            Log.e(TAG, "Cannot refresh: missing credentials");
            return;
        }

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/favorites")
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        try (okhttp3.Response response = client.newCall(req).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray items = json.optJSONArray("items");
                if (items == null) {
                    items = new JSONArray(body);
                }

                Set<String> favoritesSet = new HashSet<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject obj = items.getJSONObject(i);
                    String userLink = obj.optString("user_link", "");
                    if (!userLink.isEmpty()) {
                        favoritesSet.add(userLink);
                    }
                    String otherUserId = StringUtils.extractUserIdFromLink(userLink);
                    if (otherUserId.isEmpty()) {
                        otherUserId = obj.optString("member_id", obj.optString("id", ""));
                    }
                    // Update status in ProfileCacheManager cache
                    boolean online = obj.optBoolean("online", false) || obj.optBoolean("is_online", false);
                    ProfileCacheManager.getInstance().updateStatus(context, otherUserId, online ? 1 : 0, null);
                }

                prefs.edit()
                        .putStringSet("CACHED_FAVORITES_LINKS", favoritesSet)
                        .putLong("WIDGET_LAST_UPDATE_TS", System.currentTimeMillis())
                        .apply();
                Log.d(TAG, "Background refresh succeeded, cached " + favoritesSet.size() + " links");
            } else {
                Log.e(TAG, "Refresh failed: HTTP " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during background refresh", e);
        }
    }
}
