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

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import io.github.kgelinas.jalfnotifier.sync.SettingsSyncManager;

public class JalfNotificationTask {

    private static final String TAG = "JALFWorker";
    private static final String CHAT_CHANNEL_ID = "jal_chat_notifications";
    private static final String ONLINE_CHANNEL_ID = "jal_online_notifications";
    private static final String EVENT_CHANNEL_ID = "jal_event_notifications";

    private static final OkHttpClient sharedClient = JalfNotifierApplication.httpClient();

    private final Context context;
    private String USER_ID;
    private String SUID;
    private String FULL_COOKIE;
    private String USERNAME;
    private String PASSWORD;

    public JalfNotificationTask(Context context) {
        this.context = context.getApplicationContext();
        loadCredentials();
    }

    private void loadCredentials() {
        SecurePrefs secure = SecurePrefs.get(context);
        USER_ID = getAppPrefs().getString(ApiConstants.KEY_USER_ID, "");
        SUID = secure.getString(ApiConstants.KEY_SUID, "");
        FULL_COOKIE = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        USERNAME = secure.getString(ApiConstants.KEY_USERNAME, "");
        PASSWORD = secure.getString(ApiConstants.KEY_PASSWORD, "");
    }

    private AppPrefs getAppPrefs() {
        return AppPrefs.getInstance(context);
    }

    public boolean execute(boolean scheduleNextRun) {
        AppLogger.log("Task: Starting sync");
        Log.d(TAG, "Task: Starting sync");

        if (USER_ID.isEmpty() || SUID.isEmpty() || FULL_COOKIE.isEmpty()) {
            AppLogger.log("Task: Error - Missing credentials");
            return false; // failure
        }

        MetadataManager.getInstance().init(context);
        migrateNotificationSettings();
        checkOnlineNotifications();

        AppLogger.log("Task: Fetching data...");
        String responseNew = performApiCheck(
                ApiConstants.BASE_URL + "/rest/users/" + USER_ID + "/conversations/new?offset=0&count=10");
        String responseActive = performApiCheck(
                ApiConstants.BASE_URL + "/rest/users/" + USER_ID + "/conversations/active?offset=0&count=10");
        String responseNotifs = performApiCheck(ApiConstants.BASE_URL + "/rest/users/" + USER_ID + "/notifications");

        if ("403".equals(responseNew) || "403".equals(responseActive) || "403".equals(responseNotifs)) {
            AppLogger.log("Task: Session expired (403), attempting auto-login");
            if (USERNAME.isEmpty() || PASSWORD.isEmpty())
                return false;

            boolean loggedIn = attemptAutoLogin(USERNAME, PASSWORD);
            if (loggedIn) {
                loadCredentials();
                AppLogger.log("Task: Auto-login successful, retrying fetch");
                responseNew = performApiCheck(
                        ApiConstants.BASE_URL + "/rest/users/" + USER_ID + "/conversations/new?offset=0&count=10");
                responseActive = performApiCheck(
                        ApiConstants.BASE_URL + "/rest/users/" + USER_ID + "/conversations/active?offset=0&count=10");
                responseNotifs = performApiCheck(ApiConstants.BASE_URL + "/rest/users/" + USER_ID + "/notifications");
            } else {
                AppLogger.log("Task: Auto-login failed");
                return false;
            }
        }

        AppPrefs prefs = getAppPrefs();
        Set<String> notifiedLinks;
        try {
            notifiedLinks = prefs.getStringSet(ApiConstants.KEY_NOTIFIED_LINKS, new HashSet<>());
        } catch (ClassCastException e) {
            notifiedLinks = new HashSet<>();
            prefs.edit().remove(ApiConstants.KEY_NOTIFIED_LINKS).apply();
        }

        Set<String> filteredSexes;
        try {
            filteredSexes = prefs.getStringSet(ApiConstants.KEY_FILTERED_SEXES, null);
        } catch (ClassCastException e) {
            filteredSexes = null;
            prefs.edit().remove(ApiConstants.KEY_FILTERED_SEXES).apply();
        }

        Set<String> salutationSexes;
        try {
            salutationSexes = prefs.getStringSet(ApiConstants.KEY_SALUTATION_SEXES, null);
        } catch (ClassCastException e) {
            salutationSexes = null;
            prefs.edit().remove(ApiConstants.KEY_SALUTATION_SEXES).apply();
        }

        JSONObject displayData = new JSONObject();
        try {
            displayData.put("new", processConversationsForUI(responseNew));
            displayData.put("active", processConversationsForUI(responseActive));
            displayData.put("events", processEventsForUI(responseNotifs));

            handleChatNotifications(responseNew, notifiedLinks, filteredSexes, true);
            handleChatNotifications(responseActive, notifiedLinks, filteredSexes, false);
            handleEventNotifications(responseNotifs, notifiedLinks, salutationSexes);
        } catch (Exception e) {
            AppLogger.log("Task: Build error: " + e.getMessage());
        }

        if (notifiedLinks.size() > 100) {
            java.util.List<String> list = new java.util.ArrayList<>(notifiedLinks);
            java.util.Collections.sort(list);
            while (list.size() > 100) {
                String oldest = list.remove(0);
                notifiedLinks.remove(oldest);
            }
        }

        prefs.edit()
                .putString(ApiConstants.KEY_CACHED_MESSAGES_V2, displayData.toString())
                .putStringSet(ApiConstants.KEY_NOTIFIED_LINKS, new HashSet<>(notifiedLinks))
                .apply();

        checkAutoBackup();

        if (scheduleNextRun) {
            scheduleNextRun(false);
        }
        return true;
    }

