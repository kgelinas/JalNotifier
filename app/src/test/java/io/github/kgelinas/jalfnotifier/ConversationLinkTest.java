package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.SharedPreferences;

import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.ui.ChatAdapter.ChatItem;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationLinkTest {

    private SharedPreferences mockPrefs;
    private final Map<String, String> prefsStorage = new HashMap<>();

    @Before
    public void setUp() {
        prefsStorage.clear();
        prefsStorage.put("12345", "/rest/conversations/active/67890");

        mockPrefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if ("getString".equals(method.getName())) {
                        String key = (String) args[0];
                        String defValue = (String) args[1];
                        return prefsStorage.getOrDefault(key, defValue);
                    }
                    return null;
                });
    }

    /**
     * Helper to simulate the search logic in MainActivity.
     * In a real app, this logic is in findConversationForUser.
     */
    private String findInList(List<ChatItem> list, String otherUserId) {
        if (list == null)
            return null;
        for (ChatItem item : list) {
            if (otherUserId.equals(item.otherUserId)) {
                return item.conversationLink;
            }
        }
        return null;
    }

    @Test
    public void testFindInList_Success() {
        List<ChatItem> items = new ArrayList<>();
        ChatItem item = new ChatItem(ChatAdapter.TYPE_CHAT);
        item.otherUserId = "12345";
        item.conversationLink = "/rest/conversations/active/67890";
        items.add(item);

        String result = findInList(items, "12345");
        assertEquals("/rest/conversations/active/67890", result);
    }

    @Test
    public void testFindInList_NotFound() {
        List<ChatItem> items = new ArrayList<>();
        ChatItem item = new ChatItem(ChatAdapter.TYPE_CHAT);
        item.otherUserId = "12345";
        items.add(item);

        String result = findInList(items, "99999");
        assertNull(result);
    }

    @Test
    public void testSharedPreferencesLookup() {
        String result = mockPrefs.getString("12345", "");
        assertEquals("/rest/conversations/active/67890", result);

        String notFound = mockPrefs.getString("99999", "fallback");
        assertEquals("fallback", notFound);
    }
}
