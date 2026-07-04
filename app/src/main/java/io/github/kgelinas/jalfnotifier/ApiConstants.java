package io.github.kgelinas.jalfnotifier;

/**
 * Central place for constants shared across activities and workers.
 * Prevents magic strings from being duplicated across the codebase.
 */
public class ApiConstants {

        private ApiConstants() {
        }

        public static final String BASE_URL = "https://m-app.jalf.com";
        public static final String PATH_LOOKERS = "/ct/online/3";
        public static final String PATH_POKES = "/ct/online/8";
        public static final String PATH_POKES_SENT = "/ct/online/10";
        public static final String PATH_VISITS_SENT = "/ct/online/6";
        public static final String PATH_PROFILE_EDIT = "/ct/profile";
        public static final String PATH_GEOLOCATION_OPTIONS = "/ct/myOptions/geolocation";

        public static final String GEMINI_API_KEY = "";

        public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36";

        public static final String PREFS_NAME = "JALNotifierPrefs";

        // SharedPreferences keys
        public static final String KEY_SUID = "SUID";
        public static final String KEY_FULL_COOKIE = "FULL_COOKIE";
        public static final String KEY_USER_ID = "USER_ID";
        public static final String KEY_USERNAME = "USERNAME";
        public static final String KEY_PASSWORD = "PASSWORD";
        public static final String KEY_USER_PROFILE_JSON = "USER_PROFILE_JSON";
        public static final String KEY_APP_IN_FOREGROUND = "APP_IN_FOREGROUND";
        public static final String KEY_CACHED_MESSAGES_V2 = "CACHED_MESSAGES_V2";
        public static final String KEY_NOTIFIED_LINKS = "NOTIFIED_LINKS";
        public static final String KEY_FILTERED_SEXES = "FILTERED_SEXES";
        public static final String KEY_SALUTATION_SEXES = "SALUTATION_SEXES";
        public static final String KEY_FAVORITE_ONLINE_NOTIFS = "FAVORITE_ONLINE_NOTIFS";
        public static final String KEY_ONLINE_NOTIF_USERS = "ONLINE_NOTIF_USERS";
        public static final String KEY_ONLINE_NOTIF_NAMES = "ONLINE_NOTIF_NAMES";
        public static final String KEY_FIB_INDEX = "FIB_INDEX";
        public static final String KEY_NEXT_REFRESH_TIME = "NEXT_REFRESH_TIME";
        public static final String KEY_CURRENT_DELAY_MINS = "CURRENT_DELAY_MINS";
        public static final String KEY_QUICK_RESPONSES = "QUICK_RESPONSES";
        public static final String KEY_USE_IMPERIAL = "USE_IMPERIAL"; // true = show lbs/feet, false = kg/meters
        public static final String KEY_SEND_READ_RECEIPTS = "SEND_READ_RECEIPTS"; // true = send read_until: now
        public static final String KEY_BLUR_NSFW = "BLUR_NSFW"; // true = blur NSFW photos by default
        public static final String KEY_BIOMETRIC_LOGIN = "BIOMETRIC_LOGIN"; // true = allow auto-fill/login with
                                                                            // biometric
        public static final String KEY_BIOMETRIC_LOCK = "BIOMETRIC_LOCK"; // true = force biometric on app launch
        public static final String KEY_AUTO_BACKUP = "AUTO_BACKUP"; // true = daily auto-backup to Google Drive
        public static final String KEY_AUTO_OFFLINE = "AUTO_OFFLINE"; // Deprecated: was for presence timeout
        public static final String KEY_NOTIFICATION_TIMEOUT = "NOTIFICATION_TIMEOUT"; // Deprecated: was for SSE timeout
        public static final String KEY_APPEAR_OFFLINE = "APPEAR_OFFLINE"; // true = force Visible=no on server
        public static final String KEY_SSE_URL = "SSE_URL"; // stored SSE endpoint
        public static final String KEY_BACKGROUND_NOTIFICATIONS = "BACKGROUND_NOTIFICATIONS"; // true = poll in background when offline
        public static final String KEY_GOOGLE_ACCOUNT_EMAIL = "GOOGLE_ACCOUNT_EMAIL";
        public static final String KEY_LAST_DRIVE_SYNC_TIME = "LAST_DRIVE_SYNC_TIME";