    private JSONArray processConversationsForUI(String responseData) {
        JSONArray array = new JSONArray();
        if (responseData == null || "403".equals(responseData))
            return array;
        try {
            JSONObject json = new JSONObject(responseData);
            if (!json.has("conversations"))
                return array;
            JSONArray conversations = json.getJSONArray("conversations");
            for (int i = 0; i < conversations.length(); i++) {
                JSONObject conv = conversations.getJSONObject(i);
                if (conv.optInt("unread_messages_count", 0) <= 0)
                    continue;

                String link = conv.optString("conversation_link", "");
                JSONArray members = conv.optJSONArray("other_members");
                JSONObject firstMember = (members != null && members.length() > 0)
                        ? members.getJSONObject(0)
                        : null;
                String senderName = (firstMember != null)
                        ? firstMember.optString("name", context.getString(R.string.someone))
                        : context.getString(R.string.someone);



                String sexDesc = "";
                String sexIcon = "";
                if (firstMember != null && firstMember.has("sex_link")) {
                    String sexLink = firstMember.getString("sex_link");
                    sexDesc = MetadataManager.getInstance().resolve(sexLink);
                    if (sexDesc == null)
                        sexDesc = "";
                    sexIcon = MetadataManager.getInstance().resolveIcon(sexLink);
                    if (sexIcon == null)
                        sexIcon = "";
                }

                String messageText = context.getString(R.string.new_message);
                String posted = "";
                if (conv.has("last_message")) {
                    JSONObject lastMsg = conv.getJSONObject("last_message");
                    messageText = lastMsg.optJSONObject("content") != null
                            ? lastMsg.getJSONObject("content").optString("text", context.getString(R.string.new_message))
                            : context.getString(R.string.new_message);

                    posted = lastMsg.optString("posted", "");
                }

                JSONObject msgObj = new JSONObject();
                msgObj.put("sender", senderName);
                msgObj.put("sex", sexDesc);
                msgObj.put("sex_icon", sexIcon);
                msgObj.put("text", messageText);
                msgObj.put("posted", posted);
                msgObj.put("link", link);
                array.put(msgObj);
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "Error in processConversationsForUI", e);
        }
        return array;
    }

    private JSONArray processEventsForUI(String responseData) {
        JSONArray array = new JSONArray();
        if (responseData == null || "403".equals(responseData))
            return array;
        try {
            JSONObject json = new JSONObject(responseData);
            if (!json.has("notifications"))
                return array;
            JSONArray notifications = json.getJSONArray("notifications");
            for (int i = 0; i < notifications.length(); i++) {
                JSONObject n = notifications.getJSONObject(i);
                String userLink = n.optString("user_link", "");
                String userName = n.optString("user_name", context.getString(R.string.someone));

                String when = n.optString("when_added", "");

                String why = n.optString("why", "");
                JSONObject refData = n.optJSONObject("reference_data");
                String refType = (refData != null) ? refData.optString("type", "") : "";
                int type = n.optInt("type", -1);
                String text = context.getString(R.string.sent_notification);

                String secondaryImage = null;

                if ("favorite_of".equals(why) || type == 22) {
                    text = context.getString(R.string.added_favorite);

                } else if ("your_favorite".equals(why) || type == 6) {
                    text = context.getString(R.string.added_new_photo);

                    if (refData != null)
                        secondaryImage = refData.optString("thumbnail_uri", null);
                } else if (type == 20 || "posted".equals(why) && "poke".equals(refType)) {
                    if (refData != null) {
                        text = context.getString(R.string.sent_poke);

                    }
                } else if ("author".equals(why)) {
                    if (type == 3) {
                        text = context.getString(R.string.would_recreate_photo);
                    } else if ("photo".equals(refType)) {
                        text = context.getString(R.string.liked_photo);

                    }
                    if (refData != null)
                        secondaryImage = refData.optString("thumbnail_uri", null);
                } else if ("visit".equals(why)) {
                    text = context.getString(R.string.visited_profile);

                }

                JSONObject eventObj = new JSONObject();
                eventObj.put("sender", userName);
                eventObj.put("text", text);
                eventObj.put("posted", when);
                eventObj.put("user_link", userLink);
                eventObj.put("secondary_image", secondaryImage);
                array.put(eventObj);
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "Error in processEventsForUI", e);
        }
        return array;
    }

