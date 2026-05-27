package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for FilterUtils utility class.
 * Tests the matchesAnySex URL pattern matching and the filterChats logic.
 * All tests run purely on JVM (no Android emulator required).
 */
public class FilterUtilsTest {

    // ==============================================================
    // Utility class safety
    // ==============================================================

    @Test
    public void testConstructorIsPrivate() throws Exception {
        Constructor<FilterUtils> constructor = FilterUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    // ==============================================================
    // matchesAnySex - null / empty guards
    // ==============================================================

    @Test
    public void testMatchesAnySex_NullUrl() {
        assertFalse(FilterUtils.matchesAnySex(null, Collections.singleton("Homme")));
    }

    @Test
    public void testMatchesAnySex_EmptyUrl() {
        assertFalse(FilterUtils.matchesAnySex("", Collections.singleton("Femme")));
    }

    @Test
    public void testMatchesAnySex_EmptyLabels() {
        // No active filter — should not match anything
        assertFalse(FilterUtils.matchesAnySex("http://m-app.jalf.com/images/ic_sex_1_m.png",
                Collections.emptySet()));
    }

    // ==============================================================
    // matchesAnySex - embedded code matching (_sex_N_)
    // ==============================================================

    @Test
    public void testMatchesAnySex_Homme_Embedded() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_1_icon.png",
                Collections.singleton("Homme")));
    }

    @Test
    public void testMatchesAnySex_Femme_Embedded() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_2_icon.png",
                Collections.singleton("Femme")));
    }

    @Test
    public void testMatchesAnySex_Couple_Embedded() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_4_icon.png",
                Collections.singleton("Couple")));
    }

    @Test
    public void testMatchesAnySex_Travesti_Embedded() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_8_icon.png",
                Collections.singleton("Travesti")));
    }

    @Test
    public void testMatchesAnySex_Trans_Embedded() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_16_icon.png",
                Collections.singleton("Trans")));
    }

    @Test
    public void testMatchesAnySex_CoupleF_Embedded() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_32_icon.png",
                Collections.singleton("Couple F")));
    }

    @Test
    public void testMatchesAnySex_CoupleH_Embedded() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_64_icon.png",
                Collections.singleton("Couple H")));
    }

    // ==============================================================
    // matchesAnySex - suffix matching (/N at end)
    // ==============================================================

    @Test
    public void testMatchesAnySex_Homme_Suffix() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/sex/1",
                Collections.singleton("Homme")));
    }

    @Test
    public void testMatchesAnySex_Trans_Suffix() {
        assertTrue(FilterUtils.matchesAnySex("http://example.com/sex/16",
                Collections.singleton("Trans")));
    }

    // ==============================================================
    // matchesAnySex - wrong label should NOT match
    // ==============================================================

    @Test
    public void testMatchesAnySex_WrongLabel() {
        // URL is Homme (sex_1), filter is Femme – should not match
        assertFalse(FilterUtils.matchesAnySex("http://example.com/ic_sex_1_icon.png",
                Collections.singleton("Femme")));
    }

    @Test
    public void testMatchesAnySex_UnknownLabel() {
        // Completely unknown label – falls through the switch with no match
        assertFalse(FilterUtils.matchesAnySex("http://example.com/ic_sex_1_icon.png",
                Collections.singleton("Alien")));
    }

    // ==============================================================
    // matchesAnySex - multiple labels (OR semantics)
    // ==============================================================

    @Test
    public void testMatchesAnySex_MultipleLabels_OneMatches() {
        Set<String> labels = new HashSet<>(Arrays.asList("Couple", "Femme"));
        // URL is Femme – matches via "Femme"
        assertTrue(FilterUtils.matchesAnySex("http://example.com/ic_sex_2_icon.png", labels));
    }

    @Test
    public void testMatchesAnySex_MultipleLabels_NoneMatch() {
        Set<String> labels = new HashSet<>(Arrays.asList("Trans", "Travesti"));
        // URL is Homme – does not match either
        assertFalse(FilterUtils.matchesAnySex("http://example.com/ic_sex_1_icon.png", labels));
    }

    // ==============================================================
    // filterChats
    // ==============================================================

    private ChatAdapter.ChatItem makeChat(boolean online, boolean unread, String otherReadUntil,
            String lastPosted, String sexIconUrl) {
        ChatAdapter.ChatItem item = new ChatAdapter.ChatItem(ChatAdapter.TYPE_CHAT);
        item.isOnline = online;
        item.isUnread = unread;
        item.otherReadUntil = otherReadUntil != null ? otherReadUntil : "";
        item.lastPosted = lastPosted != null ? lastPosted : "";
        item.sexIconUrl = sexIconUrl != null ? sexIconUrl : "";
        return item;
    }

    @Test
    public void testFilterChats_NoFilters() {
        List<ChatAdapter.ChatItem> all = Arrays.asList(
                makeChat(true, false, "", "", ""),
                makeChat(false, true, "", "", ""));
        List<ChatAdapter.ChatItem> result = FilterUtils.filterChats(all, false,
                FilterUtils.FILTER_STATUS_ALL, Collections.emptySet());
        assertEquals(2, result.size());
    }

    @Test
    public void testFilterChats_OnlineOnly() {
        List<ChatAdapter.ChatItem> all = Arrays.asList(
                makeChat(true, false, "", "", ""),
                makeChat(false, false, "", "", ""));
        List<ChatAdapter.ChatItem> result = FilterUtils.filterChats(all, true,
                FilterUtils.FILTER_STATUS_ALL, Collections.emptySet());
        assertEquals(1, result.size());
        assertTrue(result.get(0).isOnline);
    }

    @Test
    public void testFilterChats_UnreadOnly() {
        List<ChatAdapter.ChatItem> all = Arrays.asList(
                makeChat(true, true, "", "", ""),
                makeChat(false, false, "", "", ""));
        List<ChatAdapter.ChatItem> result = FilterUtils.filterChats(all, false,
                FilterUtils.FILTER_STATUS_UNREAD, Collections.emptySet());
        assertEquals(1, result.size());
        assertTrue(result.get(0).isUnread);
    }

    @Test
    public void testFilterChats_ReadOnly() {
        List<ChatAdapter.ChatItem> all = Arrays.asList(
                makeChat(true, true, "", "", ""),
                makeChat(false, false, "", "", ""));
        List<ChatAdapter.ChatItem> result = FilterUtils.filterChats(all, false,
                FilterUtils.FILTER_STATUS_READ, Collections.emptySet());
        assertEquals(1, result.size());
        assertFalse(result.get(0).isUnread);
    }

    @Test
    public void testFilterChats_SexFilter() {
        // Only include Homme (sex_1) items
        List<ChatAdapter.ChatItem> all = Arrays.asList(
                makeChat(true, false, "", "", "http://ex.com/ic_sex_1_m.png"),
                makeChat(true, false, "", "", "http://ex.com/ic_sex_2_f.png"));
        List<ChatAdapter.ChatItem> result = FilterUtils.filterChats(all, false,
                FilterUtils.FILTER_STATUS_ALL, Collections.singleton("Homme"));
        assertEquals(1, result.size());
        assertTrue(result.get(0).sexIconUrl.contains("_sex_1_"));
    }

    @Test
    public void testFilterChats_SkipsLoadMoreType() {
        ChatAdapter.ChatItem loadMore = new ChatAdapter.ChatItem(ChatAdapter.TYPE_LOAD_MORE);
        List<ChatAdapter.ChatItem> all = Arrays.asList(
                makeChat(true, false, "", "", ""),
                loadMore);
        // TYPE_LOAD_MORE items should be filtered out
        List<ChatAdapter.ChatItem> result = FilterUtils.filterChats(all, false,
                FilterUtils.FILTER_STATUS_ALL, Collections.emptySet());
        assertEquals(1, result.size());
        assertEquals(ChatAdapter.TYPE_CHAT, result.get(0).type);
    }

    @Test
    public void testFilterChats_DeliveredFilter() {
        // DELIVERED filter excludes items where the other user has read past the last
        // message
        String readUntil = "2026-01-02T10:00:00.000Z";
        String lastPosted = "2026-01-01T09:00:00.000Z";
        // otherReadUntil >= lastPosted → isChecked = true → skip in DELIVERED filter
        ChatAdapter.ChatItem checked = makeChat(true, false, readUntil, lastPosted, "");
        ChatAdapter.ChatItem unchecked = makeChat(true, false, "", "", "");
        List<ChatAdapter.ChatItem> result = FilterUtils.filterChats(
                Arrays.asList(checked, unchecked), false,
                FilterUtils.FILTER_STATUS_DELIVERED, Collections.emptySet());
        assertEquals(1, result.size());
        assertSame(unchecked, result.get(0));
    }
}
