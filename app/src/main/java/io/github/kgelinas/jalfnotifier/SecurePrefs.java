package io.github.kgelinas.jalfnotifier;

import android.content.Context;

/**
 * Modern wrapper around SecureDataStore (Jetpack DataStore + Tink).
 * Preserves the old API for convenience while eliminating the deprecated
 * EncryptedSharedPreferences and its associated build warnings.
 */
public class SecurePrefs {

    private final SecureDataStore dataStore;

    private SecurePrefs(Context context) {
        this.dataStore = SecureDataStore.getInstance(context);
    }

    /**
     * Returns a SecurePrefs instance.
     */
    public static SecurePrefs get(Context context) {
        return new SecurePrefs(context);
    }

    /**
     * Retrieves a string value from the secure DataStore.
     */
    public String getString(String key, String defValue) {
        return dataStore.getString(key, defValue);
    }

    /**
     * Saves a string value to the secure DataStore.
     */
    public SecurePrefs putString(String key, String value) {
        dataStore.putString(key, value);
        return this;
    }

    /**
     * Clears all secure data.
     */
    public void clear() {
        dataStore.clear();
    }

    /**
     * Removes a specific key from secure storage.
     */
    public void remove(String key) {
        dataStore.remove(key);
    }
}
