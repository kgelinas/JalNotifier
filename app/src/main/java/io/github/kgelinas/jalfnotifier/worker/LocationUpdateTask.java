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
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LocationUpdateTask {
    private static final String TAG = "LocationUpdateTask";
    private static final OkHttpClient client = JalfNotifierApplication.httpClient();
    private static final MediaType JSON_LOCATION = MediaType.parse("application/vnd.jalf.user.position+json");

    public interface LocationCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void sendLocation(Context context, double lat, double lon, LocationCallback callback) {
        SecurePrefs secure = SecurePrefs.get(context);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        String userId = secure.getString(ApiConstants.KEY_USER_ID, "");

        if (fullCookie.isEmpty() || userId.isEmpty()) {
            if (callback != null) callback.onFailure("Not logged in");
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("latitude", lat);
            json.put("longitude", lon);
            json.put("error", JSONObject.NULL);

            String url = ApiConstants.BASE_URL + "/rest/users/" + userId;
            
            RequestBody body = RequestBody.create(json.toString(), JSON_LOCATION);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Cookie", fullCookie)
                    .addHeader("x-csrftoken", suid)
                    .addHeader("User-Agent", ApiConstants.USER_AGENT)
                    .addHeader("Accept", "*/*")
                    .addHeader("Origin", ApiConstants.BASE_URL)
                    .addHeader("Referer", ApiConstants.BASE_URL + "/")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to send location", e);
                    if (callback != null) callback.onFailure(e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response r = response) {
                        if (r.isSuccessful()) {
                            Log.d(TAG, "Location updated successfully: " + lat + ", " + lon);
                            context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putLong(ApiConstants.KEY_LAST_LOCATION_UPDATE, System.currentTimeMillis())
                                    .apply();
                            if (callback != null) callback.onSuccess();
                        } else {
                            String error = "Server error: " + r.code();
                            Log.e(TAG, error);
                            if (callback != null) callback.onFailure(error);
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error building location JSON", e);
            if (callback != null) callback.onFailure(e.getMessage());
        }
    }
}
