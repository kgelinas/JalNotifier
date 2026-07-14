package io.github.kgelinas.jalfnotifier.util;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import com.google.android.material.color.DynamicColors;
import com.google.crypto.tink.aead.AeadConfig;
import java.security.GeneralSecurityException;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public class JalfNotifierApplication extends Application {

    // ── Shared OkHttpClient ───────────────────────────────────────────────────
    // OkHttp is designed to be shared. One instance owns one connection pool,
    // one thread pool, and one cache. All callers must use this or derive from it
    // via .newBuilder() for custom timeouts.
    private static OkHttpClient sharedHttpClient;

    /**
     * Returns the application-wide OkHttpClient.
     * Components that need custom timeouts (SSE, photo upload) should call
     * {@code httpClient().newBuilder().readTimeout(…).build()} and keep it for
     * their own lifetime — they share the underlying connection pool.
     */
    public static OkHttpClient httpClient() {
        return sharedHttpClient;
    }

    // ── Shared IO thread pool ─────────────────────────────────────────────────
    // Replaces all bare `new Thread(runnable).start()` calls in the codebase.
    // A cached pool is appropriate because tasks are short-lived I/O bursts.
    public static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();

        // Build the shared client once.
        sharedHttpClient = new OkHttpClient.Builder().build();

        // Material You
        DynamicColors.applyToActivitiesIfAvailable(this);

        // Register all notification channels eagerly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels();
        }

        try {
            AeadConfig.register();
        } catch (GeneralSecurityException e) {
            Log.e("JalfNotifierApp", "Failed to register Tink", e);
        }
    }

    private void createNotificationChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);

        NotificationChannel chat = new NotificationChannel(
                "jal_chat_notifications",
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH);
        chat.setDescription("New and active message conversations from jalf.com");

        NotificationChannel events = new NotificationChannel(
                "jal_event_notifications",
                "Events",
                NotificationManager.IMPORTANCE_DEFAULT);
        events.setDescription("Likes, pokes, and other profile events from jalf.com");

        NotificationChannel online = new NotificationChannel(
                "jal_online_notifications",
                "Favorites Online",
                NotificationManager.IMPORTANCE_DEFAULT);
        online.setDescription("Alerts when a favorite member comes online");

        nm.createNotificationChannel(chat);
        nm.createNotificationChannel(events);
        nm.createNotificationChannel(online);
    }
}
