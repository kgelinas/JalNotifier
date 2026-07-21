package io.github.kgelinas.jalfnotifier.worker;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


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
import io.github.kgelinas.jalfnotifier.util.NetworkUtils.Param;

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
                    Elements forms = doc.select("form");
                    if (forms.isEmpty()) {
                        Log.e(TAG, "No form found on profile edit page");
                        if (callback != null) callback.onFailure("Form not found");
                        return;
                    }
                    
                    Element profileForm = forms.first();
                    // Try to find the actual profile form if there are multiple (e.g. name="frmProfile")
                    for (Element f : forms) {
                        if (f.attr("name").toLowerCase().contains("profile") || f.attr("id").toLowerCase().contains("profile")) {
                            profileForm = f;
                            break;
                        }
                    }
                    
                    Log.d(TAG, "[GHOST] Updating fields: " + updateFields.keySet());

                    // 2. Extract all fields from the selected form
                    List<Param> params = new ArrayList<>();
                    Elements inputs = profileForm.select("input, select, textarea");

                    // Track which fields we've already handled from updateFields
                    java.util.Set<String> handledUpdates = new java.util.HashSet<>();

                    for (Element input : inputs) {
                        String name = input.attr("name");
                        if (name.isEmpty()) continue;

                        // If this field is being updated, we skip the extraction and add our new value later
                        if (updateFields.containsKey(name)) {
                            if (!handledUpdates.contains(name)) {
                                String newVal = updateFields.get(name);
                                params.add(new Param(name, newVal));
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
                                    String selVal = selectedOpt.attr("value");
                                    params.add(new Param(name, selVal));
                                } else {
                                    Element firstOpt = input.selectFirst("option");
                                    if (firstOpt != null) {
                                        params.add(new Param(name, firstOpt.attr("value")));
                                    }
                                }
                            } else if (tagName.equals("textarea")) {
                                params.add(new Param(name, input.text()));
                            } else if (!type.equals("submit") && !type.equals("button")) {
                                String inputVal = input.attr("value");
                                params.add(new Param(name, inputVal));
                            }
                        }
                    }

                    // Add any updates that weren't found in the form (unlikely but log it)
                    for (Map.Entry<String, String> entry : updateFields.entrySet()) {
                        if (!handledUpdates.contains(entry.getKey())) {
                            params.add(new Param(entry.getKey(), entry.getValue()));
                        }
                    }

                    // --- FANT_LIST: fetch from REST to avoid overwriting user's fantasy selection ---
                    // The HTML form's fantasy checkboxes are unreliable for reconstruction.
                    // We read the real list from GET /rest/users/{userId} → fantasies_links.
                    String myUserId = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                            .getString(ApiConstants.KEY_USER_ID, "");

                    // Remove any fant_list already in params (may be empty/wrong from HTML)
                    params.removeIf(p -> p.name.equals("fant_list"));

                    // Build the final list reference so the inner callback can use it
                    final List<Param> finalParams = params;

                    java.util.concurrent.atomic.AtomicReference<String> fantListRef =
                            new java.util.concurrent.atomic.AtomicReference<>("");

                    // Helper to fire the actual POST once we have (or failed to get) fant_list
                    Runnable doPost = () -> {
                        String fantValue = fantListRef.get();
                        if (!fantValue.isEmpty()) {
                            finalParams.add(new Param("fant_list", fantValue));
                        } else {
                            Log.w(TAG, "[GHOST] fant_list could not be resolved from REST, server may reject POST");
                        }

                        RequestBody formBody = NetworkUtils.createIsoFormBody(finalParams);
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
                            public void onResponse(@NonNull Call call, @NonNull Response postResponse) throws IOException {
                                try (Response r = postResponse) {
                                    if (r.isSuccessful()) {
                                        String responseHtml = NetworkUtils.responseToString(r);
                                        Document resDoc = Jsoup.parse(responseHtml);
                                        Elements errors = resDoc.select(".error, .alert, .message");
                                        if (!errors.isEmpty()) {
                                            Log.e(TAG, "[GHOST] Profile update server error: " + errors.text());
                                        }
                                        if (callback != null) callback.onSuccess();
                                    } else {
                                        Log.e(TAG, "Profile update failed: " + r.code());
                                        if (callback != null) callback.onFailure("HTTP " + r.code());
                                    }
                                }
                            }
                        });
                    };

                    if (myUserId.isEmpty()) {
                        Log.w(TAG, "[GHOST] Own userId not known, cannot fetch fantasies from REST");
                        doPost.run();
                    } else {
                        String restUrl = ApiConstants.BASE_URL + "/rest/users/" + myUserId;
                        Request restReq = new Request.Builder()
                                .url(restUrl)
                                .addHeader("Cookie", fullCookie)
                                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                                .build();
                        client.newCall(restReq).enqueue(new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                Log.w(TAG, "[GHOST] REST profile fetch failed, posting without fant_list: " + e.getMessage());
                                doPost.run();
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response restResponse) throws IOException {
                                try (Response rr = restResponse) {
                                    if (rr.isSuccessful() && rr.body() != null) {
                                        try {
                                            org.json.JSONObject profile = new org.json.JSONObject(NetworkUtils.responseToString(rr));

                                            // ── Helper: extract numeric tail from a REST link ──────
                                            // e.g. "/rest/goals/3" → "3"

                                            // ── fantasies_links → fant_list (comma-separated) ─────
                                            org.json.JSONArray fantLinks = profile.optJSONArray("fantasies_links");
                                            if (fantLinks != null && fantLinks.length() > 0) {
                                                StringBuilder sb = new StringBuilder();
                                                for (int i = 0; i < fantLinks.length(); i++) {
                                                    String id = fantLinks.optString(i, "").replaceAll(".*/", "").trim();
                                                    if (!id.isEmpty() && id.matches("\\d+")) {
                                                        if (sb.length() > 0) sb.append(",");
                                                        sb.append(id);
                                                    }
                                                }
                                                fantListRef.set(sb.toString());
                                                Log.d(TAG, "[GHOST] fant_list from REST: " + fantListRef.get());
                                            } else {
                                                Log.w(TAG, "[GHOST] fantasies_links empty/null in REST response");
                                            }

                                            // ── Single-value link fields ──────────────────────────
                                            // REST field → HTML form field name
                                            String[][] singleMappings = {
                                                {"sex_link",                  "Sexe"},
                                                {"sexual_orientation_link",   "Ornt"},
                                                {"social_status_link",        "Marit"},
                                                {"occupation_link",           "Occp"},
                                                {"ethnic_group_link",         "Ethn"},
                                                {"schedule_available_link",   "Dispo"},
                                                {"smoking_link",              "Fumr"},
                                                {"zodiac_sign_link",          "Sign"},
                                                {"alcohol_use_link",          "Alcl"},
                                                {"drug_use_link",             "Drog"},
                                                {"height_link",               "Hght"},
                                                {"weight_link",               "Wght"},
                                            };
                                            for (String[] mapping : singleMappings) {
                                                String restField = mapping[0];
                                                String formField = mapping[1];
                                                if (updateFields.containsKey(formField)) continue; // caller is explicitly setting this
                                                String link = profile.optString(restField, "");
                                                if (!link.isEmpty()) {
                                                    String id = link.replaceAll(".*/", "").trim();
                                                    if (!id.isEmpty() && id.matches("\\d+")) {
                                                        finalParams.removeIf(p -> p.name.equals(formField));
                                                        finalParams.add(new Param(formField, id));
                                                        Log.d(TAG, "[GHOST] REST override: " + formField + "=" + id);
                                                    }
                                                }
                                            }

                                            // ── Multi-value link fields ───────────────────────────
                                            String[][] multiMappings = {
                                                {"goals_links",          "Goals"},
                                                {"sexes_interested_links", "Want"},
                                            };
                                            for (String[] mapping : multiMappings) {
                                                String restField = mapping[0];
                                                String formField = mapping[1];
                                                if (updateFields.containsKey(formField)) continue;
                                                org.json.JSONArray links = profile.optJSONArray(restField);
                                                if (links != null && links.length() > 0) {
                                                    List<String> ids = new ArrayList<>();
                                                    for (int i = 0; i < links.length(); i++) {
                                                        String id = links.optString(i, "").replaceAll(".*/", "").trim();
                                                        if (!id.isEmpty() && id.matches("\\d+")) ids.add(id);
                                                    }
                                                    if (!ids.isEmpty()) {
                                                        finalParams.removeIf(p -> p.name.equals(formField));
                                                        for (String id : ids) finalParams.add(new Param(formField, id));
                                                        Log.d(TAG, "[GHOST] REST override (multi): " + formField + "=" + ids);
                                                    }
                                                }
                                            }

                                        } catch (Exception ex) {
                                            Log.w(TAG, "[GHOST] Error parsing REST profile response", ex);
                                        }
                                    } else {
                                        Log.w(TAG, "[GHOST] REST profile fetch returned " + rr.code());
                                    }
                                }
                                doPost.run();
                            }
                        });
                    }
                    // -----------------------------------------------------------
                }
            }
        });
    }
}
