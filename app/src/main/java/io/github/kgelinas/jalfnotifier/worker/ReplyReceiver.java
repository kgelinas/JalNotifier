package io.github.kgelinas.jalfnotifier.worker;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReplyReceiver extends BroadcastReceiver {

    private static final String TAG = "JALFReplyReceiver";
    // BroadcastReceivers are instantiated per-broadcast, so we must NOT store
    // an OkHttpClient as an instance field — use the shared application client.
    private static OkHttpClient client() { return JalfNotifierApplication.httpClient(); }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ApiConstants.ACTION_REPLY.equals(intent.getAction()))
            return;

        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence replyText = remoteInput.getCharSequence(ApiConstants.EXTRA_REMOTE_INPUT_KEY);
            String conversationLink = intent.getStringExtra(ApiConstants.EXTRA_CONVERSATION_LINK);

            if (replyText != null && conversationLink != null) {
                sendReply(context, conversationLink, replyText.toString());
            }
        }
    }

    private void sendReply(Context context, String conversationLink, String text) {
        SecurePrefs secure = SecurePrefs.get(context);
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        String cookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");

        if (suid.isEmpty() || cookie.isEmpty()) {
            Log.e(TAG, "Missing credentials for reply");
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("text", text);
            payload.put("ephemeral", false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build reply payload", e);
        }

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.newmessage.text+json"));

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(conversationLink))
                .post(body)
                .addHeader("Cookie", cookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send reply", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        Log.d(TAG, "Reply sent successfully");
                        updateNotification(context, conversationLink, text);
                    } else {
                        Log.e(TAG, "Reply failed: " + r.code());
                        // On failure, we should also update to stop spinner
                        updateNotification(context, conversationLink, context.getString(R.string.reply_failed_prefix, text));
                    }
                }
            }
        });
    }

    private String getAbsoluteUrl(String path) {
        if (path == null)
            return "";
        if (path.startsWith("http"))
            return path;
        return ApiConstants.BASE_URL + path;
    }

    private void updateNotification(Context context, String conversationLink, String statusText) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "jal_chat_notifications";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, context.getString(R.string.chat_notifications), NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentText(statusText)
                .setRemoteInputHistory(new CharSequence[] { statusText }) // Shows the reply text below the notification
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(conversationLink.hashCode(), builder.build());
    }
}
