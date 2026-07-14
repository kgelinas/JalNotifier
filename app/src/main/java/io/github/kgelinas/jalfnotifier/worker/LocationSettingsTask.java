package io.github.kgelinas.jalfnotifier.worker;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LocationSettingsTask {
    private static final String TAG = "LocationSettingsTask";
    private static final OkHttpClient client = JalfNotifierApplication.httpClient();

    public interface SettingsCallback {
        void onSettingsFetched(boolean geolocation, boolean shareDistance, boolean alwaysDefault);
        void onSuccess();
        void onFailure(String error);
    }

    public static void fetchSettings(Context context, SettingsCallback callback) {
        SecurePrefs secure = SecurePrefs.get(context);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        String userId = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ApiConstants.KEY_USER_ID, "");

        if (fullCookie.isEmpty() || userId.isEmpty()) {
            if (callback != null) callback.onFailure("Not logged in");
            return;
        }

        // Use REST API for fetching as it's cleaner
        String url = ApiConstants.BASE_URL + "/rest/users/" + userId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject profile = new JSONObject(NetworkUtils.responseToString(r));
                            JSONObject options = profile.optJSONObject("options");
                            if (options != null) {
                                boolean geo = options.optInt("geolocation", 0) == 1;
                                boolean share = options.optInt("share_distance", 0) == 1;
                                // 'always_default' might not be in the basic profile REST, 
                                // but we can default to true as in your HTML.
                                boolean always = true; 
                                
                                // If it's not in 'options', it might be in 'user-options'
                                if (callback != null) callback.onSettingsFetched(geo, share, always);
                            } else {
                                if (callback != null) callback.onFailure("Options not found in profile");
                            }
                        } catch (Exception e) {
                            if (callback != null) callback.onFailure(e.getMessage());
                        }
                    } else {
                        if (callback != null) callback.onFailure("Server error: " + r.code());
                    }
                }
            }
        });
    }

    public static void updateSettings(Context context, boolean geolocation, boolean shareDistance, boolean alwaysDefault, SettingsCallback callback) {
        SecurePrefs secure = SecurePrefs.get(context);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty()) {
            if (callback != null) callback.onFailure("Not logged in");
            return;
        }

        Map<String, String> params = new HashMap<>();
        if (geolocation) params.put("geolocationOption", "1");
        if (shareDistance) params.put("shareDistanceOption", "1");
        if (alwaysDefault) params.put("alwaysDefaultLocationOption", "1");
        params.put("saveButton", "Sauvegarder les modifications");

        RequestBody formBody = NetworkUtils.createIsoFormBody(params);
        String url = ApiConstants.BASE_URL + ApiConstants.PATH_GEOLOCATION_OPTIONS;

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Referer", url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        if (callback != null) callback.onSuccess();
                    } else {
                        if (callback != null) callback.onFailure("Server error: " + r.code());
                    }
                }
            }
        });
    }
}
