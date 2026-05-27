package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class TimeUtilsTest {

    private String formatIso(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date(millis));
    }

    @Test
    public void testGetRelativeTime_Now() {
        long now = System.currentTimeMillis();
        assertEquals("now", TimeUtils.getRelativeTime(formatIso(now)));
    }

    @Test
    public void testGetRelativeTime_MinutesAgo() {
        long fiveMinsAgo = System.currentTimeMillis() - (5 * 60_000 + 10_000);
        assertEquals("5m ago", TimeUtils.getRelativeTime(formatIso(fiveMinsAgo)));
    }

    @Test
    public void testGetRelativeTime_FutureDate() {
        long future = System.currentTimeMillis() + 100_000;
        assertEquals("now", TimeUtils.getRelativeTime(formatIso(future)));
    }

    @Test
    public void testGetRelativeTime_HoursAgo() {
        long twoHoursAgo = System.currentTimeMillis() - (2 * 3600_000 + 10_000);
        assertEquals("2h ago", TimeUtils.getRelativeTime(formatIso(twoHoursAgo)));
    }

    @Test
    public void testGetRelativeTime_DaysAgo() {
        long threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 3600_000 + 10_000);
        assertEquals("3d ago", TimeUtils.getRelativeTime(formatIso(threeDaysAgo)));
    }

    @Test
    public void testGetRelativeTime_ISOWithSubseconds() {
        // Test normalization of sub-seconds
        String iso = "2026-03-21T12:00:00.123456Z";
        // This is a fixed date in the past, so it should return X days ago
        String result = TimeUtils.getRelativeTime(iso);
        assertTrue(result.contains("d ago"));
    }

    @Test
    public void testGetRelativeTime_EmptyInput() {
        assertEquals("", TimeUtils.getRelativeTime(""));
        assertEquals("", TimeUtils.getRelativeTime(null));
    }

    @Test
    public void testGetRelativeTime_InvalidFormat() {
        // Covers catch block
        assertEquals("2024-invalid", TimeUtils.getRelativeTime("2024-invalid"));
    }

    @Test
    public void testConstructorIsPrivate() throws Exception {
        // Covers private constructor line
        Constructor<TimeUtils> constructor = TimeUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    // ==============================================================
    // timestampToIso
    // ==============================================================

    @Test
    public void testTimestampToIso_Epoch() {
        // Epoch zero = 1970-01-01T00:00:00
        String result = TimeUtils.timestampToIso(0L);
        assertEquals("1970-01-01T00:00:00", result);
    }

    @Test
    public void testTimestampToIso_KnownDate() {
        // 2026-04-12T12:00:00 UTC = 1775995200000L
        long millis = 1775995200000L;
        String result = TimeUtils.timestampToIso(millis);
        assertEquals("2026-04-12T12:00:00", result);
    }

    @Test
    public void testTimestampToIso_RoundTrip() {
        // Parse an ISO string, convert to millis, then back to ISO
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        try {
            long millis = sdf.parse("2025-06-15T08:30:45").getTime();
            assertEquals("2025-06-15T08:30:45", TimeUtils.timestampToIso(millis));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