    private void handleChatNotifications(String responseData, Set<String> notifiedLinks,
            Set<String> filteredSexes, boolean isNew) {
        if (responseData == null || "403".equals(responseData))
            return;
        try {
            JSONObject json = new JSONObject(responseData);
            if (!json.has("conversations"))
                return;
            JSONArray conversations = json.getJSONArray("conversations");

            for (int i = 0; i < conversations.length(); i++) {
                JSONObject conv = conversations.getJSONObject(i);
                if (conv.optInt("unread_messages_count", 0) <= 0)
                    continue;

                String link = conv.optString("conversation_link", "");
                JSONArray members = conv.optJSONArray("other_members");
                JSONObject firstMember = (members != null && members.length() > 0)
                        ? members.getJSONObject(0)
                        : null;
                String senderName = (firstMember != null)
                        ? firstMember.optString("name", context.getString(R.string.someone))
                        : context.getString(R.string.someone);


                String otherUserId = "";
                String userLink = "";
                if (firstMember != null && firstMember.has("user_link")) {
                    userLink = firstMember.getString("user_link");
                    String[] parts = userLink.split("/");
                    if (parts.length >= 4)
                        otherUserId = parts[3];
                }

                boolean allowed = false;
                if (!userLink.isEmpty() && isContactTypeNotificationAllowed(userLink, "chat")) {
                    allowed = true;
                }

                if (!allowed) {
                    String sexDesc = "";
                    if (firstMember != null && firstMember.has("sex_link")) {
                        sexDesc = MetadataManager.getInstance().resolve(firstMember.getString("sex_link"));
                        if (sexDesc == null)
                            sexDesc = "";
                    }
                    if (sexDesc.isEmpty() || SettingsViewModel.getNotificationFilter(getAppPrefs().getRaw(), sexDesc, "chat")) {
                        allowed = true;
                    }
                }

                if (!allowed) {
                    continue;
                }

                String avatarUrl = "";
                if (firstMember != null && firstMember.has("thumbnail_uri")) {
                    avatarUrl = firstMember.getString("thumbnail_uri");
                    if (!avatarUrl.startsWith("http"))
                        avatarUrl = ApiConstants.BASE_URL + avatarUrl;
                }

                String sexIcon = "";
                if (firstMember != null && firstMember.has("sex_link")) {
                    sexIcon = MetadataManager.getInstance().resolveIcon(firstMember.getString("sex_link"));
                }

                String messageText = context.getString(R.string.new_message);
                String posted = "";
                if (conv.has("last_message")) {
                    JSONObject lastMsg = conv.getJSONObject("last_message");
                    messageText = lastMsg.optJSONObject("content") != null
                            ? lastMsg.getJSONObject("content").optString("text", context.getString(R.string.new_message))
                            : context.getString(R.string.new_message);

                    posted = lastMsg.optString("posted", "");
                }

                String notificationKey = link + "_" + posted;
                boolean alreadyNotified = false;
                for (String entry : notifiedLinks) {
                    if (entry.equals(notificationKey) || entry.endsWith(":" + notificationKey)) {
                        alreadyNotified = true;
                        break;
                    }
                }
                if (!alreadyNotified) {
                    showChatNotification(context, senderName, messageText, link, otherUserId, avatarUrl, sexIcon);
                    java.util.Iterator<String> it = notifiedLinks.iterator();
                    while (it.hasNext()) {
                        String entry = it.next();
                        if (entry.equals(notificationKey) || entry.endsWith(":" + notificationKey)) {
                            it.remove();
                        }
                    }
                    notifiedLinks.add(System.currentTimeMillis() + ":" + notificationKey);
                }

            }
        } catch (Exception e) {
            AppLogger.log(TAG, "Error in handleChatNotifications", e);
        }
    }

