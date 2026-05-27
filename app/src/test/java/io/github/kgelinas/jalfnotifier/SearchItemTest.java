package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.*;

import org.junit.Test;

public class SearchItemTest {

    @Test
    public void testGetSummary_Full() {
        SearchAdapter.SearchItem item = new SearchAdapter.SearchItem();
        item.age = "25 ans";
        item.location = "Montreal";
        item.lastConnection = "Aujourd'hui";

        assertEquals("25 ans \u2022 Montreal \u2022 Aujourd'hui", item.getSummary());
    }

    @Test
    public void testGetSummary_Partial() {
        SearchAdapter.SearchItem item = new SearchAdapter.SearchItem();
        item.age = "30 ans";
        item.location = "";
        item.lastConnection = "Hier";

        assertEquals("30 ans \u2022 Hier", item.getSummary());
    }

    @Test
    public void testGetSummary_FallbackToDescription() {
        SearchAdapter.SearchItem item = new SearchAdapter.SearchItem();
        item.age = "";
        item.location = null;
        item.lastConnection = "";
        item.description = "Looking for fun";

        assertEquals("Looking for fun", item.getSummary());
    }

    @Test
    public void testGetSummary_Empty() {
        SearchAdapter.SearchItem item = new SearchAdapter.SearchItem();
        item.age = "";
        item.location = "";
        item.lastConnection = "";
        item.description = "";

        assertEquals("", item.getSummary());
    }
}
