package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import io.github.kgelinas.jalfnotifier.NetworkUtils.Param;

public class ProfileUpdateTask {
    private static final String TAG = "ProfileUpdateTask";
    private static final OkHttpClient client = JalfNotifierApplication.httpClient();

    public interface UpdateCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void updateProfileField(Context context, String fieldName, String value, UpdateCallback callback) {
        Map<String, String> fields = new HashMap<>();
        fields.put(fieldName, value);
        updateProfileFields(context, fields, callback);
    }

    public static void updateProfileFields(Context context, Map<String, String> updateFields, UpdateCallback callback) {
        SecurePrefs secure = SecurePrefs.get(context);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty()) {
            if (callback != null) callback.onFailure("Not logged in");
            return;
        }

        // 1. Fetch current profile page to get all parameters
        Request fetchRequest = new Request.Builder()
                .url(ApiConstants.BASE_URL + ApiConstants.PATH_PROFILE_EDIT)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(fetchRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch profile page", e);
                if (callback != null) callback.onFailure("Fetch failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        Log.e(TAG, "Fetch profile page error: " + r.code());
                        if (callback != null) callback.onFailure("Server error during fetch: " + r.code());
                        return;
                    }

                    // Decode using NetworkUtils
                    String html = NetworkUtils.responseToString(r);
                    Document doc = Jsoup.parse(html, ApiConstants.BASE_URL + ApiConstants.PATH_PROFILE_EDIT);
                    Element form = doc.selectFirst("form");
                    if (form == null) {
                        Log.e(TAG, "Profile form not found in HTML");
                        if (callback != null) callback.onFailure("Profile form not found");
                        return;
                    }

                    // 2. Extract all fields from the form
                    List<Param> params = new ArrayList<>();
                    Elements inputs = form.select("input, select, textarea");
                    
                    // Track which fields we've already handled from updateFields
                    java.util.Set<String> handledUpdates = new java.util.HashSet<>();

                    for (Element input : inputs) {
                        String name = input.attr("name");
                        if (name.isEmpty()) continue;

                        // If this field is being updated, we skip the extraction and add our new value later
                        if (updateFields.containsKey(name)) {
                            if (!handledUpdates.contains(name)) {
                                params.add(new Param(name, updateFields.get(name)));
                                handledUpdates.add(name);
                            }
                        } else {
                            String tagName = input.tagName().toLowerCase();
                            String type = input.attr("type").toLowerCase();

                            if (type.equals("checkbox") || type.equals("radio")) {
                                if (input.hasAttr("checked")) {
                                    params.add(new Param(name, input.attr("value")));
                                }
                            } else if (tagName.equals("select")) {
                                Element selectedOpt = input.selectFirst("option[selected]");
                                if (selectedOpt != null) {
                                    params.add(new Param(name, selectedOpt.attr("value")));
                                } else {
                                    // Default to the first option if none are explicitly selected
                                    Element firstOpt = input.selectFirst("option");
                                    if (firstOpt != null) {
                                        params.add(new Param(name, firstOpt.attr("value")));
                                    }
                                }
                            } else if (tagName.equals("textarea")) {
                                params.add(new Param(name, input.text()));
                            } else if (!type.equals("submit") && !type.equals("button")) {
                                params.add(new Param(name, input.attr("value")));
                            }
                        }
                    }

                    // Add any updates that weren't found in the form (unlikely)
                    for (Map.Entry<String, String> entry : updateFields.entrySet()) {
                        if (!handledUpdates.contains(entry.getKey())) {
                            params.add(new Param(entry.getKey(), entry.getValue()));
                        }
                    }

                    // 3. POST the full form back using ISO-8859-1
                    RequestBody formBody = NetworkUtils.createIsoFormBody(params);
                    
                    Request postRequest = new Request.Builder()
                            .url(ApiConstants.BASE_URL + ApiConstants.PATH_PROFILE_EDIT)
                            .post(formBody)
                            .addHeader("Cookie", fullCookie)
                            .addHeader("User-Agent", ApiConstants.USER_AGENT)
                            .addHeader("Referer", ApiConstants.BASE_URL + ApiConstants.PATH_PROFILE_EDIT)
                            .addHeader("Origin", "https://m-app.jalf.com")
                            .build();

                    client.newCall(postRequest).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.e(TAG, "Profile post update failed", e);
                            if (callback != null) callback.onFailure(e.getMessage());
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            try (Response postR = response) {
                                if (postR.isSuccessful()) {
                                    Log.d(TAG, "Profile full update successful");
                                    if (callback != null) callback.onSuccess();
                                } else {
                                    Log.e(TAG, "Profile full update failed with code: " + postR.code());
                                    if (callback != null) callback.onFailure("Server error: " + postR.code());
                                }
                            }
                        }
                    });
                }
            }
        });
    }
}
