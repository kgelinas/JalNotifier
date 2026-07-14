package io.github.kgelinas.jalfnotifier.util;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Simple in-memory log ring-buffer, capped at MAX_LOGS entries.
 *
 * Changes from original:
 *  - Uses ArrayDeque instead of ArrayList so removeFirst() is O(1) not O(n).
 *  - Uses ThreadLocal<SimpleDateFormat> so the formatter is thread-safe
 *    without needing a synchronized block just for formatting.
 */
public class AppLogger {

    private static final String TAG = "AppLogger";
    private static final int MAX_LOGS = 1000;

    // ArrayDeque: O(1) add at tail and remove from head – better than ArrayList.remove(0)
    private static final ArrayDeque<String> logs = new ArrayDeque<>(MAX_LOGS);

    // SimpleDateFormat is NOT thread-safe; ThreadLocal gives each thread its own instance.
    private static final ThreadLocal<SimpleDateFormat> SDF = ThreadLocal.withInitial(
            () -> new SimpleDateFormat("HH:mm:ss", Locale.getDefault()));

    public static synchronized void log(String message) {
        // Format timestamp outside the synchronized block would be fine with ThreadLocal,
        // but we keep the method synchronized because logs (the deque) is shared state.
        String timestamp = SDF.get().format(new Date());
        String entry = "[" + timestamp + "] " + message;
        logs.addLast(entry);
        if (logs.size() > MAX_LOGS) {
            logs.removeFirst(); // O(1) with ArrayDeque
        }
        Log.d(TAG, entry);
    }

    public static void log(String tag, String message) {
        log(tag + ": " + message);
    }

    public static void log(String tag, String message, Throwable tr) {
        log(tag + " ERROR: " + message + " -> " + tr.getMessage() + "\n" + Log.getStackTraceString(tr));
    }


    public static synchronized String getLogs() {
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append(log).append('\n');
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        logs.clear();
    }
}
