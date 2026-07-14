package io.github.kgelinas.jalfnotifier.data;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.integration.android.AndroidKeysetManager;

import java.io.IOException;
import java.security.GeneralSecurityException;

import io.reactivex.rxjava3.core.Single;

/**
 * Modern replacement for SecurePrefs using Jetpack DataStore + Google Tink.
 * Provides encrypted key-value storage without using deprecated APIs.
 */
public class SecureDataStore {
    private static final String TAG = "SecureDataStore";
    private static final String KEYSET_NAME = "master_keyset";
    private static final String PREF_FILE_NAME = "secure_settings";
    private static final String MASTER_KEY_URI = "android-keystore://jalf_master_key";

    private static volatile SecureDataStore instance;
    private final RxDataStore<Preferences> dataStore;
    private Aead aead;

    private SecureDataStore(Context context) {
        initTink(context);
        dataStore = new RxPreferenceDataStoreBuilder(context.getApplicationContext(), PREF_FILE_NAME).build();
    }

    public static SecureDataStore getInstance(Context context) {
        if (instance == null) {
            synchronized (SecureDataStore.class) {
                if (instance == null) {
                    instance = new SecureDataStore(context);
                }
            }
        }
        return instance;
    }

    private void initTink(Context context) {
        try {
            AeadConfig.register();
            aead = new AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, null)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .getKeysetHandle()
                    .getPrimitive(Aead.class);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to initialize Tink", e);
        }
    }

    /**
     * Encrypts and saves a string value.
     */
    public void putString(String key, String value) {
        if (aead == null || value == null)
            return;
        try {
            byte[] encrypted = aead.encrypt(value.getBytes(), null);
            String base64Value = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            Preferences.Key<String> prefKey = PreferencesKeys.stringKey(key);

            dataStore.updateDataAsync(prefsIn -> {
                MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
                mutablePreferences.set(prefKey, base64Value);
                return Single.just(mutablePreferences);
            }).subscribe(prefs -> {
            }, throwable -> Log.e(TAG, "Update failed", throwable));
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed for key: " + key, e);
        }
    }

    /**
     * Decrypts and retrieves a string value.
     * Note: This uses blockingFirst() for backward compatibility with
     * SharedPreferences API.
     * Ensure this is called off the main thread where possible for large values.
     */
    public String getString(String key, String defValue) {
        if (aead == null)
            return defValue;
        try {
            Preferences.Key<String> prefKey = PreferencesKeys.stringKey(key);
            // RxDataStore<Preferences> data() returns Flowable<Preferences>
            Preferences prefs = dataStore.data().firstElement().blockingGet();
            if (prefs == null)
                return defValue;

            String base64Value = prefs.get(prefKey);
            if (base64Value == null)
                return defValue;

            byte[] encrypted = Base64.decode(base64Value, Base64.NO_WRAP);
            byte[] decrypted = aead.decrypt(encrypted, null);
            return new String(decrypted);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed for key: " + key, e);
            return defValue;
        }
    }

    public void remove(String key) {
        Preferences.Key<String> prefKey = PreferencesKeys.stringKey(key);
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.remove(prefKey);
            return Single.just(mutablePreferences);
        }).subscribe(prefs -> {
        }, throwable -> Log.e(TAG, "Remove failed", throwable));
    }

    public void clear() {
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.clear();
            return Single.just(mutablePreferences);
        }).subscribe(prefs -> {
        }, throwable -> Log.e(TAG, "Clear failed", throwable));
    }
}
