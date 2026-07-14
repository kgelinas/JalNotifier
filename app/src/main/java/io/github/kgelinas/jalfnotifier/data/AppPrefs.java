package io.github.kgelinas.jalfnotifier.data;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;

public class AppPrefs {
    private static volatile AppPrefs instance;
    private final SharedPreferences prefs;

    private AppPrefs(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AppPrefs getInstance(Context context) {
        if (instance == null) {
            synchronized (AppPrefs.class) {
                if (instance == null) {
                    instance = new AppPrefs(context);
                }
            }
        }
        return instance;
    }

    public SharedPreferences getRaw() {
        return prefs;
    }

    public SharedPreferences.Editor edit() {
        return prefs.edit();
    }

    public String getString(String key, String defValue) {
        return prefs.getString(key, defValue);
    }

    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defValue) {
        return prefs.getBoolean(key, defValue);
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public int getInt(String key, int defValue) {
        return prefs.getInt(key, defValue);
    }

    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public long getLong(String key, long defValue) {
        return prefs.getLong(key, defValue);
    }

    public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    public Set<String> getStringSet(String key, Set<String> defValues) {
        return prefs.getStringSet(key, defValues);
    }

    public void putStringSet(String key, Set<String> values) {
        prefs.edit().putStringSet(key, values).apply();
    }

    public boolean contains(String key) {
        return prefs.contains(key);
    }

    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
