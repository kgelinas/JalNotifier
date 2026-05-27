package io.github.kgelinas.jalfnotifier;

import java.util.Collection;
import java.util.List;

/**
 * Pure, stateless utility class for filtering lists of chat, event, and
 * favorites items.
 * All methods are free of side-effects (no UI, no Context), making them fully
 * testable
 * on the JVM without an Android emulator.
 */
public class FilterUtils {

    private FilterUtils() {
        // Utility class - do not instantiate
    }

    // ==============================================================
    // Sex Icon Matching
    // ==============================================================

    /**
     * Checks whether a given sex icon URL matches any of the provided gender label
     * filters.
     *
     * <p>
     * JALF sex icon URLs contain a numeric gender code embedded in the path, e.g.
     * {@code /images/ic_sex_1_xxx.png} for "Homme" (male). This method maps the
     * following
     * canonical labels to their corresponding codes:
     * </p>
     *
     * <ul>
     * <li>Homme → code 1</li>
     * <li>Femme → code 2</li>
     * <li>Couple → code 4</li>
     * <li>Travesti → code 8</li>
     * <li>Trans → code 16</li>
     * <li>Couple F → code 32</li>
     * <li>Couple H → code 64</li>
     * </ul>
     *
     * @param sexIconUrl   the sex icon URL from the chat/event/favorite item; may
     *                     be null or empty
     * @param activeLabels the set of currently active gender filter labels
     * @return true if the URL matches at least one label's pattern, false otherwise
     */
    public static boolean matchesAnySex(String sexIconUrl, Collection<String> activeLabels) {
        if (sexIconUrl == null || sexIconUrl.isEmpty())
            return false;
        for (String label : activeLabels) {
            switch (label) {
                case "Homme":
                    if (sexIconUrl.contains("_sex_1_") || sexIconUrl.endsWith("/1"))
                        return true;
                    break;
                case "Femme":
                    if (sexIconUrl.contains("_sex_2_") || sexIconUrl.endsWith("/2"))
                        return true;
                    break;
                case "Couple":
                    if (sexIconUrl.contains("_sex_4_") || sexIconUrl.endsWith("/4"))
                        return true;
                    break;
                case "Travesti":
                    if (sexIconUrl.contains("_sex_8_") || sexIconUrl.endsWith("/8"))
                        return true;
                    break;
                case "Trans":
                    if (sexIconUrl.contains("_sex_16_") || sexIconUrl.endsWith("/16"))
                        return true;
                    break;
                case "Couple F":
                    if (sexIconUrl.contains("_sex_32_") || sexIconUrl.endsWith("/32"))
                        return true;
                    break;
                case "Couple H":
                    if (sexIconUrl.contains("_sex_64_") || sexIconUrl.endsWith("/64"))
                        return true;
                    break;
            }
        }
        return false;
    }

    // ==============================================================
    // Chat Item Filtering
    // ==============================================================

    public static final int FILTER_STATUS_ALL = 0;
    public static final int FILTER_STATUS_READ = 1;
    public static final int FILTER_STATUS_UNREAD = 2;
    public static final int FILTER_STATUS_DELIVERED = 3;
    public static final int FILTER_STATUS_RECEIVED = 4;

    /**
     * Filters a list of ChatItems based on online, read/unread status, and sex icon
     * filters.
     * Items with type TYPE_LOAD_MORE are excluded from the input but the caller is
     * expected
     * to re-add a load-more sentinel if needed.
     *
     * @param all          the backing (unfiltered) list
     * @param onlineOnly   if true, only include online users
     * @param statusFilter one of the FILTER_STATUS_* constants
     * @param sexLabels    set of gender labels to filter by (empty = no filter)
     * @return filtered list of ChatItems (TYPE_CHAT only)
     */
    public static List<ChatAdapter.ChatItem> filterChats(
            List<ChatAdapter.ChatItem> all,
            boolean onlineOnly,
            int statusFilter,
            Collection<String> sexLabels) {

        List<ChatAdapter.ChatItem> filtered = new java.util.ArrayList<>();
        for (ChatAdapter.ChatItem it : all) {
            if (it.type != ChatAdapter.TYPE_CHAT)
                continue;
            if (onlineOnly && !it.isOnline)
                continue;
            if (statusFilter == FILTER_STATUS_READ && it.isUnread)
                continue;
            if (statusFilter == FILTER_STATUS_UNREAD && !it.isUnread)
                continue;

            boolean isChecked = it.otherReadUntil != null && !it.otherReadUntil.isEmpty()
                    && it.lastPosted != null && !it.lastPosted.isEmpty()
                    && !it.isUnread && it.otherReadUntil.compareTo(it.lastPosted) >= 0;
            if (statusFilter == FILTER_STATUS_DELIVERED && isChecked)
                continue;
            if (statusFilter == FILTER_STATUS_RECEIVED && !isChecked)
                continue;

            if (!sexLabels.isEmpty() && !matchesAnySex(it.sexIconUrl, sexLabels))
                continue;
            filtered.add(it);
        }
        return filtered;
    }
}
