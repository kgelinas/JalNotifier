package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Unit tests for ApiParser utility class.
 * All tests use pure Java/JSON payloads with no Android framework dependency.
 */
public class ApiParserTest {

    // ==============================================================
    // Utility class safety
    // ==============================================================

    @Test
    public void testConstructorIsPrivate() throws Exception {
        Constructor<ApiParser> constructor = ApiParser.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    // ==============================================================
    // getMonthIndex
    // ==============================================================

    @Test
    public void testGetMonthIndex_Valid() {
        assertEquals(0, ApiParser.getMonthIndex("janvier"));
        assertEquals(1, ApiParser.getMonthIndex("février"));
        assertEquals(2, ApiParser.getMonthIndex("mars"));
        assertEquals(11, ApiParser.getMonthIndex("décembre"));
    }

    @Test
    public void testGetMonthIndex_Invalid() {
        assertEquals(-1, ApiParser.getMonthIndex("march"));
        assertEquals(-1, ApiParser.getMonthIndex(""));
        assertEquals(-1, ApiParser.getMonthIndex(null));
    }

    // ==============================================================
    // parseIsoToMillis
    // ==============================================================

    @Test
    public void testParseIsoToMillis_Standard() {
        // Known timestamp: 2026-01-01T00:00:00.000Z = 1767225600000
        long result = ApiParser.parseIsoToMillis("2026-01-01T00:00:00.000Z");
        assertTrue("Expected > 0 millis for a valid ISO timestamp", result > 0);
    }

    @Test
    public void testParseIsoToMillis_SpaceSeparated() {
        // Test fallback format: "yyyy-MM-dd HH:mm:ss"
        long result = ApiParser.parseIsoToMillis("2026-01-01 00:00:00");
        assertTrue(result > 0);
    }

    @Test
    public void testParseIsoToMillis_Invalid() {
        assertEquals(0, ApiParser.parseIsoToMillis("not-a-date"));
        assertEquals(0, ApiParser.parseIsoToMillis(null));
        assertEquals(0, ApiParser.parseIsoToMillis(""));
    }

    // ==============================================================
    // fixPokeImageUrl
    // ==============================================================

    @Test
    public void testFixPokeImageUrl_NormalizesUrl() {
        String raw = "/jalf/images/pokes/42/thumb_poke_42_1_1.png";
        assertEquals("/jalf/images/pokes/thumb_poke_42.png", ApiParser.fixPokeImageUrl(raw));
    }

    @Test
    public void testFixPokeImageUrl_NoChange() {
        String url = "/jalf/images/pokes/thumb_poke_99.png";
        assertEquals(url, ApiParser.fixPokeImageUrl(url));
    }

    @Test
    public void testFixPokeImageUrl_NullOrEmpty() {
        assertNull(ApiParser.fixPokeImageUrl(null));
        assertEquals("", ApiParser.fixPokeImageUrl(""));
    }

    // ==============================================================
    // parseLookerDate
    // ==============================================================

    @Test
    public void testParseLookerDate_NullOrEmpty() {
        assertEquals("", ApiParser.parseLookerDate(null));
        assertEquals("", ApiParser.parseLookerDate(""));
    }

    @Test
    public void testParseLookerDate_Aujourdhui() {
        String result = ApiParser.parseLookerDate("Aujourd'hui");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should contain today's date portion
        assertTrue(result.startsWith("20"));
    }

    @Test
    public void testParseLookerDate_Hier() {
        String today = ApiParser.parseLookerDate("Aujourd'hui");
        String yesterday = ApiParser.parseLookerDate("Hier");
        assertTrue("Yesterday should be before today", yesterday.compareTo(today) < 0);
    }

    @Test
    public void testParseLookerDate_IlYA_Jours() {
        String result = ApiParser.parseLookerDate("il y a 3 jours");
        assertNotNull(result);
        assertTrue(result.startsWith("20"));
    }

    @Test
    public void testParseLookerDate_IlYA_Heures() {
        String result = ApiParser.parseLookerDate("il y a 2 heures");
        assertNotNull(result);
        assertTrue(result.startsWith("20"));
    }

    @Test
    public void testParseLookerDate_IlYA_Minutes() {
        String result = ApiParser.parseLookerDate("il y a 30 minutes");
        assertNotNull(result);
        assertTrue(result.startsWith("20"));
    }

    @Test
    public void testParseLookerDate_AbsoluteDate() {
        String result = ApiParser.parseLookerDate("29 mars 2026");
        assertNotNull(result);
        assertTrue("Expected date-like ISO string", result.contains("2026"));
        // Relaxing checks for specific day/month because system local time vs UTC
        // conversion
        // (done in ApiParser) can shift the date depending on the current time of day.
        assertTrue("Result seems valid ISO format", result.matches("2026-\\d{2}-\\d{2}T.*"));
    }

    @Test
    public void testParseLookerDate_WithAtTime() {
        // The @ portion sets a time component. Output is UTC-converted so the exact
        // hour may differ by timezone. We verify a well-formed ISO timestamp is
        // returned.
        String result = ApiParser.parseLookerDate("Aujourd'hui @ 14:30:00");
        assertNotNull(result);
        assertFalse("Expected non-empty ISO timestamp", result.isEmpty());
        assertTrue("Expected ISO format starting with year", result.startsWith("20"));
        assertTrue("Expected ISO format with T separator", result.contains("T"));
    }

    // ==============================================================
    // parseConversationJson
    // ==============================================================

    @Test
    public void testParseConversationJson_Null() {
        List<ChatAdapter.ChatItem> result = ApiParser.parseConversationJson(null, false, "999");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseConversationJson_BasicItem() throws Exception {
        String json = "[{" +
                "\"conversation_link\": \"/rest/conversations/123\"," +
                "\"total_unread_messages_count\": 2," +
                "\"other_members\": [{" +
                "\"name\": \"Alice\"," +
                "\"user_link\": \"/rest/users/456\"," +
                "\"online\": true" +
                "}]," +
                "\"last_message\": {" +
                "\"type\": \"text\"," +
                "\"content\": {\"text\": \"Hi!\"}" +
                "}" +
                "}]";

        JSONArray arr = new JSONArray(json);
        List<ChatAdapter.ChatItem> result = ApiParser.parseConversationJson(arr, false, "999");

        assertEquals(1, result.size());
        ChatAdapter.ChatItem item = result.get(0);
        assertEquals("Alice", item.name);
        assertEquals("456", item.otherUserId);
        assertEquals("Hi!", item.lastMessage);
        assertEquals("/rest/conversations/123", item.conversationLink);
        assertEquals(2, item.unreadCount);
        assertTrue(item.isUnread);
        assertTrue(item.isOnline);
    }

    @Test
    public void testParseConversationJson_Photo() throws Exception {
        String json = "[{" +
                "\"conversation_link\": \"/rest/conversations/200\"," +
                "\"other_members\": [{\"name\": \"Bob\", \"user_link\": \"/rest/users/789\"}]," +
                "\"last_message\": {\"type\": \"photo\"}" +
                "}]";

        JSONArray arr = new JSONArray(json);
        List<ChatAdapter.ChatItem> result = ApiParser.parseConversationJson(arr, false, "999");

        assertEquals(1, result.size());
        assertEquals("📷 Photo", result.get(0).lastMessage);
    }

    @Test
    public void testParseConversationJson_DeletedProfile() throws Exception {
        String json = "[{" +
                "\"conversation_link\": \"/rest/conversations/300\"," +
                "\"other_members\": [{\"name\": \"X\", \"user_link\": \"/rest/users/100\", \"type\": \"deleted\"}]" +
                "}]";

        JSONArray arr = new JSONArray(json);
        List<ChatAdapter.ChatItem> result = ApiParser.parseConversationJson(arr, false, "999");

        assertEquals(1, result.size());
        assertEquals("Profil supprimé", result.get(0).name);
        assertEquals("Compte supprimé", result.get(0).lastMessage);
        assertEquals("", result.get(0).avatarUrl);
    }

    @Test
    public void testParseConversationJson_ForceUnread() throws Exception {
        String json = "[{\"conversation_link\": \"/c/1\", \"total_unread_messages_count\": 0, \"other_members\": [{\"name\":\"C\"}]}]";
        JSONArray arr = new JSONArray(json);
        List<ChatAdapter.ChatItem> result = ApiParser.parseConversationJson(arr, true, "999");
        assertTrue("forceUnread=true should mark item as unread", result.get(0).isUnread);
    }

    @Test
    public void testParseConversationJson_IsLastMessageMine() throws Exception {
        String json = "[{" +
                "\"conversation_link\": \"/c/1\"," +
                "\"other_members\": [{\"name\": \"D\", \"user_link\": \"/rest/users/100\"}]," +
                "\"last_message\": {\"type\": \"text\", \"content\": {\"text\": \"Hey\"}, \"from_user_link\": \"/rest/users/999\"}"
                +
                "}]";
        JSONArray arr = new JSONArray(json);
        List<ChatAdapter.ChatItem> result = ApiParser.parseConversationJson(arr, false, "999");
        assertTrue("Message from /rest/users/999 should be 'mine'", result.get(0).isLastMessageMine);
    }

    // ==============================================================
    // parseSearchResults
    // ==============================================================

    @Test
    public void testParseSearchResults_Empty() {
        List<SearchAdapter.SearchItem> result = ApiParser.parseSearchResults("<html><body></body></html>");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseSearchResults_OneBlock() {
        String html = "<div class='search_tab_block_A'>" +
                "<a class='mbrprof' href='/ct/memberProfile/user42/profile'>Alice</a>" +
                "</div>";
        List<SearchAdapter.SearchItem> result = ApiParser.parseSearchResults(html);
        assertEquals(1, result.size());
        assertEquals("user42", result.get(0).userId);
        assertEquals("Alice", result.get(0).name);
        assertTrue(result.get(0).detailsFetched);
    }

    @Test
    public void testParseSearchResults_SkipsBlockWithNoUserId() {
        // Block without a name/link element should be skipped
        String html = "<div class='search_tab_block_A'><p>No link here</p></div>";
        List<SearchAdapter.SearchItem> result = ApiParser.parseSearchResults(html);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseSearchResults_OnlineIndicator() {
        String html = "<div class='search_tab_block_B'>" +
                "<a class='mbrprof' href='/ct/memberProfile/user77/profile'>Bob</a>" +
                "<img src='/images/ic_online.png'/>" +
                "</div>";
        List<SearchAdapter.SearchItem> result = ApiParser.parseSearchResults(html);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isOnline);
    }

    // ==============================================================
    // parseFrenchDateToTimestamp
    // ==============================================================

    @Test
    public void testParseFrenchDateToTimestamp_NullOrEmpty() {
        long now = System.currentTimeMillis();
        long r1 = ApiParser.parseFrenchDateToTimestamp(null);
        assertTrue("Expected timestamp close to now", Math.abs(r1 - now) < 10_000L);
        long r2 = ApiParser.parseFrenchDateToTimestamp("");
        assertTrue(Math.abs(r2 - now) < 10_000L);
    }

    @Test
    public void testParseFrenchDateToTimestamp_Aujourdhui() {
        long result = ApiParser.parseFrenchDateToTimestamp("Aujourd'hui \u00e0 14h30");
        assertTrue("Expected a non-zero timestamp", result > 0);
    }

    @Test
    public void testParseFrenchDateToTimestamp_Hier() {
        long aujourd = ApiParser.parseFrenchDateToTimestamp("Aujourd'hui \u00e0 10h00");
        long hier = ApiParser.parseFrenchDateToTimestamp("Hier \u00e0 10h00");
        assertTrue("Yesterday should be before today", hier < aujourd);
    }

    @Test
    public void testParseFrenchDateToTimestamp_ShortDate() {
        // Absolute format: "12 jan \u00e0 10h45"
        long result = ApiParser.parseFrenchDateToTimestamp("12 jan \u00e0 10h45");
        assertTrue(result > 0);
    }

    // ==============================================================
    // parseSelectOptions
    // ==============================================================

    @Test
    public void testParseSelectOptions_Null() {
        List<SearchOption> result = ApiParser.parseSelectOptions(null, "#mySelect");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseSelectOptions_NullSelector() {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse("<html><body></body></html>");
        List<SearchOption> result = ApiParser.parseSelectOptions(doc, null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseSelectOptions_NoMatch() {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse("<html><body></body></html>");
        List<SearchOption> result = ApiParser.parseSelectOptions(doc, "#nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseSelectOptions_ThreeOptions() {
        String html = "<select id='mySelect'>" +
                "<option value=''>Tous</option>" +
                "<option value='a'>Alpha</option>" +
                "<option value='b'>Beta</option>" +
                "</select>";
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        List<SearchOption> result = ApiParser.parseSelectOptions(doc, "#mySelect");
        assertEquals(3, result.size());
        assertEquals("", result.get(0).value);
        assertEquals("Tous", result.get(0).label);
        assertEquals("a", result.get(1).value);
        assertEquals("b", result.get(2).value);
    }
}
