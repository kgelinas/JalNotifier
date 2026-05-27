package io.github.kgelinas.jalfnotifier;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

public class JalfSseService extends Service {
    private static final String TAG = "JalfSseService";
    private static final String CHANNEL_ID = "jalf_sse_channel";
    private static final int NOTIFICATION_ID = 4001;

    // ── Reconnect backoff config ──────────────────────────────────────────────
    private static final long RECONNECT_DELAY_MIN_MS = 5_000L;   // 5 s
    private static final long RECONNECT_DELAY_MAX_MS = 60_000L;  // 60 s
    private long reconnectDelayMs = RECONNECT_DELAY_MIN_MS;

    // ── Inactivity watchdog ───────────────────────────────────────────────────
    /** Max time (ms) between events before we suspect the stream is silently dead. */
    private static final long INACTIVITY_TIMEOUT_MS = 3 * 60_000L; // 3 min
    /** How often the watchdog fires to check for inactivity. */
    private static final long WATCHDOG_INTERVAL_MS = 90_000L;       // 90 s
    private final AtomicLong lastEventTimeMs = new AtomicLong(0);

    // ── OkHttp / SSE ─────────────────────────────────────────────────────────
    private EventSource currentEventSource;
    // SSE needs readTimeout(0) to keep the stream open indefinitely; derive from
    // the shared pool so we don't create a separate connection pool.
    private final OkHttpClient client = JalfNotifierApplication.httpClient()
            .newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    // ── Executor (single thread — avoids main-looper reconnects) ─────────────
    private ScheduledExecutorService sseExecutor;
    private ScheduledFuture<?> debounceFuture;
    private ScheduledFuture<?> inactivityCheckFuture;
    private ScheduledFuture<?> reconnectFuture;

    private String currentUrl;
    private final java.util.Map<String, Long> typingSnoozeMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TYPING_SNOOZE_DURATION = TimeUnit.SECONDS.toMillis(30);

