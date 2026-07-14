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

public class MarkAsReadReceiver extends BroadcastReceiver {
    private static final String TAG = "MarkAsReadReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String conversationLink = intent.getStringExtra(ApiConstants.EXTRA_CONVERSATION_LINK);
        if (conversationLink != null) {
            Log.d(TAG, "Marking conversation as read: " + conversationLink);
            
            // Cancel the notification
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String convoIdStr = StringUtils.extractNumericId(conversationLink);
            int notificationId = convoIdStr.isEmpty() ? conversationLink.hashCode() : convoIdStr.hashCode();
            notificationManager.cancel(notificationId);
            
            // Note: In a real app, we would also call the API to mark as read on the server.
            // But JALF marks as read when fetching the conversation, so just clearing the notification is often enough.
        }
    }
}
