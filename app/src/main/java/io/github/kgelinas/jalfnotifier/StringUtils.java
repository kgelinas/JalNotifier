package io.github.kgelinas.jalfnotifier;

import java.text.Normalizer;

public class StringUtils {
    private StringUtils() {
        // Utility class
    }

    /**
     * Normalizes a string by removing accents, converting to lowercase, and
     * trimming.
     * Useful for reliable string comparison.
     */
    public static String normalizeForMatch(String s) {
        if (s == null)
            return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").toLowerCase().trim();
    }

    /**
     * Extracts a numeric User ID from a Jalf REST link (e.g.,
     * "/rest/users/4267780").
     */
    public static String extractUserIdFromLink(String link) {
        if (link == null || link.isEmpty())
            return "";

        // 1. Handle REST pattern: /rest/users/ID/...
        int usersIndex = link.indexOf("/users/");
        if (usersIndex != -1) {
            int start = usersIndex + "/users/".length();
            int end = start;
            while (end < link.length() && Character.isDigit(link.charAt(end)))
                end++;
            String id = link.substring(start, end);
            if (!id.isEmpty())
                return id;
        }

        // 2. Handle CT pattern: /ct/memberProfile/ID/0
        int profileIndex = link.indexOf("/memberProfile/");
        if (profileIndex != -1) {
            int start = profileIndex + "/memberProfile/".length();
            int end = start;
            while (end < link.length() && Character.isDigit(link.charAt(end)))
                end++;
            String id = link.substring(start, end);
            if (!id.isEmpty())
                return id;
        }

        // 3. Handle /profile/ID pattern
        int simpleProfileIndex = link.indexOf("/profile/");
        if (simpleProfileIndex != -1) {
            int start = simpleProfileIndex + "/profile/".length();
            int end = start;
            while (end < link.length() && Character.isDigit(link.charAt(end)))
                end++;
            String id = link.substring(start, end);
            if (!id.isEmpty())
                return id;
        }

        // Fallback: any numeric segment longer than 4 digits (to avoid /0/ or /1/)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/(\\d{4,})(?:/|\\?|$)").matcher(link);
        if (m.find()) {
            return m.group(1);
        }

        return "";
    }

    /**
     * Extracts a numeric ID from a Jalf link by searching for numeric segments.
     * Specifically looks for a segment that appears to be an ID (typically
     * following /conversations/ or /users/).
     */
    public static String extractNumericId(String link) {
        if (link == null || link.isEmpty())
            return "";

        // First, check for /conversations/(\d+) which is the most specific for chat
        // refreshes
        java.util.regex.Matcher convoMatcher = java.util.regex.Pattern.compile("conversations/(\\d+)").matcher(link);
        if (convoMatcher.find()) {
            return convoMatcher.group(1);
        }

        // Fallback: any numeric segment at the end or before query params
        java.util.regex.Matcher generalMatcher = java.util.regex.Pattern.compile("/(\\d+)(?:/|\\?|$)").matcher(link);
        String lastFound = "";
        while (generalMatcher.find()) {
            lastFound = generalMatcher.group(1);
        }
        return lastFound;
    }

    /**
     * Robust comparison of two conversation links by comparing their numeric IDs
     * or normalized paths.
     */
    public static boolean isSameConversation(String link1, String link2) {
        if (link1 == null || link2 == null)
            return false;
        String id1 = extractNumericId(link1);
        String id2 = extractNumericId(link2);
        if (!id1.isEmpty() && !id2.isEmpty()) {
            return id1.equals(id2);
        }
        String p1 = link1.replaceAll("https?://[^/]+", "");
        String p2 = link2.replaceAll("https?://[^/]+", "");
        return p1.equals(p2);
    }

    /**
     * Extracts the NSFW rating rank from a JALF link (e.g., "/ct/rating/2" or
     * "/ct/sex/1/3").
     * JALF rank mapping:
     * - /1: Rank 0 (Safe)
     * - /2: Rank 1 (Soft)
     * - /3: Rank 2 (Medium)
     * - /4: Rank 3 (Strong)
     * - /5: Rank 4 (Extreme)
     * - /6: Rank 5 (Forbidden)
     */
    public static int extractRankFromLink(String link) {
        if (link == null || link.isEmpty())
            return 0;
        
        // Keyword fallbacks
        String lower = link.toLowerCase();
        if (lower.contains("forbidden") || lower.contains("interdit")) return 4;
        if (lower.contains("extreme")) return 4;
        if (lower.contains("strong") || lower.contains("fort")) return 3;
        if (lower.contains("medium") || lower.contains("moyen")) return 2;
        if (lower.contains("soft") || lower.contains("leger")) return 1;

        // Handle /rating/RANK or /sex/ID/RANK or ending in /RANK
        String clean = link.split("\\?")[0].replaceAll("/$", "");
        String[] parts = clean.split("/");
        if (parts.length > 0) {
            String last = parts[parts.length - 1];
            try {
                int rankId = Integer.parseInt(last);
                // JALF rank IDs: 1=Safe, 2=Soft, 3=Medium, 4=Strong, 5=Extreme, 6=Forbidden
                // Our ranks: 0=Safe, 1=Soft, 2=Medium, 3=Strong, 4=Extreme, 5=Forbidden
                if (rankId >= 1 && rankId <= 15) { // Some JALF ranks go higher, e.g. /15 in MainActivity
                    if (rankId == 1) return 0;
                    if (rankId == 2) return 1;
                    if (rankId == 3) return 2;
                    if (rankId == 4) return 3;
                    if (rankId == 6) return 4;
                    if (rankId == 8) return 5;
                    if (rankId == 9) return 6;
                    if (rankId == 12) return 7;
                    if (rankId == 14) return 8;
                    if (rankId == 15) return 9;
                    return rankId - 1; // Generic fallback
                }
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}
