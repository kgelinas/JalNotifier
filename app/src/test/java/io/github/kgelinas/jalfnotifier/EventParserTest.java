package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for the event/notification parsing methods added to ApiParser.
 * Tests parseNotificationJson, parsePokeHtml, and deduplicateEvents.
 * All tests run purely on the JVM (no Android emulator required).
 */
public class EventParserTest {

    // ==============================================================
    // parseNotificationJson — null / empty guards
    // ==============================================================

    @Test
    public void testParseNotificationJson_Null() {
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseNotificationJson_EmptyArray() throws Exception {
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(new JSONArray("[]"));
        assertTrue(result.isEmpty());
    }

    // ==============================================================
    // parseNotificationJson — why = favorite_of (type 22)
    // ==============================================================

    @Test
    public void testParseNotificationJson_FavoriteOf_Why() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject()
                .put("user_id", "123")
                .put("title", "Alice")
                .put("why", "favorite_of")
                .put("when_added", "2026-04-12T10:00:00.000Z")
                .put("is_unread", true));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        EventAdapter.EventItem item = result.get(0);
        assertEquals("123", item.otherUserId);
        assertEquals("Alice", item.title);
        assertTrue(item.body.contains("favori"));
        assertTrue(item.isUnread);
    }

    @Test
    public void testParseNotificationJson_FavoriteOf_Type22() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject()
                .put("user_id", "456")
                .put("pseudo", "Bob")
                .put("type", 22)
                .put("when_added", "2026-04-12T09:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertTrue(result.get(0).body.contains("favori"));
    }

    // ==============================================================
    // parseNotificationJson — why = your_favorite (type 6)
    // ==============================================================

    @Test
    public void testParseNotificationJson_YourFavorite_WithThumbnail() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject refData = new JSONObject().put("thumbnail_uri", "/photos/thumb.jpg");
        arr.put(new JSONObject()
                .put("user_id", "789")
                .put("user_name", "Carl")
                .put("why", "your_favorite")
                .put("reference_data", refData)
                .put("when_added", "2026-04-12T08:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertTrue(result.get(0).body.contains("photo"));
        assertEquals("/photos/thumb.jpg", result.get(0).secondaryImageUrl);
    }

    // ==============================================================
    // parseNotificationJson — type 20 poke
    // ==============================================================

    @Test
    public void testParseNotificationJson_Poke_Type20_WithSuffix() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject refData = new JSONObject()
                .put("suffix_text", "vous a envoyé un clin d'oeil")
                .put("graphic_uriref", "/pokes/thumb_poke_3.png");
        arr.put(new JSONObject()
                .put("user_id", "321")
                .put("title", "Dave")
                .put("type", 20)
                .put("reference_data", refData)
                .put("when_added", "2026-04-11T15:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertEquals("vous a envoyé un clin d'oeil", result.get(0).body);
        assertEquals("/pokes/thumb_poke_3.png", result.get(0).secondaryImageUrl);
    }

    @Test
    public void testParseNotificationJson_Poke_Type20_NoRefData() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject()
                .put("user_id", "111")
                .put("title", "Eve")
                .put("type", 20)
                .put("when_added", "2026-04-10T12:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertTrue(result.get(0).body.contains("poke"));
    }

    // ==============================================================
    // parseNotificationJson — why = author
    // ==============================================================

    @Test
    public void testParseNotificationJson_Author_Photo() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject refData = new JSONObject()
                .put("type", "photo")
                .put("thumbnail_uri", "/photos/t2.jpg");
        arr.put(new JSONObject()
                .put("user_id", "222")
                .put("title", "Frank")
                .put("why", "author")
                .put("type", 1)
                .put("reference_data", refData)
                .put("when_added", "2026-04-09T10:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertTrue(result.get(0).body.contains("aimé"));
        assertEquals("/photos/t2.jpg", result.get(0).secondaryImageUrl);
    }

    @Test
    public void testParseNotificationJson_Author_Type3() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject()
                .put("user_id", "333")
                .put("title", "Grace")
                .put("why", "author")
                .put("type", 3)
                .put("when_added", "2026-04-08T10:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertTrue(result.get(0).body.contains("photo"));
    }

    // ==============================================================
    // parseNotificationJson — why = visit
    // ==============================================================

    @Test
    public void testParseNotificationJson_Visit() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject()
                .put("user_id", "444")
                .put("title", "Hector")
                .put("why", "visit")
                .put("when_added", "2026-04-07T10:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertTrue(result.get(0).body.contains("visité"));
    }

    // ==============================================================
    // parseNotificationJson — fallback user_id from user_link
    // ==============================================================

    @Test
    public void testParseNotificationJson_FallbackUserIdFromLink() throws Exception {
        JSONArray arr = new JSONArray();
        // /rest/users/99 format — matches StringUtils.extractUserIdFromLink
        arr.put(new JSONObject()
                .put("user_link", "/rest/users/99")
                .put("pseudo", "Ida")
                .put("why", "visit")
                .put("when_added", "2026-04-06T10:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertEquals("99", result.get(0).otherUserId);
    }

    // ==============================================================
    // parseNotificationJson — avatar URL normalization
    // ==============================================================

    @Test
    public void testParseNotificationJson_AvatarUrlPrefixed() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject()
                .put("user_id", "10")
                .put("title", "Jack")
                .put("avatar_url", "/uploads/photo.jpg")
                .put("why", "visit")
                .put("when_added", "2026-04-05T10:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.parseNotificationJson(arr);
        assertEquals(1, result.size());
        assertTrue(result.get(0).avatarUrl.startsWith("http"));
    }

    // ==============================================================
    // parsePokeHtml — null / empty guards
    // ==============================================================

    @Test
    public void testParsePokeHtml_Null() {
        List<EventAdapter.EventItem> result = ApiParser.parsePokeHtml(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParsePokeHtml_Empty() {
        List<EventAdapter.EventItem> result = ApiParser.parsePokeHtml("");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParsePokeHtml_NoUserElements() {
        List<EventAdapter.EventItem> result = ApiParser
                .parsePokeHtml("<html><body><div>no users here</div></body></html>");
        assertTrue(result.isEmpty());
    }

    // ==============================================================
    // parsePokeHtml — well-formed poke block
    // ==============================================================

    @Test
    public void testParsePokeHtml_WellFormedBlock() {
        String html = "<div class='one_user'>"
                + "<a onclick='gotoprofile(55, 0)' href='#'>Karen</a>"
                + "<a class='roll'><img class='picture_border' src='/photos/karen.jpg'/></a>"
                + "<div class='cadrepoke'>"
                + "  <p class='txt_pokes'>Karen vous a envoyé un bisou</p>"
                + "  <img src='/jalf/images/pokes/3/thumb_poke_3_1_1.png'/>"
                + "  <p class='date_show'>Aujourd'hui @ 14:30:00</p>"
                + "</div>"
                + "</div>";
        List<EventAdapter.EventItem> result = ApiParser.parsePokeHtml(html);
        assertEquals(1, result.size());
        EventAdapter.EventItem item = result.get(0);
        assertEquals("55", item.otherUserId);
        assertEquals("Karen", item.title);
        // body should strip the name prefix
        assertTrue("Expected body without name prefix, got: " + item.body,
                !item.body.startsWith("Karen"));
        // secondary image URL should be normalized
        assertNotNull(item.secondaryImageUrl);
        assertTrue(item.secondaryImageUrl.contains("thumb_poke_3"));
        // event type for pokes
        assertEquals(-3, item.eventType);
    }

    @Test
    public void testParsePokeHtml_DefaultBodyIfMissing() {
        // Poke block with no p.txt_pokes element — body should default to greeting
        String html = "<div class='one_user'>"
                + "<a onclick='gotoprofile(66, 0)'>Leo</a>"
                + "<div class='cadrepoke'></div>"
                + "</div>";
        List<EventAdapter.EventItem> result = ApiParser.parsePokeHtml(html);
        assertEquals(1, result.size());
        assertTrue(result.get(0).body.contains("salutation"));
    }

    @Test
    public void testParsePokeHtml_SkipsBlockWithoutUserId() {
        // Block without the onclick attribute — no otherUserId can be extracted
        String html = "<div class='one_user'>"
                + "<a href='#'>Mystery</a>" // no onclick
                + "</div>";
        List<EventAdapter.EventItem> result = ApiParser.parsePokeHtml(html);
        assertTrue(result.isEmpty());
    }

    // ==============================================================
    // deduplicateEvents — basic dedup
    // ==============================================================

    private EventAdapter.EventItem makeEvent(String userId, String timeIso) {
        EventAdapter.EventItem item = new EventAdapter.EventItem();
        item.otherUserId = userId;
        item.timeIso = timeIso;
        return item;
    }

    @Test
    public void testDeduplicateEvents_EmptyList() {
        List<EventAdapter.EventItem> result = ApiParser.deduplicateEvents(java.util.Collections.emptyList());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testDeduplicateEvents_NoDuplicates() {
        List<EventAdapter.EventItem> events = java.util.Arrays.asList(
                makeEvent("1", "2026-04-12T10:00:00.000Z"),
                makeEvent("2", "2026-04-11T09:00:00.000Z"));
        List<EventAdapter.EventItem> result = ApiParser.deduplicateEvents(events);
        assertEquals(2, result.size());
    }

    @Test
    public void testDeduplicateEvents_RemovesDuplicate() {
        String iso = "2026-04-12T10:00:00.000Z";
        EventAdapter.EventItem a = makeEvent("7", iso);
        EventAdapter.EventItem b = makeEvent("7", iso); // same user + same minute → duplicate
        List<EventAdapter.EventItem> events = java.util.Arrays.asList(a, b);
        List<EventAdapter.EventItem> result = ApiParser.deduplicateEvents(events);
        assertEquals(1, result.size());
    }

    @Test
    public void testDeduplicateEvents_SortedDescending() {
        EventAdapter.EventItem older = makeEvent("1", "2026-04-10T08:00:00.000Z");
        EventAdapter.EventItem newer = makeEvent("2", "2026-04-12T12:00:00.000Z");
        List<EventAdapter.EventItem> events = java.util.Arrays.asList(older, newer);
        List<EventAdapter.EventItem> result = ApiParser.deduplicateEvents(events);
        assertEquals(2, result.size());
        // Newest first
        assertTrue(result.get(0).sortTimestamp >= result.get(1).sortTimestamp);
        assertEquals("2", result.get(0).otherUserId);
    }

    @Test
    public void testDeduplicateEvents_PopulatesSortTimestamp() {
        EventAdapter.EventItem item = makeEvent("5", "2026-04-12T10:00:00.000Z");
        assertEquals(0, item.sortTimestamp); // not set yet
        List<EventAdapter.EventItem> result = ApiParser.deduplicateEvents(java.util.Collections.singletonList(item));
        assertTrue(result.get(0).sortTimestamp > 0);
    }

    @Test
    public void testDeduplicateEvents_DifferentMinutesNotDuplicated() {
        // Same user but different minute → should NOT be de-duped
        EventAdapter.EventItem e1 = makeEvent("9", "2026-04-12T10:05:00.000Z");
        EventAdapter.EventItem e2 = makeEvent("9", "2026-04-12T10:06:00.000Z");
        List<EventAdapter.EventItem> result = ApiParser.deduplicateEvents(java.util.Arrays.asList(e1, e2));
        assertEquals(2, result.size());
    }
}
