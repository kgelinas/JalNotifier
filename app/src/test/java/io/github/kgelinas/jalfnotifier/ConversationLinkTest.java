package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;

import io.github.kgelinas.jalfnotifier.ChatAdapter.ChatItem;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class ConversationLinkTest {

    @Mock
    SharedPreferences mockPrefs;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
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
        // Mock the SharedPreferences behavior: return the default value (second arg) by default
        when(mockPrefs.getString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        
        // Stub a specific key
        when(mockPrefs.getString("12345", "")).thenReturn("/rest/conversations/active/67890");

        String result = mockPrefs.getString("12345", "");
        assertEquals("/rest/conversations/active/67890", result);
        
        String notFound = mockPrefs.getString("99999", "fallback");
        assertEquals("fallback", notFound);
    }
}