    // ── Notification Manager ─────────────────────────────────────────────────
    private NotificationManager notifManager;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        MetadataManager.getInstance().init(this);
        sseExecutor = Executors.newSingleThreadScheduledExecutor();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sse_listening)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sse_listening)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sse_listening)));
        }

        if (intent != null && intent.hasExtra("url")) {
            String url = intent.getStringExtra("url");
            if (url.equals(currentUrl) && currentEventSource != null) {
                return START_STICKY;
            }
            currentUrl = url;
            connectToSse(url);
        }

        return START_STICKY;
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private Notification buildNotification(String statusText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    /** Update the persistent foreground notification text without dismissing it. */
    private void updateNotification(String statusText) {
        if (notifManager != null) {
            notifManager.notify(NOTIFICATION_ID, buildNotification(statusText));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.sse_service_name),
                    NotificationManager.IMPORTANCE_LOW);
            serviceChannel.setDescription(getString(R.string.sse_service_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    // ── SSE Connection ────────────────────────────────────────────────────────

    private void connectToSse(String url) {
        // Cancel any pending watchdog / scheduled reconnect
        cancelWatchdog();
        cancelReconnect();

        // Tear down existing connection
        EventSource old = currentEventSource;
        currentEventSource = null;
        if (old != null) {
            old.cancel();
        }

        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty() || url == null || url.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
            return;
        }

        String finalUrl = url.startsWith("http") ? url : ApiConstants.BASE_URL + url;

        Request request = new Request.Builder()
                .url(finalUrl)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Origin", ApiConstants.BASE_URL)
                .addHeader("Referer", ApiConstants.BASE_URL + "/")
                .addHeader("Accept", "text/event-stream")
                .build();

        EventSource.Factory factory = EventSources.createFactory(client);
        currentEventSource = factory.newEventSource(request, new EventSourceListener() {

            @Override
            public void onOpen(EventSource eventSource, Response response) {
                AppLogger.log(TAG, "SSE Connected to " + url);
                // Reset backoff on successful open
                reconnectDelayMs = RECONNECT_DELAY_MIN_MS;
                // Stamp first event time so the watchdog has a baseline
                lastEventTimeMs.set(System.currentTimeMillis());
                // Start inactivity watchdog now that the stream is alive
                startWatchdog(url);
                // Update persistent notification to "Connected"
                updateNotification(getString(R.string.sse_status_connected));
            }

            @Override
            public void onEvent(EventSource eventSource, @Nullable String id,
                                @Nullable String type, String data) {
                AppLogger.log(TAG, "SSE Event: " + type + " -> " + data);
                if (data == null) return;

                // Refresh watchdog heartbeat timestamp
                lastEventTimeMs.set(System.currentTimeMillis());

                String actualType = type != null ? type : "message";
                if (data.contains("heartbeat")) return;

                // Debounce: cancel pending worker trigger, wait 2 s for burst to settle
                if (debounceFuture != null && !debounceFuture.isDone()) {
                    debounceFuture.cancel(false);
                }
                debounceFuture = sseExecutor.schedule(() -> {
                    AppLogger.log(TAG, "SSE Executing JalfNotificationTask for type: " + actualType);
                    JalfNotificationTask task = new JalfNotificationTask(getApplicationContext());
                    task.execute(false);
                }, 2, TimeUnit.SECONDS);

                // Broadcast so foreground activities can refresh their UI
                Intent broadcast = new Intent("io.github.kgelinas.jalfnotifier.SSE_EVENT");
                broadcast.putExtra("type", actualType);
                broadcast.putExtra("data", data);
                sendBroadcast(broadcast);

                handleRealTimeNotification(actualType, data);
            }

            // ── Real-time notification logic ──────────────────────────────────

            private void handleRealTimeNotification(String type, String data) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(data);
                    org.json.JSONObject source = json.optJSONObject("source");
                    String userLink = source != null ? source.optString("user_link", "") : "";
                    String convLink = json.optString("conversation_link", "");

                    if (!userLink.isEmpty()) {
                        JalfNotificationTask task = new JalfNotificationTask(getApplicationContext());
                        task.checkAndSendAutoMessage(userLink);
                    }

                    AppPrefs prefs = AppPrefs.getInstance(getApplicationContext());

                    if ("convo_typing".equals(type) && !convLink.isEmpty()) {
                        long now = System.currentTimeMillis();
                        Long lastNotif = typingSnoozeMap.get(convLink);
                        if (lastNotif != null && (now - lastNotif) < TYPING_SNOOZE_DURATION) return;
                        typingSnoozeMap.put(convLink, now);

                        Set<String> filteredSexes = prefs.getStringSet(ApiConstants.KEY_FILTERED_SEXES, null);
                        if (filteredSexes != null && !filteredSexes.isEmpty() && !userLink.isEmpty()) {
                            String sexDesc = ProfileCacheManager.getInstance().getUserSexDescription(
                                    getApplicationContext(), client, userLink);
                            if (!sexDesc.isEmpty() && !filteredSexes.contains(sexDesc)) return;
                        }

                        String senderName = resolveName(json, source, userLink);
                        JalfNotificationTask.showChatNotification(getApplicationContext(), senderName,
                                getString(R.string.status_typing), convLink, "", "", "");

                    } else if ("looked".equals(type) && !userLink.isEmpty()) {
                        Set<String> salutationSexes = prefs.getStringSet(ApiConstants.KEY_SALUTATION_SEXES, null);
                        if (salutationSexes != null && !salutationSexes.isEmpty()) {
                            String sexDesc = ProfileCacheManager.getInstance().getUserSexDescription(
                                    getApplicationContext(), client, userLink);
                            if (!sexDesc.isEmpty() && !salutationSexes.contains(sexDesc)) return;
                        }

                        String lookerName = resolveName(json, source, userLink);
                        JalfNotificationTask.showNotification(getApplicationContext(), "jal_event_notifications",
                                getString(R.string.event_notifications), getString(R.string.event_visit_title),
                                lookerName + " " + getString(R.string.event_visit_message), userLink);

                    } else if (("login".equals(type) || "joined".equals(type)) && !userLink.isEmpty()) {
                        String userName = resolveName(json, source, userLink);
                        JalfNotificationTask.showNotification(getApplicationContext(), "jal_online_notifications",
                                getString(R.string.online_notifications),
                                type.equals("login") ? getString(R.string.event_login_title) : getString(R.string.event_join_title),
                                userName + " " + (type.equals("login") ? getString(R.string.event_online_message) : getString(R.string.event_join_message)),
                                userLink);

                    } else if (("message".equals(type) || "convo_new".equals(type)) && !userLink.isEmpty()) {
                        String senderName = resolveName(json, source, userLink);
                        String msgLink = json.optString("message_link", "");

                        String msgText = getString(R.string.new_message);
                        if (json.has("content")) {
                            msgText = json.optJSONObject("content").optString("text", msgText);
                        } else if (json.has("last_message")) {
                            org.json.JSONObject lastMsg = json.optJSONObject("last_message");
                            if (lastMsg != null && lastMsg.has("content")) {
                                msgText = lastMsg.optJSONObject("content").optString("text", msgText);
                            }
                        }

                        // De-duplicate against the polling task
                        Set<String> notifiedLinks = prefs.getStringSet(
                                ApiConstants.KEY_NOTIFIED_LINKS, new java.util.HashSet<>());
                        String notifKey = msgLink.isEmpty() ? (convLink + "_" + type) : msgLink;
                        boolean alreadyNotified = false;
                        for (String entry : notifiedLinks) {
                            if (entry.equals(notifKey) || entry.endsWith(":" + notifKey)) {
                                alreadyNotified = true;
                                break;
                            }
                        }
                        if (!alreadyNotified) {
                            JalfNotificationTask.showChatNotification(getApplicationContext(), senderName,
                                    msgText, convLink, "", "", "");
                            Set<String> updated = new java.util.HashSet<>();
                            for (String entry : notifiedLinks) {
                                if (!entry.equals(notifKey) && !entry.endsWith(":" + notifKey)) {
                                    updated.add(entry);
                                }
                            }
                            updated.add(System.currentTimeMillis() + ":" + notifKey);
                            if (updated.size() > 100) {
                                java.util.List<String> list = new java.util.ArrayList<>(updated);
                                java.util.Collections.sort(list);
                                while (list.size() > 100) {
                                    String oldest = list.remove(0);
                                    updated.remove(oldest);
                                }
                            }
                            prefs.putStringSet(ApiConstants.KEY_NOTIFIED_LINKS, updated);
                        }
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error handling real-time notification for type: " + type, e);
                }
            }

            private String resolveName(org.json.JSONObject eventJson,
                                       org.json.JSONObject sourceJson, String userLink) {
                if (userLink == null || userLink.isEmpty()) return getString(R.string.someone);
                String name = null;
                if (sourceJson != null) {
                    name = sourceJson.optString("user_name",
                           sourceJson.optString("pseudo",
                           sourceJson.optString("name", null)));
                }
                if (name == null || name.isEmpty() || "null".equals(name)) {
                    name = eventJson.optString("user_name",
                           eventJson.optString("pseudo",
                           eventJson.optString("name", null)));
                }
                if (name != null && !name.isEmpty() && !"null".equals(name)) return name;
                String resolved = MetadataManager.getInstance().resolve(userLink);
                if (resolved != null) return resolved;
                String pseudo = ProfileCacheManager.getInstance()
                        .resolveUsername(getApplicationContext(), client, userLink);
                return pseudo != null ? pseudo : getString(R.string.someone);
            }

            // ── EventSourceListener callbacks ─────────────────────────────────

            @Override
            public void onClosed(EventSource eventSource) {
                if (eventSource != currentEventSource) return;
                AppLogger.log(TAG, "SSE Closed — scheduling reconnect in " + reconnectDelayMs + " ms");
                cancelWatchdog();
                updateNotification(getString(R.string.sse_status_reconnecting));
                scheduleReconnect(url);
            }

            @Override
            public void onFailure(EventSource eventSource, @Nullable Throwable t,
                                  @Nullable Response response) {
                if (eventSource != currentEventSource) return;
                AppLogger.log(TAG, "SSE Failure — scheduling reconnect in " + reconnectDelayMs + " ms", t);
                cancelWatchdog();
                updateNotification(getString(R.string.sse_status_reconnecting));
                scheduleReconnect(url);
            }
        });
    }

    // ── Inactivity watchdog ───────────────────────────────────────────────────

    /**
     * Schedules a repeating check. If no event has been received within
     * {@link #INACTIVITY_TIMEOUT_MS}, the stream is assumed silently dead and
     * we cancel + reconnect with the current backoff delay.
     */
    private void startWatchdog(String url) {
        cancelWatchdog();
        inactivityCheckFuture = sseExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - lastEventTimeMs.get();
            if (elapsed >= INACTIVITY_TIMEOUT_MS) {
                AppLogger.log(TAG, "SSE Watchdog: no event for " + elapsed + " ms — reconnecting");
                EventSource dead = currentEventSource;
                currentEventSource = null;
                if (dead != null) dead.cancel();
                cancelWatchdog();
                updateNotification(getString(R.string.sse_status_reconnecting));
                scheduleReconnect(url);
            }
        }, WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelWatchdog() {
        if (inactivityCheckFuture != null && !inactivityCheckFuture.isDone()) {
            inactivityCheckFuture.cancel(false);
            inactivityCheckFuture = null;
        }
    }

    // ── Exponential backoff reconnect (on background executor) ───────────────

    /**
     * Schedules a reconnect attempt after {@link #reconnectDelayMs}, then doubles
     * the delay (capped at {@link #RECONNECT_DELAY_MAX_MS}) for the next failure.
     */
    private void scheduleReconnect(String url) {
        long delay = reconnectDelayMs;
        // Double for next failure, cap at max
        reconnectDelayMs = Math.min(reconnectDelayMs * 2, RECONNECT_DELAY_MAX_MS);

        reconnectFuture = sseExecutor.schedule(() -> {
            AppLogger.log(TAG, "SSE Reconnecting to " + url);
            connectToSse(url);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelReconnect() {
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    // ── Service lifecycle ────────────────────────────────────────────────────

    @Override
    public void onDestroy() {
        cancelWatchdog();
        cancelReconnect();
        if (debounceFuture != null) debounceFuture.cancel(false);
        if (currentEventSource != null) {
            currentEventSource.cancel();
        }
        sseExecutor.shutdownNow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