        public static final String KEY_GEMINI_LANGUAGE = "GEMINI_LANGUAGE";
        public static final String KEY_GEMINI_PREFERENCE = "GEMINI_PREFERENCE";
        public static final String KEY_GEMINI_MODEL = "GEMINI_MODEL";
        public static final String KEY_AI_PROVIDER = "AI_PROVIDER";
        public static final String KEY_AI_TOKEN = "AI_TOKEN";
        public static final String KEY_AI_PROMPT_TEMPLATE = "pref_ai_prompt_template";
        public static final String KEY_AI_ENDPOINT = "AI_ENDPOINT";
        public static final String KEY_AI_CONFIGS = "AI_CONFIGS";
        public static final String KEY_AI_PROFILE_FIELDS = "AI_PROFILE_FIELDS";
        public static final String KEY_AI_UNLOCKED = "AI_UNLOCKED";
        public static final String KEY_CACHED_EVENTS = "CACHED_EVENTS";
        public static final String KEY_CACHED_PROFILES = "CACHED_PROFILES";
        public static final String KEY_PENDING_AUTO_MESSAGES = "PENDING_AUTO_MESSAGES";
        public static final String KEY_LAST_LOCATION_UPDATE = "LAST_LOCATION_UPDATE"; // timestamp of last sync
        
        public static final String KEY_AUTO_OFFLINE_DELAY_HOURS = "AUTO_OFFLINE_DELAY_HOURS"; // 0 = disabled, 1-12 = hours
        public static final String KEY_AUTO_OFFLINE_DELAY_MINUTES = "AUTO_OFFLINE_DELAY_MINUTES"; // 0 = disabled, values in minutes
        public static final String KEY_WAS_AUTO_OFFLINED = "WAS_AUTO_OFFLINED"; // boolean flag
        public static final String KEY_PERMISSIONS_ONBOARDING_DONE = "PERMISSIONS_ONBOARDING_DONE";

        // Deep Link / Intent extras
        public static final String ACTION_REPLY = "io.github.kgelinas.jalfnotifier.ACTION_REPLY";
        public static final String ACTION_MARK_AS_READ = "io.github.kgelinas.jalfnotifier.ACTION_MARK_AS_READ";
        public static final String EXTRA_CONVERSATION_LINK = "conversationLink";
        public static final String EXTRA_OTHER_USER_ID = "otherUserId";
        public static final String EXTRA_OTHER_NAME = "otherName";
        public static final String EXTRA_AVATAR_URL = "avatarUrl";
        public static final String EXTRA_SEX_ICON_URL = "sexIconUrl";
        public static final String EXTRA_USER_LINK = "userLink";
        public static final String EXTRA_REMOTE_INPUT_KEY = "extra_reply";

        // Widget Config Keys
        public static final String KEY_WIDGET_REFRESH_INTERVAL = "WIDGET_REFRESH_INTERVAL";
        public static final String KEY_WIDGET_FILTER_ONLINE = "WIDGET_FILTER_ONLINE";
        public static final String KEY_WIDGET_FILTER_CONTACT_TYPES = "WIDGET_FILTER_CONTACT_TYPES";
        public static final String KEY_WIDGET_FILTER_SEXES = "WIDGET_FILTER_SEXES";
        public static final String KEY_WIDGET_SORT_ORDER = "WIDGET_SORT_ORDER";
        public static final String ACTION_OPEN_SETTINGS = "io.github.kgelinas.jalfnotifier.widget.ACTION_OPEN_SETTINGS";
}
