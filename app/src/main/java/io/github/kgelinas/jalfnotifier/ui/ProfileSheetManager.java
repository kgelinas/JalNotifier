package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.databinding.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProfileSheetManager {

    private static final String TAG = "ProfileSheetManager";

    public static void showProfileSheet(MainActivity activity) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(activity);
        BottomSheetUtils.setupFullHeight(bottomSheet);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_profile, null);

        ImageView avatar = view.findViewById(R.id.profile_sheet_avatar);
        View onlineIndicator = view.findViewById(R.id.profile_sheet_online_indicator);
        TextView name = view.findViewById(R.id.profile_sheet_name);
        TextView details = view.findViewById(R.id.profile_sheet_details);
        TextView location = view.findViewById(R.id.profile_sheet_location);

        activity.setCurrentProfileSheet(bottomSheet);

        AppPrefs prefs = AppPrefs.getInstance(activity);
        boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);
        com.bumptech.glide.request.RequestOptions sheetOptions = new com.bumptech.glide.request.RequestOptions()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .circleCrop();

        if (blurNsfw && activity.getMyNsfwRank() > 0) {
            sheetOptions = sheetOptions.transform(new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
        }

        String myAvatarUrl = activity.getMyAvatarUrl();
        if (myAvatarUrl != null && !myAvatarUrl.isEmpty()) {
            Glide.with(activity).load(myAvatarUrl).apply(sheetOptions).into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_default_avatar);
        }
        onlineIndicator.setVisibility(activity.isMyOnline() ? View.VISIBLE : View.GONE);
        name.setText(activity.getMyName());
        details.setText(activity.getMyDetails());
        location.setText(activity.getMyLocation());

        // Status Row Initialization
        View statusLayout = view.findViewById(R.id.profile_sheet_status_layout);
        TextView statusText = view.findViewById(R.id.profile_sheet_status_text);
        ImageButton statusEdit = view.findViewById(R.id.profile_sheet_status_edit);
        ImageButton statusDelete = view.findViewById(R.id.profile_sheet_status_delete);

        final String[] myStatusId = {null};
        final String[] myStatusText = {null};

        // Initialize status text to hint
        statusText.setText(R.string.profile_sheet_status_hint);
        statusText.setTypeface(null, android.graphics.Typeface.ITALIC);
        statusDelete.setVisibility(View.GONE);

        // Fetch own profile page in the background to get current status and status delete ID
        String myUserId = activity.getMyUserId();
        if (myUserId != null && !myUserId.isEmpty()) {
            SecurePrefs secure = SecurePrefs.get(activity);
            String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
            if (!fullCookie.isEmpty()) {
                String profileUrl = ApiConstants.BASE_URL + "/ct/memberProfile/" + myUserId + "/1";
                Request profileRequest = new Request.Builder()
                        .url(profileUrl)
                        .addHeader("Cookie", fullCookie)
                        .addHeader("User-Agent", ApiConstants.USER_AGENT)
                        .build();

                JalfNotifierApplication.httpClient().newCall(profileRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Error fetching own profile for status", e);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try (Response r = response) {
                            if (r.isSuccessful() && r.body() != null) {
                                String body = NetworkUtils.responseToString(r);
                                Document doc = Jsoup.parse(body);

                                // Extract status message text
                                String statusMsg = "";
                                Element statusP = doc.selectFirst("p.last_status");
                                if (statusP != null) {
                                    statusMsg = statusP.text().trim();
                                } else {
                                    Element statusDesc = doc.getElementById("lastStatusDesc");
                                    if (statusDesc != null) {
                                        statusMsg = statusDesc.text().trim();
                                    }
                                }

                                // Extract status ID using confirmDeleteStatus(NNN) pattern
                                String extractedId = extractStatusIdFromHtml(body);
                                Log.d(TAG, "Initial status load — msg=" + statusMsg + " id=" + extractedId);

                                final String finalStatusMsg = statusMsg;
                                final String finalExtractedId = extractedId;
                                activity.runOnUiThread(() -> {
                                    myStatusText[0] = finalStatusMsg;
                                    myStatusId[0] = finalExtractedId;
                                    updateStatusUi(statusText, statusDelete, finalStatusMsg, finalExtractedId);
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing status from profile HTML", e);
                        }
                    }
                });
            }
        }

        Runnable showEditDialog = () -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.status_edit_title);

            final EditText input = new EditText(activity);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            String current = myStatusText[0];
            if (current != null && !current.isEmpty()) {
                input.setText(current);
                input.setSelection(current.length());
            }

            FrameLayout container = new FrameLayout(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = (int) (16 * activity.getResources().getDisplayMetrics().density);
            params.leftMargin = margin;
            params.rightMargin = margin;
            params.topMargin = margin;
            params.bottomMargin = margin;
            input.setLayoutParams(params);
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton(R.string.save, (dialog, which) -> {
                String newStatus = input.getText().toString().trim();
                saveStatus(activity, newStatus, statusText, statusDelete, myStatusText, myStatusId);
            });
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
            builder.show();
        };

        statusLayout.setOnClickListener(v -> showEditDialog.run());
        statusEdit.setOnClickListener(v -> showEditDialog.run());

        statusDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.status_delete_confirm_title)
                    .setMessage(R.string.status_delete_confirm_msg)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        deleteStatus(activity, statusText, statusDelete, myStatusText, myStatusId);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        avatar.setOnClickListener(v -> {
            bottomSheet.dismiss();
            activity.openProfile(activity.getMyUserId(), activity.getMyName(), activity.getMyAvatarUrl());
        });

        // Setup SettingsViewModel for toggles
        SettingsViewModel settingsVm = new androidx.lifecycle.ViewModelProvider(activity).get(SettingsViewModel.class);

        // --- Icon Buttons ---
        ImageButton btnAppearOffline = view.findViewById(R.id.btn_profile_appear_offline);
        ImageButton btnReadReceipts = view.findViewById(R.id.btn_profile_read_receipts);
        ImageButton btnNsfw = view.findViewById(R.id.btn_profile_nsfw);
        ImageButton btnGeolocation = view.findViewById(R.id.btn_profile_geolocation);
        View btnSettings = view.findViewById(R.id.btn_profile_settings);
        ImageButton btnCleanup = view.findViewById(R.id.btn_profile_cleanup);
        View btnLogout = view.findViewById(R.id.btn_profile_logout);

        // Tooltips
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnAppearOffline, activity.getString(R.string.appear_offline));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnReadReceipts, activity.getString(R.string.read_receipts));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnNsfw, activity.getString(R.string.nsfw_blur));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnGeolocation, activity.getString(R.string.geolocation));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnSettings, activity.getString(R.string.settings));
        if (btnCleanup != null) {
            androidx.appcompat.widget.TooltipCompat.setTooltipText(btnCleanup, activity.getString(R.string.action_cleanup_threads));
        }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnLogout, activity.getString(R.string.logout));

        // --- States and Listeners ---

        // 1. Appear Offline (Ghost Mode)
        boolean appearOffline = prefs.getBoolean(ApiConstants.KEY_APPEAR_OFFLINE, false);
        updateToggleButton(activity, btnAppearOffline, appearOffline);
        btnAppearOffline.setOnClickListener(v -> {
            boolean newState = !prefs.getBoolean(ApiConstants.KEY_APPEAR_OFFLINE, false);
            prefs.edit()
                .putBoolean(ApiConstants.KEY_APPEAR_OFFLINE, newState)
                .putBoolean(ApiConstants.KEY_WAS_AUTO_OFFLINED, false) // Explicitly clear auto flag on manual change
                .apply();
            updateToggleButton(activity, btnAppearOffline, newState);
            
            // Immediately cancel any pending background auto-offline tasks
            androidx.work.WorkManager.getInstance(activity).cancelAllWorkByTag("AutoOfflineWork");

            java.util.Map<String, String> fields = new java.util.HashMap<>();
            fields.put("Visible", newState ? "no" : "yes");
            fields.put("save", "Enregistrer");
            ProfileUpdateTask.updateProfileFields(activity, fields, new ProfileUpdateTask.UpdateCallback() {
                @Override
                public void onSuccess() {
                    String expectedVisible = newState ? "no" : "yes";
                    android.util.Log.w("GhostMode", "[GHOST] POST succeeded. Expected Visible=" + expectedVisible
                            + ". Fetching status in 1.5s to verify...");
                    // Wait briefly for the server to apply the change, then refresh indicator
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                            activity::fetchUserStatus, 1500);

                    // Separately verify the Visible field was actually stored on the server
                    verifyVisibleOnServer(activity, expectedVisible);
                }

                @Override
                public void onFailure(String error) {
                    android.util.Log.e("GhostMode", "[GHOST] POST failed: " + error);
                }
            });
        });

        // 2. Read Receipts
        settingsVm.sendReadReceipts.observe(activity, active -> updateToggleButton(activity, btnReadReceipts, active));
        btnReadReceipts.setOnClickListener(v -> settingsVm.setSendReadReceipts(!Boolean.TRUE.equals(settingsVm.sendReadReceipts.getValue())));

        // 3. NSFW Blur
        settingsVm.blurNsfw.observe(activity, active -> updateToggleButton(activity, btnNsfw, active));
        btnNsfw.setOnClickListener(v -> settingsVm.setBlurNsfw(!Boolean.TRUE.equals(settingsVm.blurNsfw.getValue())));

        // 4. Geolocation
        settingsVm.remoteGeolocation.observe(activity, active -> updateToggleButton(activity, btnGeolocation, active));
        btnGeolocation.setOnClickListener(v -> settingsVm.setRemoteGeolocation(!Boolean.TRUE.equals(settingsVm.remoteGeolocation.getValue())));

        // 5. Settings
        btnSettings.setOnClickListener(v -> {
            bottomSheet.dismiss();
            activity.startActivity(new Intent(activity, SettingsActivity.class));
        });

        // 5.5 Clean Up
        if (btnCleanup != null) {
            btnCleanup.setOnClickListener(v -> {
                bottomSheet.dismiss();
                Intent intent = new Intent(activity, MainActivity.class);
                intent.setAction("CLEANUP_CHATS");
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
            });
        }

        // 6. Logout
        btnLogout.setOnClickListener(v -> {
            bottomSheet.dismiss();
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.logout_confirm_title)
                    .setMessage(R.string.logout_message)
                    .setPositiveButton(R.string.logout_confirm_title, (d, w) -> activity.performLogout())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        bottomSheet.setContentView(view);
        bottomSheet.setOnShowListener(d -> {
            View bsView = bottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bsView != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bsView);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
            AppTourManager.getInstance().onProfileSheetShown();
        });
        bottomSheet.show();
    }

    /**
     * Re-fetches the profile edit page after a ghost mode change and scrapes the
     * Visible field to confirm the server actually applied the new value.
     * Logs a warning if the server value doesn't match what we sent.
     */
    private static void verifyVisibleOnServer(MainActivity activity, String expectedVisible) {
        SecurePrefs secure = SecurePrefs.get(activity);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        if (fullCookie.isEmpty()) return;

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(ApiConstants.BASE_URL + ApiConstants.PATH_PROFILE_EDIT)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        JalfNotifierApplication.httpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull java.io.IOException e) {
                android.util.Log.e(TAG, "[GHOST] verifyVisible fetch failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {
                try (okhttp3.Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        android.util.Log.e(TAG, "[GHOST] verifyVisible HTTP error: " + r.code());
                        return;
                    }
                    String html = NetworkUtils.responseToString(r);
                    Document doc = Jsoup.parse(html);
                    org.jsoup.select.Elements visibleEls = doc.select("[name=Visible]");
                    if (visibleEls.isEmpty()) {
                        android.util.Log.w(TAG, "[GHOST] verifyVisible: Element [name=Visible] not found in profile form at all.");
                        return;
                    }
                    
                    Element visibleEl = visibleEls.first();
                    String actualVisible = "(unknown)";
                    if (visibleEl.tagName().equals("select")) {
                        Element selected = visibleEl.selectFirst("option[selected]");
                        actualVisible = selected != null ? selected.attr("value") : "(none selected)";
                    } else if (visibleEl.tagName().equals("input") && (visibleEl.attr("type").equals("radio") || visibleEl.attr("type").equals("checkbox"))) {
                        Element checked = doc.selectFirst("input[name=Visible][checked]");
                        actualVisible = checked != null ? checked.attr("value") : "(none checked)";
                    } else {
                        actualVisible = visibleEl.attr("value");
                    }
                    
                    if (actualVisible.equals(expectedVisible)) {
                        android.util.Log.d(TAG, "[GHOST] verifyVisible: Server confirmed Visible=" + actualVisible);
                    } else {
                        android.util.Log.w(TAG, "[GHOST] verifyVisible: MISMATCH! Expected Visible="
                                + expectedVisible + " but server has Visible=" + actualVisible);
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "[GHOST] verifyVisible parse error: " + e.getMessage());
                }
            }
        });
    }

    private static void updateStatusUi(TextView statusText, ImageButton statusDelete, String msg, String statusId) {
        if (msg == null || msg.trim().isEmpty()) {
            statusText.setText(R.string.profile_sheet_status_hint);
            statusText.setTypeface(null, android.graphics.Typeface.ITALIC);
            statusDelete.setVisibility(View.GONE);
        } else {
            statusText.setText(msg);
            statusText.setTypeface(null, android.graphics.Typeface.NORMAL);
            statusDelete.setVisibility(View.VISIBLE);
        }
    }

    private static void saveStatus(MainActivity activity, String newStatus, TextView statusText, ImageButton statusDelete, String[] myStatusText, String[] myStatusId) {
        SecurePrefs secure = SecurePrefs.get(activity);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        if (fullCookie.isEmpty()) return;

        RequestBody formBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("action", "new")
                .addFormDataPart("stat_msg", newStatus)
                .build();

        String profileUrl = ApiConstants.BASE_URL + "/ct/memberProfile/" + activity.getMyUserId() + "/1";
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/users_status")
                .post(formBody)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Referer", profileUrl)
                .addHeader("Origin", ApiConstants.BASE_URL)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("x-requested-with", "XMLHttpRequest")
                .build();

        JalfNotifierApplication.httpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error updating status", e);
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.status_update_error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String responseBody = r.body().string();
                        Log.d(TAG, "saveStatus response: " + responseBody);

                        String idFromResponse = extractStatusIdFromHtml(responseBody);
                        final String capturedId = idFromResponse;
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, R.string.status_update_success, Toast.LENGTH_SHORT).show();
                            myStatusText[0] = newStatus;
                            if (capturedId != null) {
                                myStatusId[0] = capturedId;
                                Log.d(TAG, "Status ID captured from save response: " + capturedId);
                            } else {
                                Log.w(TAG, "Could not parse status ID from save response, will refresh");
                            }
                            updateStatusUi(statusText, statusDelete, newStatus, myStatusId[0]);
                        });

                        if (capturedId == null) {
                            refreshStatusId(activity, statusText, statusDelete, myStatusText, myStatusId);
                        }
                    } else {
                        Log.e(TAG, "Status update failed with code: " + r.code());
                        activity.runOnUiThread(() -> Toast.makeText(activity, R.string.status_update_error, Toast.LENGTH_SHORT).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling save status response", e);
                }
            }
        });
    }

    private static void deleteStatus(MainActivity activity, TextView statusText, ImageButton statusDelete, String[] myStatusText, String[] myStatusId) {
        SecurePrefs secure = SecurePrefs.get(activity);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        if (fullCookie.isEmpty()) return;

        if (myStatusId[0] == null || myStatusId[0].isEmpty()) {
            String profileUrl = ApiConstants.BASE_URL + "/ct/memberProfile/" + activity.getMyUserId() + "/1";
            Request profileRequest = new Request.Builder()
                    .url(profileUrl)
                    .addHeader("Cookie", fullCookie)
                    .addHeader("User-Agent", ApiConstants.USER_AGENT)
                    .build();
            JalfNotifierApplication.httpClient().newCall(profileRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to fetch status ID before delete", e);
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.error_network, Toast.LENGTH_SHORT).show());
                }
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response r = response) {
                        if (r.isSuccessful() && r.body() != null) {
                            String body = NetworkUtils.responseToString(r);
                            Document doc = Jsoup.parse(body);
                            String extractedId = extractStatusId(doc);
                            if (extractedId != null) {
                                myStatusId[0] = extractedId;
                                deleteStatus(activity, statusText, statusDelete, myStatusText, myStatusId);
                            } else {
                                Log.w(TAG, "Could not resolve status ID for deletion");
                                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.status_update_error, Toast.LENGTH_SHORT).show());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing status ID", e);
                    }
                }
            });
            return;
        }

        String statusId = myStatusId[0];
        RequestBody formBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("action", "delete")
                .addFormDataPart("id", statusId)
                .build();

        String profileUrl = ApiConstants.BASE_URL + "/ct/memberProfile/" + activity.getMyUserId() + "/1";
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/users_status")
                .post(formBody)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Referer", profileUrl)
                .addHeader("Origin", ApiConstants.BASE_URL)
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                .addHeader("x-requested-with", "XMLHttpRequest")
                .build();

        JalfNotifierApplication.httpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error deleting status", e);
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.error_network, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, R.string.status_delete_success, Toast.LENGTH_SHORT).show();
                            myStatusText[0] = null;
                            myStatusId[0] = null;
                            updateStatusUi(statusText, statusDelete, null, null);
                        });
                    } else {
                        Log.e(TAG, "Status delete failed with code: " + r.code());
                        activity.runOnUiThread(() -> Toast.makeText(activity, R.string.error_server, Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

    private static void refreshStatusId(MainActivity activity, TextView statusText, ImageButton statusDelete, String[] myStatusText, String[] myStatusId) {
        String myUserId = activity.getMyUserId();
        if (myUserId == null || myUserId.isEmpty()) return;
        SecurePrefs secure = SecurePrefs.get(activity);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        if (fullCookie.isEmpty()) return;

        String profileUrl = ApiConstants.BASE_URL + "/ct/memberProfile/" + myUserId + "/1";
        Request profileRequest = new Request.Builder()
                .url(profileUrl)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        JalfNotifierApplication.httpClient().newCall(profileRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error refreshing status ID after update", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String body = NetworkUtils.responseToString(r);
                        Document doc = Jsoup.parse(body);
                        final String finalExtractedId = extractStatusId(doc);
                        activity.runOnUiThread(() -> {
                            myStatusId[0] = finalExtractedId;
                            updateStatusUi(statusText, statusDelete, myStatusText[0], finalExtractedId);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing status ID in refreshStatusId", e);
                }
            }
        });
    }

    @Nullable
    private static String extractStatusIdFromHtml(String html) {
        if (html == null || html.isEmpty()) return null;

        java.util.regex.Matcher m1 = java.util.regex.Pattern
                .compile("confirmDeleteStatus\\((\\d+)\\)")
                .matcher(html);
        if (m1.find()) {
            return m1.group(1);
        }

        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("id=\\\"status_(\\d+)\\\"")
                .matcher(html);
        if (m2.find()) {
            return m2.group(1);
        }

        java.util.regex.Matcher m3 = java.util.regex.Pattern
                .compile("editUserStatus\\((\\d+)\\)")
                .matcher(html);
        if (m3.find()) {
            return m3.group(1);
        }

        return null;
    }

    @Nullable
    private static String extractStatusId(Document doc) {
        String rawHtml = doc.html();
        String id = extractStatusIdFromHtml(rawHtml);
        if (id != null) return id;

        for (Element el : doc.select("a[href*=users_status]")) {
            String href = el.attr("href");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]id=(\\d+)").matcher(href);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static void updateToggleButton(MainActivity activity, ImageButton btn, boolean active) {
        TypedValue typedValue = new TypedValue();
        if (active) {
            activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
            int colorPrimary = typedValue.data;
            
            btn.setBackgroundResource(R.drawable.circle_toggle_bg);
            if (btn.getBackground() != null) {
                btn.getBackground().mutate().setTint(colorPrimary);
                btn.getBackground().setAlpha(40);
            }
            btn.setColorFilter(colorPrimary);
        } else {
            activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
            btn.setBackgroundResource(0);
            btn.setColorFilter(typedValue.data);
        }
    }
}
