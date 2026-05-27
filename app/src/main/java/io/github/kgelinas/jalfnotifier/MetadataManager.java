package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Singleton manager that fetches and caches metadata from REST endpoints.
 * Allows resolving "/rest/metadata/ID" links into localized labels and icons.
 */
public class MetadataManager {
    private static final String TAG = "MetadataManager";
    private static volatile MetadataManager instance;
    private final OkHttpClient client = JalfNotifierApplication.httpClient();

    // Category -> (Link -> Label) - Using LinkedHashMap to preserve API order
    private final Map<String, Map<String, String>> categories = new ConcurrentHashMap<>();
    // Global Link -> Label for quick resolution
    private final Map<String, String> globalCache = new ConcurrentHashMap<>();
    // Global Link -> Icon URL
    private final Map<String, String> iconCache = new ConcurrentHashMap<>();
    // Global Link -> Imperial label (only populated for heights and weights)
    private final Map<String, String> imperialCache = new ConcurrentHashMap<>();

    private final List<Runnable> listeners = new ArrayList<>();
    private boolean isInitialized = false;

    private MetadataManager() {
    }

    public static MetadataManager getInstance() {
        if (instance == null) {
            synchronized (MetadataManager.class) {
                if (instance == null) {
                    instance = new MetadataManager();
                }
            }
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (isInitialized)
            return;

        SecurePrefs secure = SecurePrefs.get(context);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty()) {
            Log.w(TAG, "Cannot init: No credentials");
            return;
        }

        isInitialized = true;
        Log.d(TAG, "Initializing metadata caches...");

        // Primary categories
        fetch(fullCookie, suid, "/rest/sexes", "sexes", "sex_link", "description", "sexes");
        fetch(fullCookie, suid, "/rest/sexual-orientations", "sexual_orientations", "sexual_orientation_link",
                "description", "orientations");
        fetch(fullCookie, suid, "/rest/social-statuses", "social_statuses", "social_status_link", "description",
                "statuses");
        fetch(fullCookie, suid, "/rest/zodiac-signs", "zodiac_signs", "zodiac_sign_link", "description", "zodiacs");
        fetch(fullCookie, suid, "/rest/schedules-available", "schedules_available", "schedule_available_link",
                "description", "schedules");
        fetch(fullCookie, suid, "/rest/alcohol-uses", "alcohol_uses", "alcohol_use_link", "description", "alcohol");
        fetch(fullCookie, suid, "/rest/smoking", "smoking", "smoking_link", "description", "smoking");
        fetch(fullCookie, suid, "/rest/drug-uses", "drug_uses", "drug_use_link", "description", "drugs");
        fetch(fullCookie, suid, "/rest/occupations", "occupations", "occupation_link", "description", "occupations");
        fetch(fullCookie, suid, "/rest/ethnic-groups", "ethnic_groups", "ethnic_group_link", "description",
                "ethnic_groups");
        fetch(fullCookie, suid, "/rest/fantasies", "fantasies", "fantasy_link", "description", "fantasies");
        fetch(fullCookie, suid, "/rest/goals", "goals", "goal_link", "description", "goals");
        fetch(fullCookie, suid, "/rest/privileges", "privileges", "privilege_link", "description", "privileges");

        // Complex metrics — store both metric and imperial variants
        fetchWithBoth(fullCookie, suid, "/rest/weights", "weights", "weight_link", "metric", "imperial", "weights");
        fetchWithBoth(fullCookie, suid, "/rest/heights", "heights", "height_link", "metric", "imperial", "heights");
    }

    private void fetch(String cookie, String suid, String path, String rootKey, String linkKey, String labelKey,
            String category) {
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + path)
                .addHeader("Cookie", cookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch " + path, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        String body = NetworkUtils.responseToString(r);
                        JSONObject root = new JSONObject(body);
                        JSONArray items = root.optJSONArray(rootKey);
                        if (items != null) {
                            // Use LinkedHashMap and sync on the categories map to maintain order and
                            // thread-safety
                            Map<String, String> catMap;
                            synchronized (categories) {
                                catMap = categories.get(category);
                                if (catMap == null) {
                                    catMap = Collections.synchronizedMap(new java.util.LinkedHashMap<>());
                                    categories.put(category, catMap);
                                }
                            }

                            for (int i = 0; i < items.length(); i++) {
                                JSONObject item = items.getJSONObject(i);
                                String link = item.optString(linkKey);
                                String label = item.optString(labelKey);
                                if (!link.isEmpty() && !label.isEmpty()) {
                                    catMap.put(link, label);
                                    globalCache.put(link, label);
                                }

                                // Parse icons if available
                                JSONObject iconObj = item.optJSONObject("icon");
                                if (iconObj != null) {
                                    String iconUrl = iconObj.optString("jalf_24x20_icon_uriref",
                                            iconObj.optString("jalf_22x19_icon_uriref",
                                                    iconObj.optString("jalf_20x20_icon_uriref",
                                                            iconObj.optString("jalf_20x20_icon_link", ""))));
                                    if (!iconUrl.isEmpty()) {
                                        if (!iconUrl.startsWith("http"))
                                            iconUrl = ApiConstants.BASE_URL + iconUrl;
                                        iconCache.put(link, iconUrl);
                                    }
                                }
                            }
                            notifyListeners();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing " + path, e);
                }
            }
        });
    }

    /**
     * Like {@link #fetch}, but also stores an alternative label (e.g. imperial) in
     * {@link #imperialCache}.
     * Used for heights and weights where both metric and imperial values are
     * available.
     */
    private void fetchWithBoth(String cookie, String suid, String path,
            String rootKey, String linkKey,
            String metricKey, String imperialKey, String category) {
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + path)
                .addHeader("Cookie", cookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch " + path, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        JSONObject root = new JSONObject(NetworkUtils.responseToString(r));
                        JSONArray items = root.optJSONArray(rootKey);
                        if (items != null) {
                            Map<String, String> catMap;
                            synchronized (categories) {
                                catMap = categories.get(category);
                                if (catMap == null) {
                                    catMap = Collections.synchronizedMap(new java.util.LinkedHashMap<>());
                                    categories.put(category, catMap);
                                }
                            }
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject item = items.getJSONObject(i);
                                String link = item.optString(linkKey);
                                String metric = item.optString(metricKey);
                                String imperial = item.optString(imperialKey);
                                if (!link.isEmpty() && !metric.isEmpty()) {
                                    catMap.put(link, metric);
                                    globalCache.put(link, metric);
                                }
                                if (!link.isEmpty() && !imperial.isEmpty()) {
                                    imperialCache.put(link, imperial);
                                }
                            }
                            notifyListeners();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing " + path, e);
                }
            }
        });
    }

    private synchronized void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public synchronized void addListener(Runnable listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * Resolves a REST link to its descriptive label. Returns null if not cached.
     */
    public String resolve(String link) {
        if (link == null)
            return null;
        return globalCache.get(link);
    }

    /**
     * Resolves a REST link to its imperial label (only for heights/weights).
     * Returns null if not available.
     */
    public String resolveImperial(String link) {
        if (link == null)
            return null;
        String imperial = imperialCache.get(link);
        return (imperial != null && !imperial.isEmpty()) ? imperial : globalCache.get(link);
    }

    /** Resolves a REST link to its icon URL. Returns null if not cached. */
    public String resolveIcon(String link) {
        if (link == null)
            return null;
        return iconCache.get(link);
    }

    /** Returns all metadata for a specific category. */
    public Map<String, String> getCategory(String category) {
        return categories.get(category);
    }
}
