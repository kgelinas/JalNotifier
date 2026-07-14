package io.github.kgelinas.jalfnotifier.data.repository;

import android.content.Context;
import io.github.kgelinas.jalfnotifier.data.AppPrefs;
import io.github.kgelinas.jalfnotifier.data.PreferenceKeys;

/**
 * Single source of truth for user session data.
 *
 * <p>Consumers (Activities / Fragments / Workers) go through this class rather
 * than touching {@link AppPrefs} or the raw key constants directly.  If storage
 * ever migrates from SharedPreferences to Room or a remote source, only this
 * file needs to change.
 *
 * <p>Usage example:
 * <pre>
 *   UserRepository repo = new UserRepository(context);
 *   if (!repo.isLoggedIn()) startActivity(...LoginActivity...);
 *   String cookie = repo.getFullCookie();
 * </pre>
 */
public class UserRepository {

    private final AppPrefs prefs;

    public UserRepository(Context context) {
        this.prefs = AppPrefs.getInstance(context);
    }

    // ── Login state ────────────────────────────────────────────────────────

    /** Returns true if a non-empty cookie is stored (user is logged in). */
    public boolean isLoggedIn() {
        String cookie = prefs.getString(PreferenceKeys.KEY_FULL_COOKIE, "");
        return !cookie.isEmpty();
    }

    // ── Session tokens ─────────────────────────────────────────────────────

    public String getFullCookie() {
        return prefs.getString(PreferenceKeys.KEY_FULL_COOKIE, "");
    }

    public void setFullCookie(String cookie) {
        prefs.putString(PreferenceKeys.KEY_FULL_COOKIE, cookie);
    }

    public String getSuid() {
        return prefs.getString(PreferenceKeys.KEY_SUID, "");
    }

    public void setSuid(String suid) {
        prefs.putString(PreferenceKeys.KEY_SUID, suid);
    }

    // ── User identity ──────────────────────────────────────────────────────

    public String getUserId() {
        return prefs.getString(PreferenceKeys.KEY_USER_ID, "");
    }

    public void setUserId(String userId) {
        prefs.putString(PreferenceKeys.KEY_USER_ID, userId);
    }

    public String getUsername() {
        return prefs.getString(PreferenceKeys.KEY_USERNAME, "");
    }

    public void setUsername(String username) {
        prefs.putString(PreferenceKeys.KEY_USERNAME, username);
    }

    public String getPassword() {
        return prefs.getString(PreferenceKeys.KEY_PASSWORD, "");
    }

    public void setPassword(String password) {
        prefs.putString(PreferenceKeys.KEY_PASSWORD, password);
    }

    // ── Profile cache ──────────────────────────────────────────────────────

    public String getUserProfileJson() {
        return prefs.getString(PreferenceKeys.KEY_USER_PROFILE_JSON, "");
    }

    public void setUserProfileJson(String json) {
        prefs.putString(PreferenceKeys.KEY_USER_PROFILE_JSON, json);
    }

    // ── App state ──────────────────────────────────────────────────────────

    public boolean isAppInForeground() {
        return prefs.getBoolean(PreferenceKeys.KEY_APP_IN_FOREGROUND, false);
    }

    public void setAppInForeground(boolean foreground) {
        prefs.putBoolean(PreferenceKeys.KEY_APP_IN_FOREGROUND, foreground);
    }

    public boolean isAppearOffline() {
        return prefs.getBoolean(PreferenceKeys.KEY_APPEAR_OFFLINE, false);
    }

    public void setAppearOffline(boolean offline) {
        prefs.putBoolean(PreferenceKeys.KEY_APPEAR_OFFLINE, offline);
    }

    // ── Notifications ──────────────────────────────────────────────────────

    public boolean isBackgroundNotificationsEnabled() {
        return prefs.getBoolean(PreferenceKeys.KEY_BACKGROUND_NOTIFICATIONS, true);
    }

    public void setBackgroundNotificationsEnabled(boolean enabled) {
        prefs.putBoolean(PreferenceKeys.KEY_BACKGROUND_NOTIFICATIONS, enabled);
    }

    // ── Logout helper ──────────────────────────────────────────────────────

    /** Clears all auth tokens and cached user data. */
    public void clearSession() {
        prefs.putString(PreferenceKeys.KEY_FULL_COOKIE, "");
        prefs.putString(PreferenceKeys.KEY_SUID, "");
        prefs.putString(PreferenceKeys.KEY_USER_ID, "");
        prefs.putString(PreferenceKeys.KEY_USERNAME, "");
        prefs.putString(PreferenceKeys.KEY_PASSWORD, "");
        prefs.putString(PreferenceKeys.KEY_USER_PROFILE_JSON, "");
    }
}
