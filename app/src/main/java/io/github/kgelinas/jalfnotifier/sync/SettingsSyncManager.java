package io.github.kgelinas.jalfnotifier.sync;

import android.content.Context;
import android.util.Log;

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.github.kgelinas.jalfnotifier.ApiConstants;
import io.github.kgelinas.jalfnotifier.JalfNotifierApplication;

/**
 * Manages backup and restore of app settings to Google Drive App Data folder.
 */
public class SettingsSyncManager {
    private static final String TAG = "SettingsSyncManager";
    private static final String BACKUP_FILENAME = "jalf_settings_backup.json";

    public interface SyncCallback {
        void onSuccess();

        void onFailure(Exception e);
    }

    public static void backupToDrive(Context context, String accountEmail, SyncCallback callback) {
        JalfNotifierApplication.IO_EXECUTOR.execute(() -> {
            try {
                Drive driveService = getDriveService(context, accountEmail);
                if (driveService == null) {
                    throw new Exception("Google Drive service not initialized");
                }

                JSONObject backupJson = createBackupJson(context);
                String jsonStr = backupJson.toString();
                byte[] content = jsonStr.getBytes(StandardCharsets.UTF_8);
                AbstractInputStreamContent streamContent = new ByteArrayContent("application/json", content);

                // Check if file already exists
                String existingFileId = findBackupFileId(driveService);

                File metadata = new File();
                metadata.setName(BACKUP_FILENAME);

                if (existingFileId != null) {
                    // Update existing
                    driveService.files().update(existingFileId, null, streamContent).execute();
                    Log.d(TAG, "Backup updated on Drive: " + existingFileId);
                } else {
                    // Create new
                    metadata.setParents(Collections.singletonList("appDataFolder"));
                    File file = driveService.files().create(metadata, streamContent).execute();
                    Log.d(TAG, "Backup created on Drive: " + file.getId());
                }

                if (callback != null)
                    callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Backup failed", e);
                if (callback != null)
                    callback.onFailure(e);
            }
        });
    }

    public static void restoreFromDrive(Context context, String accountEmail, SyncCallback callback) {
        JalfNotifierApplication.IO_EXECUTOR.execute(() -> {
            try {
                Drive driveService = getDriveService(context, accountEmail);
                if (driveService == null) {
                    throw new Exception("Google Drive service not initialized");
                }

                String fileId = findBackupFileId(driveService);
                if (fileId == null) {
                    throw new Exception("No backup file found on Google Drive");
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                byte[] bytes = outputStream.toByteArray();
                String jsonStr = new String(bytes, StandardCharsets.UTF_8);

                JSONObject backupJson = new JSONObject(jsonStr);
                applyBackupJson(context, backupJson);

                if (callback != null)
                    callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Restore failed", e);
                if (callback != null)
                    callback.onFailure(e);
            }
        });
    }

    private static Drive getDriveService(Context context, String accountEmail) {
        if (accountEmail == null || accountEmail.isEmpty())
            return null;
        return GoogleDriveManager.getInstance(context).getDriveService(context, accountEmail);
    }

    private static String findBackupFileId(Drive driveService) throws Exception {
        FileList result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '" + BACKUP_FILENAME + "'")
                .setFields("files(id, name)")
                .execute();

        for (File file : result.getFiles()) {
            if (BACKUP_FILENAME.equals(file.getName())) {
                return file.getId();
            }
        }
        return null;
    }

    public static JSONObject createBackupJson(Context context) throws Exception {
        JSONObject root = new JSONObject();

        // 1. Named Searches (Simple & Detailed)
        // We reuse the strings stored in SharedPreferences used by
        // SearchSettingsManager
        android.content.SharedPreferences searchPrefs = context.getSharedPreferences("search_prefs",
                Context.MODE_PRIVATE);
        JSONObject searchData = new JSONObject();
        searchData.put("last_search_settings", searchPrefs.getString("last_search_settings", "{}"));
        searchData.put("named_searches_simple", searchPrefs.getString("named_searches_simple", "{}"));
        searchData.put("named_searches_det", searchPrefs.getString("named_searches_det", "{}"));
        root.put("search_data", searchData);

        // 1.5 Pinned Conversations
        android.content.SharedPreferences pinPrefs = context.getSharedPreferences("PinnedConversations",
                Context.MODE_PRIVATE);
        JSONObject pinData = new JSONObject();
        Set<String> pins = pinPrefs.getStringSet("links", new HashSet<>());
        pinData.put("links", new JSONArray(pins));
        root.put("pinned_data", pinData);

        // 2. User Preferences
        android.content.SharedPreferences userPrefs = context.getSharedPreferences(ApiConstants.PREFS_NAME,
                Context.MODE_PRIVATE);
        JSONObject prefsData = new JSONObject();
        Map<String, ?> allEntries = userPrefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            // Filter out transient/sensitive data
            if (key.startsWith("CACHED_") || key.equals(ApiConstants.KEY_FULL_COOKIE)
                    || key.equals(ApiConstants.KEY_SUID) || key.equals(ApiConstants.KEY_PASSWORD)
                    || key.equals(ApiConstants.KEY_NOTIFIED_LINKS) || key.equals("APP_IN_FOREGROUND")) {
                continue;
            }
            Object val = entry.getValue();
            if (val instanceof java.util.Collection) {
                prefsData.put(key, new JSONArray((java.util.Collection<?>) val));
            } else if (val instanceof java.util.Set) {
                // Should be covered by Collection, but being explicit for Sets
                prefsData.put(key, new JSONArray((java.util.Set<?>) val));
            } else {
                prefsData.put(key, val);
            }
        }
        root.put("user_prefs", prefsData);

        root.put("backup_timestamp", System.currentTimeMillis());
        root.put("app_version", "1.0");

        return root;
    }

