package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ContactsWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ContactsRemoteViewsFactory(this.getApplicationContext());
    }

    private static class ContactsRemoteViewsFactory implements RemoteViewsFactory {
        private static final String TAG = "ContactsWidgetService";
        private final Context context;
        private final List<WidgetContact> contacts = new ArrayList<>();

        public ContactsRemoteViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            loadContacts();
        }

        @Override
        public void onDataSetChanged() {
            loadContacts();
        }

        @Override
        public void onDestroy() {
            contacts.clear();
        }

        @Override
        public int getCount() {
            return contacts.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= contacts.size()) {
                return null;
            }

            WidgetContact contact = contacts.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_widget_contact);

            views.setTextViewText(R.id.widget_contact_name, contact.name);
            views.setTextViewText(R.id.widget_contact_details, contact.details);

            // Online status indicator dot
            views.setViewVisibility(R.id.widget_online_dot, contact.isOnline ? View.VISIBLE : View.GONE);

            // Last connection time display
            if (contact.isOnline) {
                views.setTextViewText(R.id.widget_contact_last_connection, context.getString(R.string.last_active_format, context.getString(R.string.online)));
                views.setViewVisibility(R.id.widget_contact_last_connection, View.VISIBLE);
            } else if (contact.lastConnection != null && !contact.lastConnection.isEmpty() && !"need_vip".equalsIgnoreCase(contact.lastConnection)) {
                views.setTextViewText(R.id.widget_contact_last_connection, context.getString(R.string.last_active_format, contact.lastConnection));
                views.setViewVisibility(R.id.widget_contact_last_connection, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.widget_contact_last_connection, View.GONE);
            }

            // Synchronous avatar image loading
            boolean avatarLoaded = false;
            if (contact.avatarUrl != null && !contact.avatarUrl.isEmpty()) {
                String fullUrl = contact.avatarUrl;
                if (fullUrl.startsWith("/")) {
                    fullUrl = ApiConstants.BASE_URL + fullUrl;
                }
                try {
                    Bitmap bitmap = Glide.with(context.getApplicationContext())
                            .asBitmap()
                            .load(fullUrl)
                            .circleCrop()
                            .submit(96, 96)
                            .get(3, TimeUnit.SECONDS);

                    if (bitmap != null) {
                        views.setImageViewBitmap(R.id.widget_avatar, bitmap);
                        views.setViewVisibility(R.id.widget_avatar, View.VISIBLE);
                        views.setViewVisibility(R.id.widget_avatar_initials, View.GONE);
                        avatarLoaded = true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load widget avatar for " + contact.name, e);
                }
            }

            // Initials fallback if avatar wasn't loaded
            if (!avatarLoaded) {
                views.setViewVisibility(R.id.widget_avatar, View.GONE);
                views.setViewVisibility(R.id.widget_avatar_initials, View.VISIBLE);
                String initials = "U";
                if (contact.name != null && !contact.name.isEmpty()) {
                    initials = contact.name.substring(0, 1).toUpperCase();
                }
                views.setTextViewText(R.id.widget_avatar_initials, initials);
            }

            // Fill-in intent for item click (will trigger ACTION_CLICK in ContactsWidgetProvider)
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(ApiConstants.EXTRA_USER_LINK, contact.userLink);
            fillInIntent.putExtra(ApiConstants.EXTRA_OTHER_USER_ID, contact.otherUserId);
            fillInIntent.putExtra(ApiConstants.EXTRA_OTHER_NAME, contact.name);
            views.setOnClickFillInIntent(R.id.item_widget_root, fillInIntent);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null; // Standard loading view
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        private void loadContacts() {
            Log.d(TAG, "Loading contacts from SharedPreferences & CacheManager...");
            contacts.clear();

            SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
            String myUserId = prefs.getString(ApiConstants.KEY_USER_ID, "");

            Set<String> favorites = prefs.getStringSet("CACHED_FAVORITES_LINKS", new HashSet<>());
            Set<String> bookmarks = prefs.getStringSet("CACHED_BOOKMARKS_LINKS", new HashSet<>());
            Set<String> followed = prefs.getStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS, new HashSet<>());

            // Get active groups (default to all three if not configured)
            Set<String> defaultGroups = new HashSet<>();
            defaultGroups.add("favorite");
            defaultGroups.add("bookmark");
            defaultGroups.add("notified");
            Set<String> activeGroups = prefs.getStringSet(ApiConstants.KEY_WIDGET_FILTER_CONTACT_TYPES + "_" + myUserId, defaultGroups);

            // Merge and deduplicate based on active groups
            Set<String> allLinks = new HashSet<>();
            if (activeGroups.contains("favorite")) {
                allLinks.addAll(favorites);
            }
            if (activeGroups.contains("bookmark")) {
                allLinks.addAll(bookmarks);
            }
            if (activeGroups.contains("notified")) {
                allLinks.addAll(followed);
            }

            // Get filters
            boolean onlineOnly = prefs.getBoolean(ApiConstants.KEY_WIDGET_FILTER_ONLINE + "_" + myUserId, false);
            Set<String> activeSexes = prefs.getStringSet(ApiConstants.KEY_WIDGET_FILTER_SEXES + "_" + myUserId, new HashSet<>());
            final String widgetSortOrder = prefs.getString(ApiConstants.KEY_WIDGET_SORT_ORDER + "_" + myUserId, "default");

            // Read named mapped users for offline/unnotified fallbacks
            String namesJson = prefs.getString(ApiConstants.KEY_ONLINE_NOTIF_NAMES, "{}");
            JSONObject namesObj;
            try {
                namesObj = new JSONObject(namesJson);
            } catch (Exception e) {
                namesObj = new JSONObject();
            }

            // Load details from ProfileCacheManager
            ProfileCacheManager cache = ProfileCacheManager.getInstance();
            // Ensure cache is initialized
            cache.init(context);

            for (String userLink : allLinks) {
                if (userLink == null || userLink.isEmpty()) continue;
                String otherUserId = StringUtils.extractUserIdFromLink(userLink);
                if (otherUserId.isEmpty()) continue;

                WidgetContact contact = new WidgetContact();
                contact.userLink = userLink;
                contact.otherUserId = otherUserId;

                JSONObject profile = cache.getProfile(otherUserId);
                if (profile != null) {
                    contact.name = profile.optString("name", profile.optString("pseudo", namesObj.optString(userLink, "User")));
                    contact.isOnline = "1".equals(profile.optString("online", "0")) 
                            || profile.optBoolean("online", false) 
                            || profile.optBoolean("is_online", false);
                    contact.lastConnection = profile.optString("last_connection", profile.optString("last_connected", ""));

                    // Filter: Online only
                    if (onlineOnly && !contact.isOnline) {
                        continue;
                    }

                    // Filter: Sex/Gender
                    if (!activeSexes.isEmpty()) {
                        String sexLink = profile.optString("sex_link", "");
                        if (!sexLink.isEmpty()) {
                            String sexDesc = MetadataManager.getInstance().resolve(sexLink);
                            if (sexDesc != null && !activeSexes.contains(sexDesc)) {
                                continue;
                            }
                        } else {
                            // No sex link in cached profile, skip if filtering is active
                            continue;
                        }
                    }

                    String age = profile.optString("age", "");
                    String city = profile.optString("city", "");
                    if (!city.isEmpty()) {
                        if (!age.isEmpty()) {
                            contact.details = age + " ans, " + city;
                        } else {
                            contact.details = city;
                        }
                    } else if (!age.isEmpty()) {
                        contact.details = age + " ans";
                    } else {
                        contact.details = contact.isOnline ? "Online" : "Offline";
                    }

                    JSONObject photo = profile.optJSONObject("photo");
                    if (photo != null) {
                        contact.avatarUrl = photo.optString("image_82x107_link", 
                                photo.optString("image_144x189_link", ""));
                    }
                } else {
                    // Fallback for non-cached user profiles
                    if (onlineOnly) {
                        continue;
                    }
                    if (!activeSexes.isEmpty()) {
                        continue;
                    }
                    contact.name = namesObj.optString(userLink, "User " + otherUserId);
                    contact.isOnline = false;
                    contact.details = "Offline";
                    contact.avatarUrl = "";
                    contact.lastConnection = "";
                }

                contacts.add(contact);
            }

            // Sort contacts based on widgetSortOrder
            Collections.sort(contacts, new Comparator<WidgetContact>() {
                @Override
                public int compare(WidgetContact c1, WidgetContact c2) {
                    if ("timedesc".equals(widgetSortOrder) || "timeasc".equals(widgetSortOrder)) {
                        long t1 = parseLastConnectionToTimestamp(c1.lastConnection, c1.isOnline);
                        long t2 = parseLastConnectionToTimestamp(c2.lastConnection, c2.isOnline);
                        if (t1 == 0 && t2 == 0) {
                            return c1.name.compareToIgnoreCase(c2.name);
                        }
                        if (t1 == 0) return 1;  // Put 0 values at the bottom
                        if (t2 == 0) return -1; // Put 0 values at the bottom
                        
                        if ("timedesc".equals(widgetSortOrder)) {
                            return Long.compare(t2, t1); // newest first (descending)
                        } else {
                            return Long.compare(t1, t2); // oldest first (ascending)
                        }
                    } else {
                        // Default sort: Online contacts first, then by name
                        if (c1.isOnline != c2.isOnline) {
                            return c1.isOnline ? -1 : 1; // online first
                        }
                        return c1.name.compareToIgnoreCase(c2.name);
                    }
                }
            });

            Log.d(TAG, "Contacts loaded: " + contacts.size());
        }

        private long parseLastConnectionToTimestamp(String value, boolean isOnline) {
            if (isOnline) {
                return System.currentTimeMillis();
            }
            if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("need_vip")) {
                return 0; // indicates "no time shown"
            }

            String str = value.trim().toLowerCase();
            long now = System.currentTimeMillis();

            // Handle precise French connection states in order of modern/scraped presence
            if (str.contains("présentement en ligne") || str.equals("membre présentement en ligne")) {
                return now;
            }
            if (str.equals("ce soir")) {
                return now - 2 * 60 * 60 * 1000L;
            }
            if (str.equals("cet après-midi") || str.equals("cet apres-midi")) {
                return now - 6 * 60 * 60 * 1000L;
            }
            if (str.equals("ce matin")) {
                return now - 12 * 60 * 60 * 1000L;
            }
            if (str.equals("cette nuit")) {
                return now - 18 * 60 * 60 * 1000L;
            }
            if (str.equals("hier soir")) {
                return now - 24 * 60 * 60 * 1000L;
            }
            if (str.equals("la nuit passée") || str.equals("la nuit passee")) {
                return now - 26 * 60 * 60 * 1000L;
            }
            if (str.equals("hier après-midi") || str.equals("hier apres-midi")) {
                return now - 30 * 60 * 60 * 1000L;
            }
            if (str.equals("hier matin")) {
                return now - 36 * 60 * 60 * 1000L;
            }

            // Relative time like "Il y a une semaine", "Il y a un mois", etc.
            if (str.contains("il y a")) {
                if (str.contains("une semaine")) {
                    return now - 7 * 24 * 60 * 60 * 1000L;
                }
                if (str.contains("plus d'un ans") || str.contains("plus d'un an")) {
                    return now - 500 * 24 * 60 * 60 * 1000L;
                }
                if (str.contains("un an")) {
                    return now - 365 * 24 * 60 * 60 * 1000L;
                }
                if (str.contains("plus d'un mois")) {
                    return now - 45 * 24 * 60 * 60 * 1000L;
                }
                if (str.contains("un mois")) {
                    return now - 30 * 24 * 60 * 60 * 1000L;
                }
                
                // Weekly checks (e.g. "Il y a 2 semaines")
                if (str.contains("semaine")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
                    if (m.find()) {
                        int val = Integer.parseInt(m.group());
                        return now - val * 7 * 24 * 60 * 60 * 1000L;
                    }
                }
                
                // Monthly checks (e.g. "Il y a 2 mois")
                if (str.contains("mois")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
                    if (m.find()) {
                        int val = Integer.parseInt(m.group());
                        return now - val * 30 * 24 * 60 * 60 * 1000L;
                    }
                }
                
                // Yearly checks (e.g. "Il y a 2 ans")
                if (str.contains("ans") || str.contains("an")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
                    if (m.find()) {
                        int val = Integer.parseInt(m.group());
                        return now - val * 365 * 24 * 60 * 60 * 1000L;
                    }
                }
            }
            
            // Handle common online/now terms
            if (str.contains("en ligne") || str.contains("online") || str.contains("maintenant") || str.contains("now") || str.contains("en ce moment")) {
                return System.currentTimeMillis();
            }

            // Handle relative time like "Il y a 5 minutes" or "5 minutes ago"
            if (str.contains("il y a") || str.contains("ago")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
                if (m.find()) {
                    int val = Integer.parseInt(m.group());
                    if (str.contains("minute")) {
                        return now - val * 60 * 1000L;
                    } else if (str.contains("heure") || str.contains("hour")) {
                        return now - val * 60 * 60 * 1000L;
                    } else if (str.contains("jour") || str.contains("day")) {
                        return now - val * 24 * 60 * 60 * 1000L;
                    }
                }
            }

            return 0;
        }
    }

    private static class WidgetContact {
        String otherUserId;
        String userLink;
        String name;
        String details;
        String avatarUrl;
        boolean isOnline;
        String lastConnection;
    }
}
