package io.github.kgelinas.jalfnotifier.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Manage navigation context for the next/previous pill in profiles and conversations.
 */
public class NavigationManager {

    public static class NavigationItem {
        public String userId;
        public String name;
        public String avatarUrl;
        public String conversationLink; // optional
        public String sexIconUrl; // optional
        public boolean isOnline;

        public NavigationItem(String userId, String name, String avatarUrl) {
            this.userId = userId;
            this.name = name;
            this.avatarUrl = avatarUrl;
        }
    }

    private static List<NavigationItem> navigationItems = new ArrayList<>();
    private static int currentIndex = -1;
    private static String currentSource = ""; // "search", "favorites", etc.

    public static void setNavigationList(String source, List<NavigationItem> items, int startIndex) {
        currentSource = source;
        navigationItems = items != null ? items : new ArrayList<>();
        currentIndex = startIndex;
    }

    public static boolean hasNavigation() {
        return navigationItems.size() > 1 && currentIndex >= 0 && currentIndex < navigationItems.size();
    }

    public static int getCurrentIndex() {
        return currentIndex;
    }

    public static int getTotalCount() {
        return navigationItems.size();
    }

    public static NavigationItem getCurrentItem() {
        if (currentIndex >= 0 && currentIndex < navigationItems.size()) {
            return navigationItems.get(currentIndex);
        }
        return null;
    }

    public static NavigationItem navigateTo(int index) {
        if (index >= 0 && index < navigationItems.size()) {
            currentIndex = index;
            return navigationItems.get(currentIndex);
        }
        return null;
    }

    public static NavigationItem next() {
        if (currentIndex < navigationItems.size() - 1) {
            currentIndex++;
            return navigationItems.get(currentIndex);
        }
        return null;
    }

    public static NavigationItem prev() {
        if (currentIndex > 0) {
            currentIndex--;
            return navigationItems.get(currentIndex);
        }
        return null;
    }

    public static void clear() {
        navigationItems.clear();
        currentIndex = -1;
        currentSource = "";
    }
}