    private void handleEventNotifications(String responseData, Set<String> notifiedLinks,
            Set<String> salutationSexes) {
        if (responseData == null || "403".equals(responseData))
            return;
        try {
            JSONObject json = new JSONObject(responseData);
            if (!json.has("notifications"))
                return;
            JSONArray notifications = json.getJSONArray("notifications");
            for (int i = 0; i < notifications.length(); i++) {
                JSONObject n = notifications.getJSONObject(i);
                String notifId = n.optString("notification_link", "");
                String userLink = n.optString("user_link", "");
                String userName = n.optString("user_name", context.getString(R.string.someone));


                String why = n.optString("why", "");
                int type = n.optInt("type", -1);
                JSONObject refData = n.optJSONObject("reference_data");
                String refType = (refData != null) ? refData.optString("type", "") : "";

                // Detect why from message if missing or generic
                if (why.isEmpty() || "notif".equals(why)) {
                    String b = n.optString("message", "").toLowerCase();
                    if (b.contains("visité")) why = "visit";
                    else if (b.contains("salu") || b.contains("poke")) why = "salutation";
                    else if (b.contains("favori")) why = "favorite_of";
                    else if (b.contains("photo") && b.contains("ajouté")) why = "your_favorite";
                    else if (b.contains("photo") && (b.contains("aimé") || b.contains("like"))) why = "author";
                }

                String notifType = "visit";
                if ("favorite_of".equals(why) || type == 22) {
                    notifType = "favorite";
                } else if ("your_favorite".equals(why) || type == 6) {
                    notifType = "photo";
                } else if (type == 20 || "salutation".equals(why) || "posted".equals(why)) {
                    notifType = "poke";
                } else if ("author".equals(why)) {
                    notifType = "photo";
                } else if ("visit".equals(why) || type == -2) {
                    notifType = "visit";
                }

                boolean allowed = false;
                if (!userLink.isEmpty() && isContactTypeNotificationAllowed(userLink, notifType)) {
                    allowed = true;
                }

                if (!allowed) {
                    if (!userLink.isEmpty()) {
                        String sexDesc = getUserSexDescription(userLink);
                        if (sexDesc.isEmpty() || SettingsViewModel.getNotificationFilter(getAppPrefs().getRaw(), sexDesc, notifType)) {
                            allowed = true;
                        }
                    } else {
                        allowed = true;
                    }
                }

                if (!allowed) {
                    continue;
                }

                String text = context.getString(R.string.sent_notification);


                if ("favorite_of".equals(why) || type == 22) {
                    text = context.getString(R.string.added_favorite);
                } else if ("your_favorite".equals(why) || type == 6) {
                    text = context.getString(R.string.added_new_photo);
                } else if (type == 20 || "salutation".equals(why) || "posted".equals(why)) {
                    text = context.getString(R.string.sent_poke);
                } else if ("author".equals(why)) {
                    if (type == 3) {
                        text = context.getString(R.string.would_recreate_photo);
                    } else {
                        text = context.getString(R.string.liked_photo);
                    }
                } else if ("visit".equals(why) || type == -2) {
                    text = context.getString(R.string.visited_profile);
                }

                boolean alreadyNotified = false;
                for (String entry : notifiedLinks) {
                    if (entry.equals(notifId) || entry.endsWith(":" + notifId)) {
                        alreadyNotified = true;
                        break;
                    }
                }
                if (!alreadyNotified) {
                    showNotification(context, EVENT_CHANNEL_ID, context.getString(R.string.event_notifications),
                            context.getString(R.string.new_event), userName + " " + text, userLink);

                    java.util.Iterator<String> it = notifiedLinks.iterator();
                    while (it.hasNext()) {
                        String entry = it.next();
                        if (entry.equals(notifId) || entry.endsWith(":" + notifId)) {
                            it.remove();
                        }
                    }
                    notifiedLinks.add(System.currentTimeMillis() + ":" + notifId);
                }
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "Error in handleEventNotifications", e);
        }
    }

    /**
     * Resolves a user's sex description (e.g. "Homme", "Femme") using cache or
     * synchronous fetch.
     */
    private String getUserSexDescription(String userLink) {
        return ProfileCacheManager.getInstance().getUserSexDescription(context, sharedClient, userLink);
    }

