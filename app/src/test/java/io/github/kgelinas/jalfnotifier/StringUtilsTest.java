package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class StringUtilsTest {

    @Test
    public void testNormalizeForMatch() {
        assertEquals("cafe", StringUtils.normalizeForMatch("Café"));
        assertEquals("ete", StringUtils.normalizeForMatch("Été"));
        assertEquals("garcon", StringUtils.normalizeForMatch("Garçon"));
        assertEquals("hello world", StringUtils.normalizeForMatch("  Hello World  "));
        assertEquals("", StringUtils.normalizeForMatch(null));
    }

    @Test
    public void testExtractUserIdFromLink() {
        assertEquals("4267780", StringUtils.extractUserIdFromLink("/rest/users/4267780"));
        assertEquals("123", StringUtils.extractUserIdFromLink("https://jalf.com/rest/users/123/profile"));
        assertEquals("999", StringUtils.extractUserIdFromLink("/abc/999")); // fallback logic
        assertEquals("", StringUtils.extractUserIdFromLink("/rest/users/abc"));
        assertEquals("", StringUtils.extractUserIdFromLink(null));
    }

    @Test
    public void testExtractUserIdFromLink_ExhaustiveBranches() {
        // link == null (A=true)
        assertEquals("", StringUtils.extractUserIdFromLink(null));
        // link.isEmpty() (A=false, B=true)
        assertEquals("", StringUtils.extractUserIdFromLink(""));
        // link neither (A=false, B=false)
        assertEquals("123", StringUtils.extractUserIdFromLink("user/123"));

        // index == -1 (A=false)
        assertEquals("123", StringUtils.extractUserIdFromLink("user/123"));

        // lastSlash == -1 (A=false). Expected to return "" because it is not a link.
        assertEquals("", StringUtils.extractUserIdFromLink("123"));

        // lastSlash == length - 1 (A=true, B=false)
        assertEquals("", StringUtils.extractUserIdFromLink("user/"));

        // candidate non-numeric (matches=false)
        assertEquals("", StringUtils.extractUserIdFromLink("user/abc"));
    }

    @Test
    public void testExtractNumericId() {
        // Standard links
        assertEquals("12345", StringUtils.extractNumericId("/ct/conversations/12345"));
        assertEquals("678", StringUtils.extractNumericId("/rest/users/9/conversations/678"));

        // Deeply nested message links
        assertEquals("92400074",
                StringUtils.extractNumericId("/rest/users/5339535/conversations/92400074/messages/1381109925"));

        // Links with trailing slashes or query params
        assertEquals("999", StringUtils.extractNumericId("/conversations/999/"));
        assertEquals("999", StringUtils.extractNumericId("/conversations/999?offset=0"));

        // Fallback behavior (users)
        assertEquals("4267780", StringUtils.extractNumericId("/rest/users/4267780"));

        // No ID
        assertEquals("", StringUtils.extractNumericId("/abc/def"));
        assertEquals("", StringUtils.extractNumericId(null));
    }

    @Test
    public void testIsSameConversation() {
        String link1 = "/rest/users/456/conversations/123";
        String link2 = "/ct/conversations/123";
        assertTrue(StringUtils.isSameConversation(link1, link2));

        String link3 = "https://jalf.com/ct/conversations/999";
        String link4 = "/ct/conversations/999";
        assertTrue(StringUtils.isSameConversation(link3, link4));

        String link5 = "/rest/users/1/conversations/1";
        String link6 = "/rest/users/2/conversations/2";
        assertTrue(!StringUtils.isSameConversation(link5, link6));

        assertTrue(!StringUtils.isSameConversation(null, link1));
    }

    @Test
    public void testConstructorIsPrivate() throws Exception {
        Constructor<StringUtils> constructor = StringUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }
}
