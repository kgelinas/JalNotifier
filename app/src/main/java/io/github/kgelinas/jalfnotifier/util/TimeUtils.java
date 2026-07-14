package io.github.kgelinas.jalfnotifier.util;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {
    private TimeUtils() {
        // Utility class
    }

    /**
     * Converts an ISO timestamp into a relative human-readable string (e.g., "5m
     * ago").
     */
    public static String getRelativeTime(android.content.Context context, String isoTime) {
        if (isoTime == null || isoTime.isEmpty())
            return "";
        try {
            // Robust parsing: normalize by removing sub-seconds if present
            String normalized = isoTime.replaceAll("(\\.\\d+)?Z$", "Z");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date date = sdf.parse(normalized);
            long diff = System.currentTimeMillis() - date.getTime();
            if (diff < 0)
                return context.getString(R.string.time_now); // Future dates (skew) handled as now

            if (diff < 60_000)
                return context.getString(R.string.time_now);

            long mins = diff / 60_000;
            if (mins < 60)
                return context.getString(R.string.time_ago_m, mins);

            long hours = mins / 60;
            if (hours < 24)
                return context.getString(R.string.time_ago_h, hours);

            long days = hours / 24;
            return context.getString(R.string.time_ago_d, days);
        } catch (Exception e) {
            return isoTime;
        }
    }

    /**
     * Formats an ISO-8601 timestamp for display in a chat.
     * Shows "HH:mm" for today, "Hier HH:mm" for yesterday, and "d MMM HH:mm" for
     * older messages.
     *
     * @param isoTime the ISO-8601 timestamp string
     * @return a formatted timestamp string
     */
    public static String formatChatTimestamp(android.content.Context context, String isoTime) {
        if (isoTime == null || isoTime.isEmpty())
            return "";
        try {
            // Robust parsing: normalize by removing sub-seconds if present
            String normalized = isoTime.replaceAll("(\\.\\d+)?Z$", "Z");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date date = sdf.parse(normalized);
            if (date == null)
                return isoTime;

            long now = System.currentTimeMillis();
            long msgTime = date.getTime();

            // Calculate day difference using local time
            java.util.Calendar calNow = java.util.Calendar.getInstance();
            calNow.setTimeInMillis(now);
            java.util.Calendar calMsg = java.util.Calendar.getInstance();
            calMsg.setTimeInMillis(msgTime);

            boolean isSameDay = calNow.get(java.util.Calendar.YEAR) == calMsg.get(java.util.Calendar.YEAR) &&
                    calNow.get(java.util.Calendar.DAY_OF_YEAR) == calMsg.get(java.util.Calendar.DAY_OF_YEAR);

            if (isSameDay) {
                SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return timeFmt.format(date);
            }

            // Check if yesterday
            calNow.add(java.util.Calendar.DAY_OF_YEAR, -1);
            boolean isYesterday = calNow.get(java.util.Calendar.YEAR) == calMsg.get(java.util.Calendar.YEAR) &&
                    calNow.get(java.util.Calendar.DAY_OF_YEAR) == calMsg.get(java.util.Calendar.DAY_OF_YEAR);

            if (isYesterday) {
                SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return context.getString(R.string.time_yesterday, timeFmt.format(date));
            }

            // Older than yesterday
            SimpleDateFormat dateFmt = new SimpleDateFormat("d MMM HH:mm", Locale.getDefault());
            return dateFmt.format(date);

        } catch (Exception e) {
            return isoTime;
        }
    }

    /**
     * Converts epoch milliseconds to an ISO-8601 UTC string.
     * Format: "yyyy-MM-dd'T'HH:mm:ss" (no milliseconds, no Z suffix).
     *
     * @param epochMillis epoch milliseconds
     * @return ISO-8601 formatted date-time string
     */
    public static String timestampToIso(long epochMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(epochMillis));
    }
}
