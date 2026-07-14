package io.github.kgelinas.jalfnotifier.data;

/**
 * SharedPreferences key constants.
 * Extracted from ApiConstants as part of the package refactoring.
 */
public final class PreferenceKeys {

    private PreferenceKeys() {}

    public static final String PREFS_NAME = "JALNotifierPrefs";

    // Auth / session
    public static final String KEY_SUID = "SUID";
    public static final String KEY_FULL_COOKIE = "FULL_COOKIE";
    public static final String KEY_USER_ID = "USER_ID";
    public static final String KEY_USERNAME = "USERNAME";
    public static final String KEY_PASSWORD = "PASSWORD";
    public static final String KEY_USER_PROFILE_JSON = "USER_PROFILE_JSON";
    public static final String KEY_APP_IN_FOREGROUND = "APP_IN_FOREGROUND";

    // Messaging cache
    public static final String KEY_CACHED_MESSAGES_V2 = "CACHED_MESSAGES_V2";
    public static final String KEY_NOTIFIED_LINKS = "NOTIFIED_LINKS";
    public static final String KEY_PENDING_AUTO_MESSAGES = "PENDING_AUTO_MESSAGES";

    // Filtering / UI preferences
    public static final String KEY_FILTERED_SEXES = "FILTERED_SEXES";
    public static final String KEY_SALUTATION_SEXES = "SALUTATION_SEXES";
    public static final String KEY_USE_IMPERIAL = "USE_IMPERIAL";
    public static final String KEY_SEND_READ_RECEIPTS = "SEND_READ_RECEIPTS";
    public static final String KEY_BLUR_NSFW = "BLUR_NSFW";
    public static final String KEY_QUICK_RESPONSES = "QUICK_RESPONSES";

    // Online notifications
    public static final String KEY_FAVORITE_ONLINE_NOTIFS = "FAVORITE_ONLINE_NOTIFS";
    public static final String KEY_ONLINE_NOTIF_USERS = "ONLINE_NOTIF_USERS";
    public static final String KEY_ONLINE_NOTIF_NAMES = "ONLINE_NOTIF_NAMES";

    // Polling / background refresh
    public static final String KEY_FIB_INDEX = "FIB_INDEX";
    public static final String KEY_NEXT_REFRESH_TIME = "NEXT_REFRESH_TIME";
    public static final String KEY_CURRENT_DELAY_MINS = "CURRENT_DELAY_MINS";
    public static final String KEY_BACKGROUND_NOTIFICATIONS = "BACKGROUND_NOTIFICATIONS";

    // Security
    public static final String KEY_BIOMETRIC_LOGIN = "BIOMETRIC_LOGIN";
    public static final String KEY_BIOMETRIC_LOCK = "BIOMETRIC_LOCK";

    // Cloud / backup
    public static final String KEY_AUTO_BACKUP = "AUTO_BACKUP";
    public static final String KEY_GOOGLE_ACCOUNT_EMAIL = "GOOGLE_ACCOUNT_EMAIL";
    public static final String KEY_LAST_DRIVE_SYNC_TIME = "LAST_DRIVE_SYNC_TIME";

    // Deprecated flags
    public static final String KEY_AUTO_OFFLINE = "AUTO_OFFLINE";
    public static final String KEY_NOTIFICATION_TIMEOUT = "NOTIFICATION_TIMEOUT";

    // Presence / SSE
    public static final String KEY_APPEAR_OFFLINE = "APPEAR_OFFLINE";
    public static final String KEY_SSE_URL = "SSE_URL";

    // AI / Gemini
    public static final String KEY_GEMINI_LANGUAGE = "GEMINI_LANGUAGE";
    public static final String KEY_GEMINI_PREFERENCE = "GEMINI_PREFERENCE";
    public static final String KEY_GEMINI_MODEL = "GEMINI_MODEL";
    public static final String KEY_AI_PROVIDER = "AI_PROVIDER";
    public static final String KEY_AI_TOKEN = "AI_TOKEN";
    public static final String KEY_AI_PROMPT_TEMPLATE = "pref_ai_prompt_template";
    public static final String KEY_AI_REPLY_PROMPT_TEMPLATE = "pref_ai_reply_prompt_template";
    public static final String KEY_AI_ENDPOINT = "AI_ENDPOINT";
    public static final String KEY_AI_CONFIGS = "AI_CONFIGS";
    public static final String KEY_AI_INCLUDE_PICTURE = "AI_INCLUDE_PICTURE";
    public static final String KEY_AI_INCLUDE_ALL_PICTURES = "AI_INCLUDE_ALL_PICTURES";
    public static final String KEY_AI_ENCODE_BASE64 = "AI_ENCODE_BASE64";
    public static final String KEY_AI_UNLOCKED = "AI_UNLOCKED";

    // Cache
    public static final String KEY_CACHED_EVENTS = "CACHED_EVENTS";
    public static final String KEY_CACHED_PROFILES = "CACHED_PROFILES";

    // Location
    public static final String KEY_LAST_LOCATION_UPDATE = "LAST_LOCATION_UPDATE";

    // Auto-offline
    public static final String KEY_AUTO_OFFLINE_DELAY_HOURS = "AUTO_OFFLINE_DELAY_HOURS";
    public static final String KEY_AUTO_OFFLINE_DELAY_MINUTES = "AUTO_OFFLINE_DELAY_MINUTES";
    public static final String KEY_WAS_AUTO_OFFLINED = "WAS_AUTO_OFFLINED";

    // Onboarding
    public static final String KEY_PERMISSIONS_ONBOARDING_DONE = "PERMISSIONS_ONBOARDING_DONE";

    // Widget config
    public static final String KEY_WIDGET_REFRESH_INTERVAL = "WIDGET_REFRESH_INTERVAL";
    public static final String KEY_WIDGET_FILTER_ONLINE = "WIDGET_FILTER_ONLINE";
    public static final String KEY_WIDGET_FILTER_CONTACT_TYPES = "WIDGET_FILTER_CONTACT_TYPES";
    public static final String KEY_WIDGET_FILTER_SEXES = "WIDGET_FILTER_SEXES";
    public static final String KEY_WIDGET_SORT_ORDER = "WIDGET_SORT_ORDER";
}
