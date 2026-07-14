package io.github.kgelinas.jalfnotifier.data;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Singleton manager that handles caching for user profiles and recent events.
 * Profiles have a 72-hour TTL for static data and a 2-minute TTL for online
 * status.
 * Events have a 2-minute TTL.
 */
public class ProfileCacheManager extends Object {
    private static final String TAG = "ProfileCacheManager";
    private static volatile ProfileCacheManager instance;

    private static final long PROFILE_TTL = TimeUnit.HOURS.toMillis(72);
    private static final long STATUS_TTL = 0; // Never cache online status
    private static final long EVENTS_TTL = TimeUnit.MINUTES.toMillis(2);

    private final Map<String, CachedProfile> profileCache = new HashMap<>();

    private JSONArray cachedNotifications = new JSONArray();
    private JSONArray cachedLookers = new JSONArray();
    private JSONArray cachedPokes = new JSONArray();
    private JSONArray cachedOutgoingEvents = new JSONArray();
    private long lastEventsUpdate = 0;
    private long lastOutgoingEventsUpdate = 0;

    private ProfileCacheManager() {
        super();
    }

    public static ProfileCacheManager getInstance() {
        if (instance == null) {
            synchronized (ProfileCacheManager.class) {
                if (instance == null) {
                    instance = new ProfileCacheManager();
                }
            }
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (context == null) return;
        profileCache.clear();
        cachedNotifications = new JSONArray();
        cachedLookers = new JSONArray();
        cachedPokes = new JSONArray();
        cachedOutgoingEvents = new JSONArray();
        lastEventsUpdate = 0;
        lastOutgoingEventsUpdate = 0;

        SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String userId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        String eventsKey = ApiConstants.KEY_CACHED_EVENTS + (userId.isEmpty() ? "" : "_" + userId);
        String profilesKey = ApiConstants.KEY_CACHED_PROFILES + (userId.isEmpty() ? "" : "_" + userId);

        // Load Events
        try {
            String eventsJson = prefs.getString(eventsKey, "{}");
            JSONObject root = new JSONObject(eventsJson);
            cachedNotifications = root.optJSONArray("notifications");
            if (cachedNotifications == null)
                cachedNotifications = new JSONArray();
            cachedLookers = root.optJSONArray("lookers");
            if (cachedLookers == null)
                cachedLookers = new JSONArray();
            cachedPokes = root.optJSONArray("pokes");
            if (cachedPokes == null)
                cachedPokes = new JSONArray();
            cachedOutgoingEvents = root.optJSONArray("outgoing_events");
            if (cachedOutgoingEvents == null)
                cachedOutgoingEvents = new JSONArray();
            lastEventsUpdate = root.optLong("ts", 0);
            lastOutgoingEventsUpdate = root.optLong("outgoing_ts", 0);
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached events", e);
        }

        // Load Profiles
        try {
            String profilesJson = prefs.getString(profilesKey, "{}");
            JSONObject root = new JSONObject(profilesJson);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String uId = keys.next();
                JSONObject obj = root.getJSONObject(uId);
                profileCache.put(uId, new CachedProfile(
                        obj.getJSONObject("data"),
                        obj.getLong("ts"),
                        obj.optLong("status_ts", 0)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cached profiles", e);
        }
    }

    public synchronized void save(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String userId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        String eventsKey = ApiConstants.KEY_CACHED_EVENTS + (userId.isEmpty() ? "" : "_" + userId);
        String profilesKey = ApiConstants.KEY_CACHED_PROFILES + (userId.isEmpty() ? "" : "_" + userId);

        // Save Events
        try {
            JSONObject root = new JSONObject();
            root.put("notifications", cachedNotifications);
            root.put("lookers", cachedLookers);
            root.put("pokes", cachedPokes);
            root.put("outgoing_events", cachedOutgoingEvents);
            root.put("ts", lastEventsUpdate);
            root.put("outgoing_ts", lastOutgoingEventsUpdate);
            editor.putString(eventsKey, root.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error saving events to cache", e);
        }

        // Save Profiles
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, CachedProfile> entry : profileCache.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("data", entry.getValue().data);
                obj.put("ts", entry.getValue().timestamp);
                root.put(entry.getKey(), obj);
            }
            editor.putString(profilesKey, root.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error saving profiles to cache", e);
        }
        editor.apply();
    }

    public synchronized void clear(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String userId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        String eventsKey = ApiConstants.KEY_CACHED_EVENTS + (userId.isEmpty() ? "" : "_" + userId);
        String profilesKey = ApiConstants.KEY_CACHED_PROFILES + (userId.isEmpty() ? "" : "_" + userId);

        prefs.edit()
                .remove(ApiConstants.KEY_CACHED_EVENTS)
                .remove(ApiConstants.KEY_CACHED_PROFILES)
                .remove(eventsKey)
                .remove(profilesKey)
                .apply();

        profileCache.clear();
        cachedNotifications = new JSONArray();
        cachedLookers = new JSONArray();
        cachedPokes = new JSONArray();
        cachedOutgoingEvents = new JSONArray();
        lastEventsUpdate = 0;
        lastOutgoingEventsUpdate = 0;
    }

    public synchronized JSONArray getCachedNotifications() {
        return cachedNotifications;
    }

    public synchronized JSONArray getCachedLookers() {
        return cachedLookers;
    }

    public synchronized JSONArray getCachedPokes() {
        return cachedPokes;
    }

    public synchronized JSONArray getCachedOutgoingEvents() {
        return cachedOutgoingEvents;
    }

    public synchronized boolean areEventsStale() {
        return System.currentTimeMillis() - lastEventsUpdate > EVENTS_TTL;
    }

    public synchronized boolean areOutgoingEventsStale() {
        return System.currentTimeMillis() - lastOutgoingEventsUpdate > EVENTS_TTL;
    }

    public synchronized void markEventsStale() {
        this.lastEventsUpdate = 0;
        this.lastOutgoingEventsUpdate = 0;
    }

    public synchronized void putNotifications(JSONArray events) {
        this.cachedNotifications = events;
        this.lastEventsUpdate = System.currentTimeMillis();
    }

    public synchronized void putLookers(JSONArray events) {
        this.cachedLookers = events;
        this.lastEventsUpdate = System.currentTimeMillis();
    }

    public synchronized void putPokes(JSONArray events) {
        this.cachedPokes = events;
        this.lastEventsUpdate = System.currentTimeMillis();
    }

    public synchronized void putOutgoingEvents(JSONArray events) {
        this.cachedOutgoingEvents = events;
        this.lastOutgoingEventsUpdate = System.currentTimeMillis();
    }

    public synchronized JSONObject getProfile(String userId) {
        CachedProfile cached = profileCache.get(userId);
        if (cached != null) {
            return cached.data;
        }
        return null;
    }

    public synchronized boolean isProfileStale(String userId) {
        CachedProfile cached = profileCache.get(userId);
        return cached == null || System.currentTimeMillis() - cached.timestamp > PROFILE_TTL;
    }

    public synchronized boolean isStatusStale(String userId) {
        CachedProfile cached = profileCache.get(userId);
        return cached == null || System.currentTimeMillis() - cached.statusTimestamp > STATUS_TTL;
    }

    public synchronized void putProfile(Context context, String userId, JSONObject data) {
        if (context == null) return;
        JSONObject cacheData = null;
        if (data != null) {
            try {
                cacheData = new JSONObject(data.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error cloning profile for cache", e);
                cacheData = data; // fallback
            }
        }
        profileCache.put(userId, new CachedProfile(cacheData, System.currentTimeMillis(), System.currentTimeMillis()));
        save(context);
    }

    public synchronized void putProfileField(Context context, String userId, String key, Object value) {
        if (context == null) return;
        CachedProfile cached = profileCache.get(userId);
        if (cached != null) {
            try {
                cached.data.put(key, value);
                save(context);
            } catch (Exception e) {
                Log.e(TAG, "Error updating profile field", e);
            }
        }
    }

    public synchronized void updateStatus(Context context, String userId, int online, String lastConnected) {
        if (context == null) return;
        CachedProfile cached = profileCache.get(userId);
        if (cached != null) {
            try {
                cached.data.put("online", online);
                cached.data.put("is_online", online == 1);
                if (lastConnected != null) {
                    cached.data.put("last_connection", lastConnected);
                }
                cached.statusTimestamp = System.currentTimeMillis();
                save(context);
            } catch (Exception e) {
                Log.e(TAG, "Error updating status in cache", e);
            }
        }
    }

    /**
     * Resolves a user's sex description (e.g. "Homme", "Femme") using cache or
     * synchronous fetch.
     */
    public String getUserSexDescription(Context context, okhttp3.OkHttpClient client, String userLink) {
        if (context == null) return "";
        if (userLink == null || userLink.isEmpty())
            return "";

        String userId = extractUserIdFromLink(userLink);
        if (userId.isEmpty())
            return "";

        // 1. Try cache
        JSONObject profile = getProfile(userId);
        String sexLink = "";
        if (profile != null && profile.has("sex_link")) {
            try {
                sexLink = profile.getString("sex_link");
            } catch (Exception e) {
            }
        }

        // 2. Fallback to synchronous fetch (intended for background threads)
        if (sexLink.isEmpty()) {
            JSONObject fetched = fetchProfileSync(context, client, userLink, userId);
            if (fetched != null) {
                sexLink = fetched.optString("sex_link", "");
            }
        }

        // 3. Resolve link to label
        if (!sexLink.isEmpty()) {
            String resolved = MetadataManager.getInstance().resolve(sexLink);
            return (resolved != null) ? resolved : "";
        }
        return "";
    }

    /**
     * Resolves a username (pseudo) using cache or synchronous fetch.
     */
    public String resolveUsername(Context context, okhttp3.OkHttpClient client, String userLink) {
        if (context == null) return null;
        if (userLink == null || userLink.isEmpty())
            return null;

        String userId = extractUserIdFromLink(userLink);
        if (userId.isEmpty())
            return null;

        JSONObject profile = getCachedOrFetchProfileSync(context, client, userLink);
        if (profile != null) {
            return profile.optString("pseudo", profile.optString("name", null));
        }

        return null;
    }

    /**
     * Helper to get profile from cache or fetch it synchronously if missing/stale.
     */
    public synchronized JSONObject getCachedOrFetchProfileSync(Context context, okhttp3.OkHttpClient client, String userLink) {
        if (context == null) return null;
        String userId = extractUserIdFromLink(userLink);
        if (userId.isEmpty()) return null;

        // 1. Try fresh cache
        if (!isProfileStale(userId)) {
            return getProfile(userId);
        }

        // 2. Fetch synchronously
        return fetchProfileSync(context, client, userLink, userId);
    }

    private String extractUserIdFromLink(String userLink) {
        if (userLink == null) return "";
        String[] parts = userLink.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return "";
    }

    private JSONObject fetchProfileSync(Context context, okhttp3.OkHttpClient client, String userLink, String userId) {
        if (context == null) return null;
        SecurePrefs secure = SecurePrefs.get(context);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty() || suid.isEmpty()) {
            Log.w(TAG, "Cannot fetch profile for " + userId + ": Missing credentials");
            return null;
        }

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(ApiConstants.BASE_URL + userLink)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        Log.d(TAG, "Fetching profile synchronously for: " + userId + " (" + userLink + ")");
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String body = NetworkUtils.responseToString(response);
                if (body != null && (body.trim().startsWith("{") || body.trim().startsWith("["))) {
                    JSONObject newProfile = new JSONObject(body);
                    putProfile(context, userId, newProfile);
                    return newProfile;
                } else {
                    Log.e(TAG, "Profile response for " + userId + " is not JSON: " + (body != null ? body.substring(0, Math.min(body.length(), 100)) : "null"));
                }
            } else {
                Log.e(TAG, "Error fetching profile for " + userId + ": HTTP " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception fetching profile for " + userId, e);
        }
        return null;
    }

    private static class CachedProfile {
        final JSONObject data;
        final long timestamp;
        long statusTimestamp;

        CachedProfile(JSONObject data, long timestamp, long statusTimestamp) {
            this.data = data;
            this.timestamp = timestamp;
            this.statusTimestamp = statusTimestamp;
        }
    }
}