    public static void applyBackupJson(Context context, JSONObject root) throws Exception {
        // 1. Search Data
        JSONObject searchData = root.optJSONObject("search_data");
        if (searchData != null) {
            android.content.SharedPreferences.Editor searchEditor = context
                    .getSharedPreferences("search_prefs", Context.MODE_PRIVATE).edit();
            Iterator<String> keys = searchData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = searchData.getString(key);
                searchEditor.putString(key, val);
            }
            if (searchEditor.commit()) {
                Log.d(TAG, "Search prefs committed successfully.");
            } else {
                Log.e(TAG, "Search prefs commit FAILED.");
            }
        }

        // 1.5 Pinned Data
        JSONObject pinnedData = root.optJSONObject("pinned_data");
        if (pinnedData != null) {
            android.content.SharedPreferences.Editor pinEditor = context
                    .getSharedPreferences("PinnedConversations", Context.MODE_PRIVATE).edit();
            JSONArray links = pinnedData.optJSONArray("links");
            if (links != null) {
                Set<String> set = new HashSet<>();
                for (int i = 0; i < links.length(); i++) {
                    set.add(links.optString(i));
                }
                pinEditor.putStringSet("links", set);
                pinEditor.apply();
            }
        }

        // 2. User Prefs
        JSONObject prefsData = root.optJSONObject("user_prefs");
        if (prefsData != null) {
            android.content.SharedPreferences.Editor prefsEditor = context
                    .getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE).edit();
            Iterator<String> keys = prefsData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object val = prefsData.get(key);
                if (val instanceof Boolean)
                    prefsEditor.putBoolean(key, (Boolean) val);
                else if (val instanceof Integer)
                    prefsEditor.putInt(key, (Integer) val);
                else if (val instanceof Long)
                    prefsEditor.putLong(key, (Long) val);
                else if (val instanceof Float)
                    prefsEditor.putFloat(key, (Float) val);
                else if (val instanceof Double)
                    prefsEditor.putFloat(key, ((Double) val).floatValue());
                else if (val instanceof String)
                    prefsEditor.putString(key, (String) val);
                else if (val instanceof JSONArray) {
                    JSONArray arr = (JSONArray) val;
                    java.util.Set<String> set = new java.util.HashSet<>();
                    for (int i = 0; i < arr.length(); i++) {
                        set.add(arr.optString(i));
                    }
                    prefsEditor.putStringSet(key, set);
                }
            }
            if (prefsEditor.commit()) {
                Log.d(TAG, "User prefs committed successfully.");
            } else {
                Log.e(TAG, "User prefs commit FAILED.");
            }
        }
    }
}