    private boolean isContactTypeNotificationAllowed(String userLink, String notifType) {
        if (userLink == null || userLink.isEmpty()) {
            return false;
        }

        String targetUserId = StringUtils.extractUserIdFromLink(userLink);
        if (targetUserId.isEmpty()) {
            return false;
        }

        AppPrefs prefs = getAppPrefs();

        // 1. Check Notified category
        Set<String> notifiedUsers = prefs.getStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS, java.util.Collections.emptySet());
        for (String link : notifiedUsers) {
            if (targetUserId.equals(StringUtils.extractUserIdFromLink(link))) {
                String key = "notif_filter_ct_notified_" + notifType.toLowerCase(Locale.ROOT);
                if (prefs.getBoolean(key, true)) {
                    return true;
                }
                break;
            }
        }

        // 2. Check Contacts (Favorites) category
        Set<String> favorites = prefs.getStringSet("CACHED_FAVORITES_LINKS", java.util.Collections.emptySet());
        for (String link : favorites) {
            if (targetUserId.equals(StringUtils.extractUserIdFromLink(link))) {
                String key = "notif_filter_ct_contacts_" + notifType.toLowerCase(Locale.ROOT);
                if (prefs.getBoolean(key, true)) {
                    return true;
                }
                break;
            }
        }

        // 3. Check Bookmarks category
        Set<String> bookmarks = prefs.getStringSet("CACHED_BOOKMARKS_LINKS", java.util.Collections.emptySet());
        for (String link : bookmarks) {
            if (targetUserId.equals(StringUtils.extractUserIdFromLink(link))) {
                String key = "notif_filter_ct_bookmarks_" + notifType.toLowerCase(Locale.ROOT);
                if (prefs.getBoolean(key, true)) {
                    return true;
                }
                break;
            }
        }

