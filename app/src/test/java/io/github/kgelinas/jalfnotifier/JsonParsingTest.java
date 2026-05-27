package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class JsonParsingTest {

    /**
     * Replicates the core logic of MainActivity.parseConversationJson
     * for testing purposes.
     */
    private List<ChatAdapter.ChatItem> parseConversationJson(JSONArray arr) {
        List<ChatAdapter.ChatItem> result = new ArrayList<>();
        if (arr == null)
            return result;

        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                ChatAdapter.ChatItem item = new ChatAdapter.ChatItem(ChatAdapter.TYPE_CHAT);

                JSONArray members = obj.optJSONArray("other_members");
                if (members != null && members.length() > 0) {
                    JSONObject m = members.getJSONObject(0);
                    item.name = m.optString("name", "Unknown");
                    item.otherUserId = StringUtils.extractUserIdFromLink(m.optString("user_link", ""));
                }

                JSONObject lastMsg = obj.optJSONObject("last_message");
                if (lastMsg != null) {
                    JSONObject content = lastMsg.optJSONObject("content");
                    if (content != null) {
                        item.lastMessage = content.optString("text", "");
                    }
                }

                item.conversationLink = obj.optString("conversation_link", "");
                item.unreadCount = obj.optInt("total_unread_messages_count", 0);
                item.isUnread = item.unreadCount > 0;

                result.add(item);
            } catch (Exception e) {
                // Ignore for test
            }
        }
        return result;
    }

    @Test
    public void testParseConversationJson_Success() throws Exception {
        String jsonStr = "[" +
                "  {" +
                "    \"conversation_link\": \"/rest/conversations/123\"," +
                "    \"total_unread_messages_count\": 2," +
                "    \"other_members\": [{" +
                "      \"name\": \"Alice\"," +
                "      \"user_link\": \"/rest/users/456\"" +
                "    }]," +
                "    \"last_message\": {" +
                "      \"content\": { \"text\": \"Hello!\" }" +
                "    }" +
                "  }" +
                "]";

        JSONArray arr = new JSONArray(jsonStr);
        List<ChatAdapter.ChatItem> result = parseConversationJson(arr);

        assertEquals(1, result.size());
        ChatAdapter.ChatItem item = result.get(0);
        assertEquals("Alice", item.name);
        assertEquals("456", item.otherUserId);
        assertEquals("Hello!", item.lastMessage);
        assertEquals("/rest/conversations/123", item.conversationLink);
        assertTrue(item.isUnread);
        assertEquals(2, item.unreadCount);
    }

    @Test
    public void testParseConversationJson_EmptyPayload() throws Exception {
        JSONArray arr = new JSONArray("[]");
        List<ChatAdapter.ChatItem> result = parseConversationJson(arr);
        assertEquals(0, result.size());
    }
}
