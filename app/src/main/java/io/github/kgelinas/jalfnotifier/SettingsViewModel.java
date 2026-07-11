package io.github.kgelinas.jalfnotifier;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.kgelinas.jalfnotifier.sync.GoogleDriveManager;
import io.github.kgelinas.jalfnotifier.sync.SettingsSyncManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SettingsViewModel extends AndroidViewModel {

    private static final String TAG = "SettingsViewModel";
    private final SharedPreferences prefs;

    private final MutableLiveData<String> _googleAccount = new MutableLiveData<>();
    public final LiveData<String> googleAccount = _googleAccount;

    private final MutableLiveData<String> _syncStatus = new MutableLiveData<>();
    public final LiveData<String> syncStatus = _syncStatus;

    private final MutableLiveData<String> _lastSyncTime = new MutableLiveData<>();
    public final LiveData<String> lastSyncTime = _lastSyncTime;

    private final MutableLiveData<Boolean> _isSyncing = new MutableLiveData<>();
    public final LiveData<Boolean> isSyncing = _isSyncing;

    private final MutableLiveData<List<QuickResponse>> _quickResponses = new MutableLiveData<>();
    public final LiveData<List<QuickResponse>> quickResponses = _quickResponses;

    private final MutableLiveData<Set<String>> _selectedSexes = new MutableLiveData<>();
    public final LiveData<Set<String>> selectedSexes = _selectedSexes;

    private final MutableLiveData<Set<String>> _salutationSexes = new MutableLiveData<>();
    public final LiveData<Set<String>> salutationSexes = _salutationSexes;

    private final MutableLiveData<Boolean> _useImperial = new MutableLiveData<>();
    public final LiveData<Boolean> useImperial = _useImperial;

    private final MutableLiveData<Boolean> _sendReadReceipts = new MutableLiveData<>();
    public final LiveData<Boolean> sendReadReceipts = _sendReadReceipts;

    private final MutableLiveData<Boolean> _blurNsfw = new MutableLiveData<>();
    public final LiveData<Boolean> blurNsfw = _blurNsfw;

    private final MutableLiveData<String> _geminiLanguage = new MutableLiveData<>();
    public final LiveData<String> geminiLanguage = _geminiLanguage;

    private final MutableLiveData<String> _aiPromptTemplate = new MutableLiveData<>();
    public final LiveData<String> aiPromptTemplate = _aiPromptTemplate;

    private final MutableLiveData<String> _geminiModel = new MutableLiveData<>();
    public final LiveData<String> geminiModel = _geminiModel;

    private final MutableLiveData<String> _aiReplyPromptTemplate = new MutableLiveData<>();
    public final LiveData<String> aiReplyPromptTemplate = _aiReplyPromptTemplate;

    private final MutableLiveData<Boolean> _aiIncludePicture = new MutableLiveData<>();
    public final LiveData<Boolean> aiIncludePicture = _aiIncludePicture;

    private final MutableLiveData<Boolean> _aiIncludeAllPictures = new MutableLiveData<>();
    public final LiveData<Boolean> aiIncludeAllPictures = _aiIncludeAllPictures;

    private final MutableLiveData<Boolean> _aiEncodeBase64 = new MutableLiveData<>();
    public final LiveData<Boolean> aiEncodeBase64 = _aiEncodeBase64;

    private final MutableLiveData<String> _aiProvider = new MutableLiveData<>();
    public final LiveData<String> aiProvider = _aiProvider;

    private final MutableLiveData<String> _aiToken = new MutableLiveData<>();
    public final LiveData<String> aiToken = _aiToken;

    private final MutableLiveData<String> _aiEndpoint = new MutableLiveData<>();
    public final LiveData<String> aiEndpoint = _aiEndpoint;

    private final MutableLiveData<List<String>> _availableModels = new MutableLiveData<>();
    public final LiveData<List<String>> availableModels = _availableModels;

    private final MutableLiveData<Boolean> _autoBackupEnabled = new MutableLiveData<>();
    public final LiveData<Boolean> autoBackupEnabled = _autoBackupEnabled;

    private final MutableLiveData<Boolean> _remoteGeolocation = new MutableLiveData<>();
    public final LiveData<Boolean> remoteGeolocation = _remoteGeolocation;

    private final MutableLiveData<Boolean> _remoteShareDistance = new MutableLiveData<>();
    public final LiveData<Boolean> remoteShareDistance = _remoteShareDistance;

    private final MutableLiveData<Boolean> _remoteAlwaysDefault = new MutableLiveData<>();
    public final LiveData<Boolean> remoteAlwaysDefault = _remoteAlwaysDefault;

    private final MutableLiveData<Map<String, String>> _sexesMetadata = new MutableLiveData<>();
    public final LiveData<Map<String, String>> sexesMetadata = _sexesMetadata;

    private final MutableLiveData<Integer> _currentLlmSlot = new MutableLiveData<>(0);
    public final LiveData<Integer> currentLlmSlot = _currentLlmSlot;
    
    private final MutableLiveData<String> _friendlyName = new MutableLiveData<>();
    public final LiveData<String> friendlyName = _friendlyName;

    private JSONArray aiConfigsArray = new JSONArray();

    private final Runnable metadataListener = this::loadMetadata;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        prefs = application.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        loadSettings();
        MetadataManager.getInstance().addListener(metadataListener);
        loadMetadata();
        _googleAccount.setValue(prefs.getString(ApiConstants.KEY_GOOGLE_ACCOUNT_EMAIL, null));
        _autoBackupEnabled.setValue(prefs.getBoolean(ApiConstants.KEY_AUTO_BACKUP, false));
        updateLastSyncLabel();
    }

    private void updateLastSyncLabel() {
        long last = prefs.getLong(ApiConstants.KEY_LAST_DRIVE_SYNC_TIME, 0);
        if (last == 0) {
            _lastSyncTime.postValue(getApplication().getString(R.string.sync_never));
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            _lastSyncTime.postValue(sdf.format(new Date(last)));
        }
    }

    public void setAiIncludePicture(boolean include) {
        prefs.edit().putBoolean(ApiConstants.KEY_AI_INCLUDE_PICTURE, include).apply();
        _aiIncludePicture.postValue(include);
    }

    public void setAiIncludeAllPictures(boolean include) {
        prefs.edit().putBoolean(ApiConstants.KEY_AI_INCLUDE_ALL_PICTURES, include).apply();
        _aiIncludeAllPictures.postValue(include);
    }

    public void setAiEncodeBase64(boolean encode) {
        prefs.edit().putBoolean(ApiConstants.KEY_AI_ENCODE_BASE64, encode).apply();
        _aiEncodeBase64.postValue(encode);
    }

    public void setGoogleAccount(String email) {
        prefs.edit().putString(ApiConstants.KEY_GOOGLE_ACCOUNT_EMAIL, email).apply();
        _googleAccount.postValue(email);
        if (email != null) {
            updateLastSyncLabel();
        }
    }

    public void setAutoBackupEnabled(boolean enabled) {
        prefs.edit().putBoolean(ApiConstants.KEY_AUTO_BACKUP, enabled).apply();
        _autoBackupEnabled.postValue(enabled);
    }

    public void backup() {
        _isSyncing.setValue(true);
        String email = googleAccount.getValue();
        SettingsSyncManager.backupToDrive(getApplication(), email, new SettingsSyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                long now = System.currentTimeMillis();
                prefs.edit().putLong(ApiConstants.KEY_LAST_DRIVE_SYNC_TIME, now).apply();
                _syncStatus.postValue(getApplication().getString(R.string.sync_backup_success));
                _isSyncing.postValue(false);
                updateLastSyncLabel();
            }

            @Override
            public void onFailure(Exception e) {
                _syncStatus.postValue(getApplication().getString(R.string.sync_backup_failed_prefix));
                _isSyncing.postValue(false);
            }
        });
    }

    public void restore() {
        _isSyncing.setValue(true);
        String email = googleAccount.getValue();
        SettingsSyncManager.restoreFromDrive(getApplication(), email, new SettingsSyncManager.SyncCallback() {
            @Override
            public void onSuccess() {
                _syncStatus.postValue(getApplication().getString(R.string.sync_restore_success));
                _isSyncing.postValue(false);
                loadSettings(); // Reload local state
            }

            @Override
            public void onFailure(Exception e) {
                _syncStatus.postValue(getApplication().getString(R.string.sync_restore_failed_prefix));
                _isSyncing.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        MetadataManager.getInstance().removeListener(metadataListener);
        super.onCleared();
    }

    private void loadSettings() {
        try {
            _selectedSexes
                    .postValue(new HashSet<>(prefs.getStringSet(ApiConstants.KEY_FILTERED_SEXES, new HashSet<>())));
        } catch (ClassCastException e) {
            _selectedSexes.postValue(new HashSet<>());
            prefs.edit().remove(ApiConstants.KEY_FILTERED_SEXES).apply();
        }

        try {
            _salutationSexes
                    .postValue(new HashSet<>(prefs.getStringSet(ApiConstants.KEY_SALUTATION_SEXES, new HashSet<>())));
        } catch (ClassCastException e) {
            _salutationSexes.postValue(new HashSet<>());
            prefs.edit().remove(ApiConstants.KEY_SALUTATION_SEXES).apply();
        }

        _useImperial.postValue(prefs.getBoolean(ApiConstants.KEY_USE_IMPERIAL, false));
        _sendReadReceipts.postValue(prefs.getBoolean(ApiConstants.KEY_SEND_READ_RECEIPTS, true));
        _blurNsfw.postValue(prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true));
        _geminiLanguage.postValue(prefs.getString(ApiConstants.KEY_GEMINI_LANGUAGE, "Français"));
        _aiPromptTemplate.postValue(prefs.getString(ApiConstants.KEY_AI_PROMPT_TEMPLATE, ""));
        _aiReplyPromptTemplate.postValue(prefs.getString(ApiConstants.KEY_AI_REPLY_PROMPT_TEMPLATE, ""));
        _aiIncludePicture.postValue(prefs.getBoolean(ApiConstants.KEY_AI_INCLUDE_PICTURE, false));
        _aiIncludeAllPictures.postValue(prefs.getBoolean(ApiConstants.KEY_AI_INCLUDE_ALL_PICTURES, false));
        _aiEncodeBase64.postValue(prefs.getBoolean(ApiConstants.KEY_AI_ENCODE_BASE64, false));
        
        loadAiConfigs();
        
        String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        _remoteGeolocation.postValue(prefs.getInt("remote_geolocation_" + myId, 1) == 1);
        _remoteShareDistance.postValue(prefs.getInt("remote_share_distance_" + myId, 1) == 1);
        _remoteAlwaysDefault.postValue(true); // Default
        loadQuickResponses();
        fetchGoldMessage();
        fetchRemoteGeolocationSettings();
    }

    private void loadMetadata() {
        _sexesMetadata.postValue(MetadataManager.getInstance().getCategory("sexes"));
    }

    private String getQuickResponsesKey() {
        String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        if (!myId.isEmpty()) {
            return ApiConstants.KEY_QUICK_RESPONSES + "_" + myId;
        }
        return ApiConstants.KEY_QUICK_RESPONSES;
    }

    private void loadQuickResponses() {
        String json = prefs.getString(getQuickResponsesKey(), "[]");
        List<QuickResponse> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    list.add(new QuickResponse(obj.optString("label"), obj.optString("message"), i));
                } else {
                    list.add(new QuickResponse(item.toString(), item.toString(), i));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading quick responses", e);
        }
        _quickResponses.postValue(list);
    }

    public void toggleSex(String desc, boolean isSalutation, boolean isChecked) {
        Set<String> current = isSalutation ? _salutationSexes.getValue() : _selectedSexes.getValue();
        if (current == null)
            current = new HashSet<>();

        Set<String> next = new HashSet<>(current);
        if (isChecked)
            next.add(desc);
        else
            next.remove(desc);

        if (isSalutation) {
            _salutationSexes.setValue(next);
            prefs.edit().putStringSet(ApiConstants.KEY_SALUTATION_SEXES, next).apply();
        } else {
            _selectedSexes.setValue(next);
            prefs.edit().putStringSet(ApiConstants.KEY_FILTERED_SEXES, next).apply();
        }
    }

    public static boolean getNotificationFilter(SharedPreferences prefs, String sexDesc, String type) {
        String key = "notif_filter_" + sexDesc.toLowerCase(Locale.ROOT) + "_" + type.toLowerCase(Locale.ROOT);
        if (!prefs.contains(key)) {
            if ("chat".equals(type)) {
                Set<String> filtered = null;
                try {
                    filtered = prefs.getStringSet(ApiConstants.KEY_FILTERED_SEXES, null);
                } catch (Exception ignored) {}
                if (filtered == null || filtered.isEmpty()) {
                    return true;
                } else {
                    return filtered.contains(sexDesc);
                }
            } else {
                Set<String> salutations = null;
                try {
                    salutations = prefs.getStringSet(ApiConstants.KEY_SALUTATION_SEXES, null);
                } catch (Exception ignored) {}
                if (salutations == null || salutations.isEmpty()) {
                    return true;
                } else {
                    return salutations.contains(sexDesc);
                }
            }
        }
        return prefs.getBoolean(key, true);
    }

    public boolean getNotificationFilter(String sexDesc, String type) {
        return getNotificationFilter(prefs, sexDesc, type);
    }

    public void setNotificationFilter(String sexDesc, String type, boolean enabled) {
        String key = "notif_filter_" + sexDesc.toLowerCase(Locale.ROOT) + "_" + type.toLowerCase(Locale.ROOT);
        prefs.edit().putBoolean(key, enabled).apply();
    }

    public boolean getContactTypeFilter(String type) {
        return prefs.getBoolean("notif_filter_type_" + type.toLowerCase(Locale.ROOT), true);
    }

    public void setContactTypeFilter(String type, boolean enabled) {
        prefs.edit().putBoolean("notif_filter_type_" + type.toLowerCase(Locale.ROOT), enabled).apply();
    }

    public boolean getContactTypeCategoryFilter(String contactType, String type) {
        String key = "notif_filter_ct_" + contactType.toLowerCase(Locale.ROOT) + "_" + type.toLowerCase(Locale.ROOT);
        return prefs.getBoolean(key, true);
    }

    public void setContactTypeCategoryFilter(String contactType, String type, boolean enabled) {
        String key = "notif_filter_ct_" + contactType.toLowerCase(Locale.ROOT) + "_" + type.toLowerCase(Locale.ROOT);
        prefs.edit().putBoolean(key, enabled).apply();
    }

    public void setUseImperial(boolean value) {
        _useImperial.setValue(value);
        prefs.edit().putBoolean(ApiConstants.KEY_USE_IMPERIAL, value).apply();
        syncUsageToAccount(value);
    }

    public void setSendReadReceipts(boolean value) {
        _sendReadReceipts.setValue(value);
        prefs.edit().putBoolean(ApiConstants.KEY_SEND_READ_RECEIPTS, value).apply();
    }

    public void setBlurNsfw(boolean value) {
        _blurNsfw.setValue(value);
        prefs.edit().putBoolean(ApiConstants.KEY_BLUR_NSFW, value).apply();
    }

    public void setGeminiLanguage(String value) {
        _geminiLanguage.setValue(value);
        prefs.edit().putString(ApiConstants.KEY_GEMINI_LANGUAGE, value).apply();
    }

    public void setAiPromptTemplate(String value) {
        _aiPromptTemplate.setValue(value);
        prefs.edit().putString(ApiConstants.KEY_AI_PROMPT_TEMPLATE, value).apply();
    }

    public void setCurrentLlmSlot(int slot) {
        _currentLlmSlot.setValue(slot);
        updateUiForCurrentSlot();
    }

    private void updateUiForCurrentSlot() {
        int slot = _currentLlmSlot.getValue() != null ? _currentLlmSlot.getValue() : 0;
        JSONObject config = aiConfigsArray.optJSONObject(slot);
        if (config == null) config = new JSONObject();
        
        String fName = config.optString("friendlyName", "");
        _friendlyName.setValue(fName);
        _geminiModel.setValue(config.optString("model", "models/gemini-1.5-flash"));
        _aiProvider.setValue(config.optString("provider", "Google AI Studio"));
        _aiToken.setValue(config.optString("token", ""));
        _aiEndpoint.setValue(config.optString("endpoint", "https://generativelanguage.googleapis.com/v1beta/openai"));
        
        fetchAvailableModels();
    }

    private void saveCurrentSlotConfig(String key, String value) {
        int slot = _currentLlmSlot.getValue() != null ? _currentLlmSlot.getValue() : 0;
        try {
            while (aiConfigsArray.length() <= slot) {
                aiConfigsArray.put(new JSONObject());
            }
            JSONObject config = aiConfigsArray.getJSONObject(slot);
            config.put(key, value);
            prefs.edit().putString(ApiConstants.KEY_AI_CONFIGS, aiConfigsArray.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save AI config", e);
        }
    }

    private void loadAiConfigs() {
        String jsonStr = prefs.getString(ApiConstants.KEY_AI_CONFIGS, null);
        if (jsonStr != null) {
            try {
                aiConfigsArray = new JSONArray(jsonStr);
            } catch (Exception e) {
                aiConfigsArray = new JSONArray();
            }
        } else {
            // Migrate old settings
            aiConfigsArray = new JSONArray();
            JSONObject primary = new JSONObject();
            try {
                primary.put("provider", prefs.getString(ApiConstants.KEY_AI_PROVIDER, "Google AI Studio"));
                primary.put("token", prefs.getString(ApiConstants.KEY_AI_TOKEN, ""));
                primary.put("endpoint", prefs.getString(ApiConstants.KEY_AI_ENDPOINT, "https://generativelanguage.googleapis.com/v1beta/openai"));
                primary.put("model", prefs.getString(ApiConstants.KEY_GEMINI_MODEL, "models/gemini-1.5-flash"));
                aiConfigsArray.put(primary);
                prefs.edit().putString(ApiConstants.KEY_AI_CONFIGS, aiConfigsArray.toString()).apply();
            } catch (Exception ignored) {}
        }
        updateUiForCurrentSlot();
    }

    public void setGeminiModel(String value) {
        _geminiModel.setValue(value);
        saveCurrentSlotConfig("model", value);
    }

    public void setFriendlyName(String value) {
        _friendlyName.setValue(value);
        saveCurrentSlotConfig("friendlyName", value);
    }

    public void setAiProvider(String value) {
        _aiProvider.setValue(value);
        saveCurrentSlotConfig("provider", value);
    }

    public void setAiToken(String value) {
        _aiToken.setValue(value);
        saveCurrentSlotConfig("token", value);
    }

    public void setAiEndpoint(String value) {
        _aiEndpoint.setValue(value);
        saveCurrentSlotConfig("endpoint", value);
    }

    public void setAiReplyPromptTemplate(String value) {
        _aiReplyPromptTemplate.setValue(value);
        prefs.edit().putString(ApiConstants.KEY_AI_REPLY_PROMPT_TEMPLATE, value).apply();
    }


    public void fetchRemoteGeolocationSettings() {
        LocationSettingsTask.fetchSettings(getApplication(), new LocationSettingsTask.SettingsCallback() {
            @Override
            public void onSettingsFetched(boolean geolocation, boolean shareDistance, boolean alwaysDefault) {
                _remoteGeolocation.postValue(geolocation);
                _remoteShareDistance.postValue(shareDistance);
                _remoteAlwaysDefault.postValue(alwaysDefault);
                
                String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");
                prefs.edit()
                    .putInt("remote_geolocation_" + myId, geolocation ? 1 : 0)
                    .putInt("remote_share_distance_" + myId, shareDistance ? 1 : 0)
                    .apply();
            }

            @Override
            public void onSuccess() {}

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to fetch remote location settings: " + error);
            }
        });
    }

    public void setRemoteGeolocation(boolean value) {
        _remoteGeolocation.setValue(value);
        String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        prefs.edit().putInt("remote_geolocation_" + myId, value ? 1 : 0).apply();
        updateRemoteSettings();
    }

    public void setRemoteShareDistance(boolean value) {
        _remoteShareDistance.setValue(value);
        updateRemoteSettings();
    }

    public void setRemoteAlwaysDefault(boolean value) {
        _remoteAlwaysDefault.setValue(value);
        updateRemoteSettings();
    }

    private void updateRemoteSettings() {
        boolean geo = Boolean.TRUE.equals(_remoteGeolocation.getValue());
        boolean share = Boolean.TRUE.equals(_remoteShareDistance.getValue());
        boolean always = Boolean.TRUE.equals(_remoteAlwaysDefault.getValue());

        LocationSettingsTask.updateSettings(getApplication(), geo, share, always, new LocationSettingsTask.SettingsCallback() {
            @Override
            public void onSettingsFetched(boolean geolocation, boolean shareDistance, boolean alwaysDefault) {}

            @Override
            public void onSuccess() {
                Log.d(TAG, "Remote location settings updated successfully");
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to update remote location settings: " + error);
            }
        });
    }

    public void fetchAvailableModels() {
        String token = _aiToken.getValue() != null ? _aiToken.getValue().trim() : "";
        String endpoint = _aiEndpoint.getValue() != null ? _aiEndpoint.getValue().trim() : "https://generativelanguage.googleapis.com/v1beta/openai";

        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        // Post a loading placeholder so the UI isn't stale
        List<String> loading = new java.util.ArrayList<>();
        loading.add("Loading…");
        _availableModels.postValue(loading);

        String modelsUrl = endpoint;
        if (!modelsUrl.endsWith("/models")) {
            modelsUrl = modelsUrl + "/v1/models";
        }

        final String finalModelsUrl = modelsUrl;
        okhttp3.HttpUrl parsedUrl = okhttp3.HttpUrl.parse(finalModelsUrl);
        if (parsedUrl == null) {
            List<String> err = new java.util.ArrayList<>();
            err.add("(Invalid URL format)");
            _availableModels.postValue(err);
            return;
        }

        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(parsedUrl);
        if (!token.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        okhttp3.OkHttpClient modelClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        modelClient.newCall(requestBuilder.build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                List<String> err = new java.util.ArrayList<>();
                err.add("(Network error: " + e.getMessage() + ")");
                _availableModels.postValue(err);
                Log.e(TAG, "Failed to fetch models from " + finalModelsUrl, e);
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response)
                    throws java.io.IOException {
                try (okhttp3.Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject json = new JSONObject(r.body().string());
                        JSONArray arr = json.optJSONArray("data");
                        List<String> models = new java.util.ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject m = arr.getJSONObject(i);
                                String id = m.optString("id");
                                if (!id.isEmpty()) {
                                    models.add(id);
                                }
                            }
                        } else {
                            JSONArray modelsArr = json.optJSONArray("models");
                            if (modelsArr != null) {
                                for (int i = 0; i < modelsArr.length(); i++) {
                                    JSONObject m = modelsArr.getJSONObject(i);
                                    String name = m.optString("name");
                                    if (!name.isEmpty()) {
                                        models.add(name);
                                    }
                                }
                            }
                        }

                        if (!models.isEmpty()) {
                            Collections.sort(models);
                            _availableModels.postValue(models);
                        } else {
                            List<String> err = new java.util.ArrayList<>();
                            err.add("(No compatible models found)");
                            _availableModels.postValue(err);
                        }
                    } else {
                        List<String> err = new java.util.ArrayList<>();
                        err.add("(API error " + r.code() + " — check settings)");
                        _availableModels.postValue(err);
                        Log.e(TAG, "Models API error: " + r.code() + " at " + finalModelsUrl);
                    }
                } catch (Exception e) {
                    List<String> err = new java.util.ArrayList<>();
                    err.add("(Failed to parse model list)");
                    _availableModels.postValue(err);
                    Log.e(TAG, "Error parsing models from " + finalModelsUrl, e);
                }
            }
        });
    }

    public void saveQuickResponse(Integer index, String label, String message) {
        String json = prefs.getString(getQuickResponsesKey(), "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("label", label);
            obj.put("message", message);

            if (index == null) {
                arr.put(obj);
            } else {
                arr.put(index, obj);
                if (index == 0 && "Message Gold".equals(label)) {
                    syncGoldMessage(message);
                }
            }
            prefs.edit().putString(getQuickResponsesKey(), arr.toString()).apply();
            loadQuickResponses();
        } catch (Exception e) {
            Log.e(TAG, "Error saving quick response", e);
        }
    }

    public void deleteQuickResponse(int index) {
        String json = prefs.getString(getQuickResponsesKey(), "[]");
        try {
            JSONArray oldArr = new JSONArray(json);
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < oldArr.length(); i++) {
                if (i != index)
                    newArr.put(oldArr.get(i));
            }
            prefs.edit().putString(getQuickResponsesKey(), newArr.toString()).apply();
            loadQuickResponses();
        } catch (Exception e) {
            Log.e(TAG, "Error deleting quick response", e);
        }
    }

    private void syncUsageToAccount(boolean isImperial) {
        SecurePrefs secure = SecurePrefs.get(getApplication());
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty())
            return;

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("flt_display", isImperial ? "i" : "m");
        params.put("save", "Sauvegarder");

        okhttp3.RequestBody formBody = NetworkUtils.createIsoFormBody(params);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://m-app.jalf.com/ct/myOptions/display_options")
                .post(formBody)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Referer", "https://m-app.jalf.com/ct/myOptions/display_options")
                .build();

        new okhttp3.OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                Log.e("SettingsViewModel", "Failed to sync unit preference to account", e);
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response)
                    throws java.io.IOException {
                try (okhttp3.Response r = response) {
                    if (r.isSuccessful()) {
                        Log.d("SettingsViewModel", "Unit preference synced to account successfully");
                    } else {
                        Log.e("SettingsViewModel", "Failed to sync unit preference to account: " + r.code());
                    }
                }
            }
        });
    }

    private void fetchGoldMessage() {
        SecurePrefs secure = SecurePrefs.get(getApplication());
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");

        if (myId.isEmpty() || fullCookie.isEmpty())
            return;

        String url = ApiConstants.BASE_URL + "/rest/users/" + myId + "/conversations/gold-message";
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        new okhttp3.OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response)
                    throws java.io.IOException {
                try (okhttp3.Response r = response) {
                    if (r.isSuccessful()) {
                        try {
                            String bodyStr = NetworkUtils.responseToString(r);
                            if (bodyStr.isEmpty())
                                return;
                            JSONObject json = new JSONObject(bodyStr);
                            String goldMsg = json.optString("message", null);
                            if (goldMsg != null && !goldMsg.isEmpty()) {
                                saveGoldMessageLocally(goldMsg);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });
    }

    private void saveGoldMessageLocally(String message) {
        String json = prefs.getString(getQuickResponsesKey(), "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("label", "Message Gold");
            obj.put("message", message);

            if (arr.length() > 0) {
                JSONObject first = arr.optJSONObject(0);
                if (first != null && "Message Gold".equals(first.optString("label"))) {
                    if (!first.optString("message").equals(message)) {
                        arr.put(0, obj);
                        prefs.edit().putString(getQuickResponsesKey(), arr.toString()).apply();
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(SettingsViewModel.this::loadQuickResponses);
                    }
                } else {
                    JSONArray newArr = new JSONArray();
                    newArr.put(obj);
                    for (int i = 0; i < arr.length(); i++) {
                        newArr.put(arr.get(i));
                    }
                    prefs.edit().putString(getQuickResponsesKey(), newArr.toString()).apply();
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(SettingsViewModel.this::loadQuickResponses);
                }
            } else {
                arr.put(obj);
                prefs.edit().putString(getQuickResponsesKey(), arr.toString()).apply();
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(SettingsViewModel.this::loadQuickResponses);
            }
        } catch (Exception e) {
        }
    }

    private void syncGoldMessage(String message) {
        SecurePrefs secure = SecurePrefs.get(getApplication());
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");

        if (myId.isEmpty() || fullCookie.isEmpty())
            return;

        String url = ApiConstants.BASE_URL + "/rest/users/" + myId + "/conversations/gold-message";
        // Simple JSON escape
        String escapedMsg = message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r",
                "\\r");
        String payload = "{\"message\":\"" + escapedMsg + "\"}";
        okhttp3.RequestBody body = okhttp3.RequestBody.create(payload,
                okhttp3.MediaType.parse("application/vnd.jalf.convo.goldmessage+json"));

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        new okhttp3.OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response)
                    throws java.io.IOException {
                response.close();
            }
        });
    }

    public static class QuickResponse {
        public final String label;
        public final String message;
        public final int index;

        public QuickResponse(String label, String message, int index) {
            this.label = label;
            this.message = message;
            this.index = index;
        }
    }
}