        return false;
    }

    private void migrateNotificationSettings() {
        AppPrefs prefs = getAppPrefs();
        if (!prefs.contains(ApiConstants.KEY_ONLINE_NOTIF_USERS)
                && prefs.contains(ApiConstants.KEY_FAVORITE_ONLINE_NOTIFS)) {
            Set<String> oldSet;
            try {
                oldSet = prefs.getStringSet(ApiConstants.KEY_FAVORITE_ONLINE_NOTIFS, new HashSet<>());
            } catch (ClassCastException e) {
                oldSet = new HashSet<>();
                prefs.edit().remove(ApiConstants.KEY_FAVORITE_ONLINE_NOTIFS).apply();
            }
            if (!oldSet.isEmpty()) {
                prefs.edit().putStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS, new HashSet<>(oldSet)).apply();
                AppLogger.log("Task: Migrated " + oldSet.size() + " notification settings");
            }
        }
    }

    private void checkOnlineNotifications() {
        AppPrefs prefs = getAppPrefs();
        Set<String> subscribedUsers;
        try {
            subscribedUsers = prefs.getStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS, new HashSet<>());
        } catch (ClassCastException e) {
            subscribedUsers = new HashSet<>();
            prefs.edit().remove(ApiConstants.KEY_ONLINE_NOTIF_USERS).apply();
        }
        if (subscribedUsers.isEmpty()) {
            // Even if no specific subs, we might have pending messages to check
            try {
                String pendingJson = prefs.getString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, "{}");
                org.json.JSONObject pendingMap = new org.json.JSONObject(pendingJson);
                if (pendingMap.length() == 0)
                    return;
            } catch (Exception e) {
                AppLogger.log(TAG, "Error parsing pending auto messages in checkOnlineNotifications", e);
                return;
            }
        }

        AppLogger.log("Task: Checking online status for " + subscribedUsers.size() + " users");

        // 1. Bulk check via Favorites (Efficient)
        Set<String> remainingToCheck = new HashSet<>(subscribedUsers);

        // Also add users with pending messages to the check list
        try {
            String pendingJson = prefs.getString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, "{}");
            org.json.JSONObject pendingMap = new org.json.JSONObject(pendingJson);
            java.util.Iterator<String> keys = pendingMap.keys();
            while (keys.hasNext()) {
                remainingToCheck.add("/rest/users/" + keys.next());
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "Error adding pending auto messages to check list", e);
        }

        String favResponse = performApiCheck(ApiConstants.BASE_URL + "/rest/users/" + USER_ID + "/favorites");
        if (favResponse != null && !favResponse.equals("403")) {
            try {
                JSONObject json = new JSONObject(favResponse);
                JSONArray items = json.getJSONArray("items");
                Set<String> favoritesSet = new HashSet<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String userLink = item.optString("user_link", "");
                    if (!userLink.isEmpty()) {
                        favoritesSet.add(userLink);
                    }
                    if (remainingToCheck.contains(userLink)) {
                        remainingToCheck.remove(userLink);
                        if (item.optBoolean("online", false) || item.optBoolean("is_online", false)) {
                            notifyIfOnline(userLink, item.optString("description", item.optString("name", "Unknown")));
                        }
                    }
                }
                prefs.edit().putStringSet("CACHED_FAVORITES_LINKS", favoritesSet).apply();
            } catch (Exception e) {
                AppLogger.log(TAG, "Error checking favorites online status", e);
            }
        }

        // 2. Individual check for non-favorites (using /status endpoint)
        if (!remainingToCheck.isEmpty()) {
            String namesJson = prefs.getString(ApiConstants.KEY_ONLINE_NOTIF_NAMES, "{}");
            JSONObject namesObj;
            try {
                namesObj = new JSONObject(namesJson);
            } catch (Exception e) {
                AppLogger.log(TAG, "Error parsing online notification names JSON, resetting", e);
                namesObj = new JSONObject();
            }
            boolean namesUpdated = false;

            int count = 0;
            for (String userLink : remainingToCheck) {
                if (count++ > 20)
                    break;

                String statusResponse = performApiCheck(ApiConstants.BASE_URL + userLink + "/status");
                if (statusResponse != null && !statusResponse.equals("403")) {
                    try {
                        JSONObject statusJson = new JSONObject(statusResponse);
                        if (statusJson.optInt("online", 0) == 1) {
                            String nameToUse = namesObj.optString(userLink, "");

                            // If name is missing, try cache first
                            if (nameToUse.isEmpty()) {
                                String userId = StringUtils.extractUserIdFromLink(userLink);
                                JSONObject cached = ProfileCacheManager.getInstance().getProfile(userId);
                                if (cached != null) {
                                    nameToUse = cached.optString("name", cached.optString("pseudo", ""));
                                    if (!nameToUse.isEmpty()) {
                                        namesObj.put(userLink, nameToUse);
                                        namesUpdated = true;
                                    }
                                }
                            }

                            // If still missing, fetch full profile once to get it
                            if (nameToUse.isEmpty()) {
                                String profileResponse = performApiCheck(ApiConstants.BASE_URL + userLink);
                                if (profileResponse != null && !profileResponse.equals("403")) {
                                    JSONObject profile = new JSONObject(profileResponse);
                                    nameToUse = profile.optString("name", "");
                                    if (!nameToUse.isEmpty()) {
                                        namesObj.put(userLink, nameToUse);
                                        namesUpdated = true;
                                        // Also store in main profile cache
                                        ProfileCacheManager.getInstance().putProfile(context,
                                                StringUtils.extractUserIdFromLink(userLink), profile);
                                    }
                                }
                            }

                            if (nameToUse.isEmpty()) {
                                nameToUse = context.getString(R.string.someone) + " (" + userLink.replace("/rest/users/", "") + ")";

                            }

                            notifyIfOnline(userLink, nameToUse);
                        }
                    } catch (Exception e) {
                        AppLogger.log(TAG, "Error checking status for user: " + userLink, e);
                    }
                }
            }
            if (namesUpdated) {
                prefs.edit().putString(ApiConstants.KEY_ONLINE_NOTIF_NAMES, namesObj.toString()).apply();
            }
        }
    }

    private void notifyIfOnline(String userLink, String name) {
        AppPrefs prefs = getAppPrefs();
        String lastNotifiedKey = "LAST_ONLINE_NOTIF_V2_" + userLink; // Using V2 to reset due to logic change
        long lastNotified = prefs.getLong(lastNotifiedKey, 0);

        if (System.currentTimeMillis() - lastNotified > TimeUnit.HOURS.toMillis(2)) {
            showNotification(context, ONLINE_CHANNEL_ID, context.getString(R.string.chat_notifications),
                    context.getString(R.string.user_online_title), 
                    context.getString(R.string.user_online_message, name), userLink);
            prefs.edit().putLong(lastNotifiedKey, System.currentTimeMillis()).apply();
        }


        checkAndSendAutoMessage(userLink);
    }

    public void checkAndSendAutoMessage(String userLink) {
        AppPrefs prefs = getAppPrefs();
        String userId = userLink.replace("/rest/users/", "");
        try {
            String pendingJson = prefs.getString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, "{}");
            JSONObject pendingMap = new JSONObject(pendingJson);

            if (pendingMap.has(userId)) {
                JSONObject autoMsg = pendingMap.getJSONObject(userId);
                String text = autoMsg.getString("text");
                String convoLink = autoMsg.optString("convoLink", "");

                if (!convoLink.isEmpty()) {
                    boolean success = sendAutoMessage(convoLink, text);
                    if (success) {
                        pendingMap.remove(userId);
                        prefs.edit().putString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, pendingMap.toString()).apply();
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "Error processing auto-message for " + userId, e);
        }
    }

    private boolean sendAutoMessage(String convoLink, String text) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("text", text);
            payload.put("ephemeral", false); // Auto-messages are not ephemeral by default for reliability
        } catch (Exception e) {
            AppLogger.log(TAG, "Error building sendAutoMessage payload", e);
        }

        okhttp3.RequestBody body = okhttp3.RequestBody.create(payload.toString(),
                okhttp3.MediaType.parse("application/vnd.jalf.convo.newmessage.text+json"));

        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + convoLink)
                .post(body)
                .addHeader("Cookie", FULL_COOKIE)
                .addHeader("x-csrftoken", SUID)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        try (Response response = sharedClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            AppLogger.log(TAG, "Failed to send auto-message background", e);
            return false;
        }
    }

    // Removed prefetchSexes, getSexDescription, getSexIcon as they are now handled
    // by MetadataManager

    private void scheduleNextRun(boolean reset) {
        AppPrefs prefs = getAppPrefs();
        int currentFibIndex = prefs.getInt(ApiConstants.KEY_FIB_INDEX, 0);
        int[] fib = { 1, 1, 2, 3, 5, 8, 13 };
        if (reset)
            currentFibIndex = 0;
        else if (currentFibIndex < fib.length - 1)
            currentFibIndex++;

        int delayMinutes = fib[currentFibIndex];
        long nextRefreshTime = System.currentTimeMillis()
                + TimeUnit.MINUTES.toMillis(delayMinutes);

        prefs.edit()
                .putInt(ApiConstants.KEY_FIB_INDEX, currentFibIndex)
                .putLong(ApiConstants.KEY_NEXT_REFRESH_TIME, nextRefreshTime)
                .putInt(ApiConstants.KEY_CURRENT_DELAY_MINS, delayMinutes)
                .apply();

        OneTimeWorkRequest nextRequest = new OneTimeWorkRequest.Builder(
                JalfNotificationWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag("JAL_POLLING_DYNAMIC")
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "JALNotifierDynamicWork",
                ExistingWorkPolicy.REPLACE,
                nextRequest);
    }

    private String performApiCheck(String apiUrl) {
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Cookie", FULL_COOKIE)
                .addHeader("x-csrftoken", SUID)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        try (Response response = sharedClient.newCall(request).execute()) {
            if (response.code() == 403)
                return "403";
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        } catch (IOException e) {
        }
        return null;
    }

    private boolean attemptAutoLogin(String username, String password) {
        okhttp3.OkHttpClient loginClient = new okhttp3.OkHttpClient.Builder()
                .cookieJar(new okhttp3.CookieJar() {
                    private final java.util.HashMap<String, java.util.List<okhttp3.Cookie>> store = new java.util.HashMap<>();

                    @Override
                    public void saveFromResponse(okhttp3.HttpUrl url,
                            java.util.List<okhttp3.Cookie> cookies) {
                        store.put(url.host(), cookies);
                    }

                    @Override
                    public java.util.List<okhttp3.Cookie> loadForRequest(
                            okhttp3.HttpUrl url) {
                        java.util.List<okhttp3.Cookie> c = store.get(url.host());
                        return c != null ? c : java.util.Collections.emptyList();
                    }
                })
                .build();

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("Username", username);
        params.put("Password", password);
        params.put("what", "start_page");

        okhttp3.RequestBody formBody = NetworkUtils.createIsoFormBody(params);

        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/connect")
                .post(formBody)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        try (Response response = loginClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                okhttp3.HttpUrl parsedUrl = okhttp3.HttpUrl.parse(ApiConstants.BASE_URL + "/");
                java.util.List<okhttp3.Cookie> cookies = loginClient.cookieJar().loadForRequest(parsedUrl);

                String newSuid = "";
                StringBuilder newCookieBuilder = new StringBuilder();
                for (okhttp3.Cookie cookie : cookies) {
                    newCookieBuilder.append(cookie.name()).append("=")
                            .append(cookie.value()).append("; ");
                    if ("SUID".equals(cookie.name()))
                        newSuid = cookie.value();
                }

                if (!newSuid.isEmpty()) {
                    SecurePrefs.get(context)
                            .putString(ApiConstants.KEY_SUID, newSuid)
                            .putString(ApiConstants.KEY_FULL_COOKIE, newCookieBuilder.toString());
                    return true;
                }
            }
        } catch (IOException e) {
        }
        return false;
    }

    public static void showNotification(Context context, String channelId, String channelName,
            String title, String message, String userLink) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (userLink != null) {
            intent.putExtra(ApiConstants.EXTRA_USER_LINK, userLink);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    public static void showChatNotification(Context context, String senderName, String messageText,
            String conversationLink,
            String otherUserId, String avatarUrl, String sexIconUrl) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHAT_CHANNEL_ID, context.getString(R.string.chat_notifications), NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(ApiConstants.EXTRA_CONVERSATION_LINK, conversationLink);
        intent.putExtra(ApiConstants.EXTRA_OTHER_USER_ID, otherUserId);
        intent.putExtra(ApiConstants.EXTRA_OTHER_NAME, senderName);
        intent.putExtra(ApiConstants.EXTRA_AVATAR_URL, avatarUrl);
        intent.putExtra(ApiConstants.EXTRA_SEX_ICON_URL, sexIconUrl);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Person sender = new Person.Builder()
                .setName(senderName)
                .build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(sender)
                .addMessage(messageText, System.currentTimeMillis(), sender)
                .setGroupConversation(false);

        RemoteInput remoteInput = new RemoteInput.Builder(ApiConstants.EXTRA_REMOTE_INPUT_KEY)
                .setLabel(context.getString(R.string.action_reply))
                .build();

        Intent replyIntent = new Intent(context, ReplyReceiver.class);
        replyIntent.setAction(ApiConstants.ACTION_REPLY);
        replyIntent.putExtra(ApiConstants.EXTRA_CONVERSATION_LINK, conversationLink);

        String convoIdStr = StringUtils.extractNumericId(conversationLink);
        int notificationId = convoIdStr.isEmpty() ? conversationLink.hashCode() : convoIdStr.hashCode();

        PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send, context.getString(R.string.action_reply), replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .build();

        Intent readIntent = new Intent(context, MarkAsReadReceiver.class);
        readIntent.setAction(ApiConstants.ACTION_MARK_AS_READ);
        readIntent.putExtra(ApiConstants.EXTRA_CONVERSATION_LINK, conversationLink);
        PendingIntent readPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                readIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action readAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_agenda, context.getString(R.string.action_mark_as_read), readPendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(messagingStyle)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setShortcutId(conversationLink)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .addAction(replyAction)
                .addAction(readAction)
                .setAutoCancel(true);

        notificationManager.notify(notificationId, builder.build());
    }

    private void checkAutoBackup() {
        AppPrefs prefs = getAppPrefs();
        boolean enabled = prefs.getBoolean(ApiConstants.KEY_AUTO_BACKUP, false);
        if (!enabled)
            return;

        String email = prefs.getString(ApiConstants.KEY_GOOGLE_ACCOUNT_EMAIL, null);
        if (email == null || email.isEmpty())
            return;

        long lastSync = prefs.getLong(ApiConstants.KEY_LAST_DRIVE_SYNC_TIME, 0);
        long now = System.currentTimeMillis();

        // Check if > 24 hours (86,400,000 ms)
        if (now - lastSync > 24 * 6 * 10 * 1000 * 24) { // Oops, let's use TimeUnit
            if (now - lastSync > TimeUnit.DAYS.toMillis(1)) {
                AppLogger.log(TAG, "Triggering automatic background backup to Google Drive");
                SettingsSyncManager.backupToDrive(context, email, new SettingsSyncManager.SyncCallback() {
                    @Override
                    public void onSuccess() {
                        prefs.edit().putLong(ApiConstants.KEY_LAST_DRIVE_SYNC_TIME, System.currentTimeMillis()).apply();
                        AppLogger.log(TAG, "Automatic backup successful");
                    }

                    @Override
                    public void onFailure(Exception e) {
                        AppLogger.log(TAG, "Automatic backup failed", e);
                    }
                });
            }
        }
    }
}
