package io.github.kgelinas.jalfnotifier.worker;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MarkAsReadReceiver extends BroadcastReceiver {
    private static final String TAG = "MarkAsReadReceiver";

    private static OkHttpClient client() {
        return JalfNotifierApplication.httpClient();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String conversationLink = intent.getStringExtra(ApiConstants.EXTRA_CONVERSATION_LINK);
        if (conversationLink == null || conversationLink.isEmpty()) {
            return;
        }

        Log.d(TAG, "Marking conversation as read: " + conversationLink);

        // Cancel the notification immediately
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String convoIdStr = StringUtils.extractNumericId(conversationLink);
        int notificationId = convoIdStr.isEmpty() ? conversationLink.hashCode() : convoIdStr.hashCode();
        if (notificationManager != null) {
            notificationManager.cancel(notificationId);
        }

        // Send read receipt to the server
        Context appContext = context.getApplicationContext();
        SecurePrefs secure = SecurePrefs.get(appContext);
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        String cookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");

        if (suid.isEmpty() || cookie.isEmpty()) {
            Log.e(TAG, "Missing credentials for mark as read");
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("read_until", "now");
        } catch (Exception e) {
            AppLogger.log(TAG, "Error building read_until payload", e);
        }

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.conversationchange+json; charset=utf-8"));

        String url = conversationLink.startsWith("http") ? conversationLink : ApiConstants.BASE_URL + conversationLink;
        Request request = new Request.Builder()
                .url(url)
                .patch(body)
                .addHeader("Cookie", cookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Accept", "*/*")
                .build();

        final PendingResult pendingResult = goAsync();
        client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                try {
                    Log.e(TAG, "Failed to mark conversation as read on server", e);
                } finally {
                    pendingResult.finish();
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean success;
                try (Response r = response) {
                    success = r.isSuccessful();
                    Log.d(TAG, "Mark as read POST response status: " + r.code());
                }
                if (!success) {
                    // Fallback: fetching the conversation via GET also marks it read on JALF
                    Request getReq = new Request.Builder()
                            .url(url + (url.contains("?") ? "&count=1" : "?count=1"))
                            .get()
                            .addHeader("Cookie", cookie)
                            .addHeader("x-csrftoken", suid)
                            .addHeader("User-Agent", ApiConstants.USER_AGENT)
                            .addHeader("Accept", "application/vnd.jalf.convo.conversation+json; charset=utf-8")
                            .build();
                    try (Response getResp = client().newCall(getReq).execute()) {
                        Log.d(TAG, "Mark as read GET fallback status: " + getResp.code());
                    } catch (Exception e) {
                        Log.e(TAG, "Mark as read GET fallback failed", e);
                    } finally {
                        pendingResult.finish();
                    }
                } else {
                    pendingResult.finish();
                }
            }
        });
    }
}
