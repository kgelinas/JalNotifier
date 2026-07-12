package io.github.kgelinas.jalfnotifier;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, stateless utility class for parsing API responses into data models.
 * All methods are free of side-effects (no SharedPreferences, no Context),
 * making
 * them fully testable on the JVM without an Android emulator.
 */
public class ApiParser {

    private ApiParser() {
        // Utility class - do not instantiate
    }

    // ==============================================================
    // Conversation JSON Parsing
    // ==============================================================

    /**
     * Parses a raw conversation JSON array into a list of ChatItem objects.
     * Note: SharedPreferences persistence is the caller's responsibility.
     *
     * @param arr         JSON array from the conversation API endpoint
     * @param forceUnread if true, marks all items as unread regardless of server
     *                    state
     * @param myUserId    the logged-in user's ID, used to determine message
     *                    ownership
     * @return list of parsed ChatItem objects
     */
    public static List<ChatAdapter.ChatItem> parseConversationJson(JSONArray arr, boolean forceUnread,
            String myUserId) {
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
                    item.avatarUrl = m.optString("profile_photo_uri", "");
                    if (item.avatarUrl != null && !item.avatarUrl.isEmpty()
                            && !item.avatarUrl.startsWith("http")) {
                        item.avatarUrl = ApiConstants.BASE_URL + item.avatarUrl;
                    }
                    item.isOnline = m.optBoolean("online", false);

                    if (!m.isNull("read_until")) {
                        item.otherReadUntil = m.optString("read_until", "");
                    } else {
                        item.otherReadUntil = "";
                    }

                    String uLink = m.optString("user_link", "");
                    item.otherUserId = StringUtils.extractUserIdFromLink(uLink);
                    item.sexLink = m.optString("sex_link", "");

                    if ("deleted".equals(m.optString("type", ""))) {
                        item.name = "Profil supprimé";
                        item.avatarUrl = "";
                        item.sexLink = "";
                    }
                }

                JSONObject lastMsg = obj.optJSONObject("last_message");
                JSONObject preview = obj.optJSONObject("last_message_preview");
                if (lastMsg != null) {
                    item.lastMessagePosted = lastMsg.optString("posted", "");
                    String type = lastMsg.optString("type", "");
                    JSONObject content = lastMsg.optJSONObject("content");
                    if ("photo".equals(type)) {
                        item.lastMessage = "📷 Photo";
                    } else if (content != null) {
                        item.lastMessage = content.optString("text", "");
                    } else {
                        item.lastMessage = "";
                    }
                    String fromLink = lastMsg.optString("from_user_link", "");
                    item.isLastMessageMine = !fromLink.isEmpty() && fromLink.endsWith("/" + myUserId);
                    item.isEphemeral = lastMsg.optJSONObject("ephemeral") != null;
                } else if (preview != null) {
                    item.lastMessage = preview.optString("text", "");
                } else {
                    item.lastMessage = "";
                }

                if ("Profil supprimé".equals(item.name)) {
                    item.lastMessage = "Compte supprimé";
                }

                item.lastPosted = obj.optString("last_posted", "");
                if (item.lastPosted.isEmpty() && lastMsg != null) {
                    item.lastPosted = lastMsg.optString("posted", "");
                }
                item.timeIso = item.lastPosted;
                item.unreadCount = obj.optInt("unread_messages_count", 0);
                item.isUnread = forceUnread || (item.unreadCount > 0) || obj.optBoolean("is_new", false);
                item.conversationLink = obj.optString("conversation_link", "");
                // nsfwRank is enriched asynchronously via /rest/users/{id} profile fetch
                item.nsfwRank = 0;

                result.add(item);
            } catch (Exception e) {
                // Skip malformed entries
            }
        }
        return result;
    }

    // ==============================================================
    // Search Results HTML Parsing
    // ==============================================================

    /**
     * Parses the HTML of a JALF profile search page into a list of SearchItem
     * objects.
     *
     * @param html the raw HTML string from the search results page
     * @return list of parsed SearchItem objects
     */
    public static List<SearchAdapter.SearchItem> parseSearchResults(String html) {
        List<SearchAdapter.SearchItem> list = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements profileBlocks = doc.select(".search_tab_block_A, .search_tab_block_B");

        for (Element block : profileBlocks) {
            try {
                String userId = "";
                String name = "";
                Element nameEl = block.selectFirst("a.m_mbrprof, a.mbrprof");
                if (nameEl != null) {
                    name = nameEl.text();
                    String href = nameEl.attr("href");
                    Matcher m = Pattern.compile("/ct/memberProfile/([^/]+)").matcher(href);
                    if (m.find()) {
                        userId = m.group(1);
                    } else {
                        String[] parts = href.split("/");
                        if (parts.length >= 4) {
                            userId = parts[parts.length - 2];
                        }
                    }
                }

                if (userId.isEmpty())
                    continue;

                String avatarUrl = "";
                Element imgEl = block.selectFirst(".pictures img");
                if (imgEl != null) {
                    avatarUrl = imgEl.attr("src");
                    if (avatarUrl.startsWith("/")) {
                        avatarUrl = ApiConstants.BASE_URL + avatarUrl;
                    }
                }

                String age = "";
                String location = "";
                String description = "";
                String sexIconUrl = "";
                String lastConnection = "";
                boolean isOnline = block.selectFirst("img[src*=ic_online]") != null;
                boolean isVip = block.selectFirst("img[src*=ic_vip]") != null;
                int nsfwRank = 0;
                Element ratingEl = block.selectFirst("a[href*=/rating/], a[href*=/sex/], .rating_link, [class*=rating], img[src*=/rating/], img[src*=/sex/]");
                if (ratingEl != null) {
                    String href = ratingEl.attr("href");
                    if (href.isEmpty() && ratingEl.hasAttr("src"))
                        href = ratingEl.attr("src");
                    nsfwRank = StringUtils.extractRankFromLink(href);
                }

                List<String> privIcons = new ArrayList<>();
                Elements icons = block.select("img[src*=ic_vip], img[src*=ic_gold], img[src*=ic_privilege]");
                for (Element img : icons) {
                    String src = img.attr("src");
                    if (!src.isEmpty()) {
                        if (!src.startsWith("http"))
                            src = ApiConstants.BASE_URL + src;
                        if (!privIcons.contains(src))
                            privIcons.add(src);
                    }
                }

                Element offlineSpan = block.selectFirst("span.offline");
                if (offlineSpan != null) {
                    Element leftDiv = offlineSpan.selectFirst("div.left");
                    if (leftDiv != null) {
                        String connText = leftDiv.html().replace("<br>", "\n").replaceAll("<[^>]*>", "").trim();
                        if (connText.toLowerCase().contains("dernière connexion")) {
                            lastConnection = connText.replaceAll("(?i)dernière connexion", "").trim().replace("\n",
                                    " ");
                        } else {
                            lastConnection = connText.replace("\n", " ");
                        }
                    }
                }

                Elements userbutDivs = block.select(".userbut > div");
                for (int i = 0; i < userbutDivs.size(); i++) {
                    Element div = userbutDivs.get(i);
                    String text = div.text().trim();

                    Element sexImg = div.selectFirst("img[src*=ic_sex]");
                    if (sexImg != null) {
                        sexIconUrl = ApiConstants.BASE_URL + sexImg.attr("src");
                    }

                    if (text.contains("ans") && age.isEmpty()) {
                        age = text;
                    } else if (text.contains("uel(le)") || text.contains(", ")) {
                        description = text;
                    } else if (div.attr("style") != null && div.attr("style").contains("border-bottom")) {
                        location = div.ownText().trim();
                        if (location.endsWith(","))
                            location = location.substring(0, location.length() - 1).trim();

                        Element distP = div.selectFirst("p.nbrkm");
                        if (distP != null && !distP.text().isEmpty()) {
                            String dist = distP.text().trim();
                            if (!dist.isEmpty()) {
                                location += " • " + dist;
                            }
                        }
                    }
                }

                SearchAdapter.SearchItem item = new SearchAdapter.SearchItem(
                        userId, name, avatarUrl, age, location, isOnline, isVip, description, sexIconUrl,
                        lastConnection, privIcons);
                item.nsfwRank = nsfwRank;
                item.detailsFetched = false; // Allow REST enrichment to refine data (city, NSFW rank, etc.) without hitting HTML profile
                list.add(item);

            } catch (Exception e) {
                // Skip malformed blocks
            }
        }
        return list;
    }

    // ==============================================================
    // Date / Time Parsing
    // ==============================================================

    /**
     * Converts a French relative/absolute date string into an ISO-8601 UTC
     * timestamp.
     * Handles formats like "Aujourd'hui", "Hier", "il y a 3 jours", "29 mars 2026 @
     * 12:44:19".
     *
     * @param rawDate the raw French date string
     * @return ISO-8601 UTC timestamp string, or empty string if input is null/empty
     */
    public static String parseLookerDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty())
            return "";
        rawDate = rawDate.trim().replaceAll("\\s+", " ");
        Calendar cal = Calendar.getInstance();

        String timePart = "";
        if (rawDate.contains("@")) {
            String[] parts = rawDate.split("@");
            if (parts.length > 1) {
                timePart = parts[1].trim();
                rawDate = parts[0].trim();
            }
        }

        boolean dateFound = false;
        if (rawDate.contains("Aujourd'hui")) {
            dateFound = true;
        } else if (rawDate.contains("Hier")) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
            dateFound = true;
        } else if (rawDate.contains("il y a")) {
            dateFound = true;
            String[] parts = rawDate.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    try {
                        int amount = Integer.parseInt(parts[i - 1]);
                        if (parts[i].startsWith("minut")) {
                            cal.add(Calendar.MINUTE, -amount);
                        } else if (parts[i].startsWith("heur")) {
                            cal.add(Calendar.HOUR_OF_DAY, -amount);
                        } else if (parts[i].startsWith("jour")) {
                            cal.add(Calendar.DAY_OF_YEAR, -amount);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (!dateFound) {
            String dateRegex = "(\\d{1,2}) (janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre) (\\d{4})";
            Matcher dm = Pattern.compile(dateRegex, Pattern.CASE_INSENSITIVE).matcher(rawDate);
            if (dm.find()) {
                try {
                    int day = Integer.parseInt(dm.group(1));
                    String monthStr = dm.group(2).toLowerCase();
                    int year = Integer.parseInt(dm.group(3));
                    int month = getMonthIndex(monthStr);
                    if (month != -1) {
                        cal.set(year, month, day);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!timePart.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(timePart.length() > 5 ? "HH:mm:ss" : "HH:mm", Locale.US);
                Date time = sdf.parse(timePart);
                if (time != null) {
                    Calendar timeCal = Calendar.getInstance();
                    timeCal.setTime(time);
                    cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                    cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                    cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND));
                    cal.set(Calendar.MILLISECOND, 0);
                }
            } catch (Exception ignored) {
            }
        }

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return isoFormat.format(cal.getTime());
    }

    /**
     * Returns the 0-based month index for a French month name, or -1 if not found.
     */
    public static int getMonthIndex(String month) {
        String[] months = { "janvier", "février", "mars", "avril", "mai", "juin",
                "juillet", "août", "septembre", "octobre", "novembre", "décembre" };
        for (int i = 0; i < months.length; i++) {
            if (months[i].equals(month))
                return i;
        }
        return -1;
    }

    /**
     * Parses an ISO-8601 timestamp string into epoch milliseconds.
     * Handles both "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" and "yyyy-MM-dd HH:mm:ss"
     * formats.
     *
     * @param iso the ISO timestamp string
     * @return epoch milliseconds, or 0 if parsing fails
     */
    public static long parseIsoToMillis(String iso) {
        if (iso == null || iso.isEmpty())
            return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(iso);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                Date date = sdf2.parse(iso);
                return date != null ? date.getTime() : 0;
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    /**
     * Normalizes a poke image URL from its verbose versioned form to its simple
     * canonical form.
     * e.g. /jalf/images/pokes/42/thumb_poke_42_1_1.png ->
     * /jalf/images/pokes/thumb_poke_42.png
     *
     * @param rawUrl the raw URL string (may be null or empty)
     * @return the normalized URL, or the original URL if it doesn't match the
     *         expected pattern
     */
    public static String fixPokeImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty())
            return rawUrl;
        Matcher m = Pattern.compile("pokes/(\\d+)/").matcher(rawUrl);
        if (m.find()) {
            return "/jalf/images/pokes/thumb_poke_" + m.group(1) + ".png";
        }
        return rawUrl;
    }
    // ==============================================================
    // French Chat Date Parsing
    // ==============================================================

    /**
     * Converts a French conversational date string ("Aujourd'hui \u00e0 14h30")
     * into epoch milliseconds.
     * Used for sorting chat list items that lack ISO timestamps.
     *
     * @param dateStr the raw French date string
     * @return epoch milliseconds, defaulting to current time if format is
     *         unrecognized
     */
    public static long parseFrenchDateToTimestamp(String dateStr) {
        Calendar cal = Calendar.getInstance();
        if (dateStr == null || dateStr.isEmpty())
            return cal.getTimeInMillis();
        dateStr = dateStr.toLowerCase();
        if (dateStr.contains("aujourd'hui")) {
            String time = getPart(dateStr, "\u00e0");
            applyHourMinute(cal, time);
        } else if (dateStr.contains("hier")) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
            String time = getPart(dateStr, "\u00e0");
            applyHourMinute(cal, time);
        } else {
            // "12 jan \u00e0 10h45"
            try {
                String dayShortMonth = dateStr.substring(0, dateStr.indexOf("\u00e0")).trim();
                SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.FRENCH);
                Date d = sdf.parse(dayShortMonth);
                if (d != null) {
                    Calendar temp = Calendar.getInstance();
                    temp.setTime(d);
                    cal.set(Calendar.DAY_OF_MONTH, temp.get(Calendar.DAY_OF_MONTH));
                    cal.set(Calendar.MONTH, temp.get(Calendar.MONTH));
                    String time = getPart(dateStr, "\u00e0");
                    applyHourMinute(cal, time);
                }
            } catch (Exception ignored) {
            }
        }
        return cal.getTimeInMillis();
    }

    private static String getPart(String s, String delimiter) {
        int idx = s.indexOf(delimiter);
        return idx >= 0 ? s.substring(idx + 1).trim() : "";
    }

    private static void applyHourMinute(Calendar cal, String timeHhMm) {
        try {
            String[] parts = timeHhMm.split("h");
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0].trim()));
            cal.set(Calendar.MINUTE, parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0);
        } catch (Exception ignored) {
        }
    }

    // ==============================================================
    // HTML Select Option Parsing
    // ==============================================================

    /**
     * Parses all {@code <option>} elements from an HTML {@code <select>} element
     * identified by the given CSS selector, into a list of {@link SearchOption}
     * objects.
     *
     * @param doc      parsed Jsoup document
     * @param selector CSS selector identifying the {@code <select>} element
     * @return list of parsed options; empty list if selector does not match or
     *         inputs are null
     */
    public static List<SearchOption> parseSelectOptions(Document doc, String selector) {
        List<SearchOption> list = new ArrayList<>();
        if (doc == null || selector == null)
            return list;
        Element select = doc.selectFirst(selector);
        if (select != null) {
            Elements options = select.select("option");
            for (Element opt : options) {
                list.add(new SearchOption(opt.attr("value"), opt.text()));
            }
        }
        return list;
    }

    // ==============================================================
    // Notification JSON Parsing
    // ==============================================================

    /**
     * Parses the JALF REST notification JSON array into a list of EventItem
     * objects.
     * Does NOT perform any network calls or SharedPreferences writes.
     *
     * @param arr the raw JSON array from the notifications endpoint
     * @return list of parsed EventItem objects (callers must still fetch profile
     *         details)
     */
    public static List<EventAdapter.EventItem> parseNotificationJson(JSONArray arr) {
        List<EventAdapter.EventItem> result = new ArrayList<>();
        if (arr == null)
            return result;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                EventAdapter.EventItem item = new EventAdapter.EventItem();

                item.otherUserId = obj.optString("user_id", "");
                if (item.otherUserId.isEmpty()) {
                    item.otherUserId = obj.optString("id", "");
                }
                if (item.otherUserId.isEmpty() || !item.otherUserId.matches("\\d+")) {
                    String userLink = obj.optString("user_link", "");
                    String extracted = StringUtils.extractUserIdFromLink(userLink);
                    if (extracted != null)
                        item.otherUserId = extracted;
                }

                item.title = obj.optString("title",
                        obj.optString("user_name", obj.optString("pseudo", "Someone")));
                item.avatarUrl = obj.optString("avatar_url", "");
                if (item.avatarUrl != null && !item.avatarUrl.isEmpty()
                        && !item.avatarUrl.startsWith("http")) {
                    item.avatarUrl = ApiConstants.BASE_URL + item.avatarUrl;
                }

                String why = obj.optString("why", "");
                int type = obj.optInt("type", -1);
                JSONObject refData = obj.optJSONObject("reference_data");
                String refType = (refData != null) ? refData.optString("type", "") : "";

                String body = "Notification";
                String secondaryImageUrl = null;

                if ("favorite_of".equals(why) || type == 22) {
                    body = "added you as a favorite";
                } else if ("your_favorite".equals(why) || type == 6) {
                    body = "added a new photo";
                    if (refData != null)
                        secondaryImageUrl = refData.optString("thumbnail_uri", null);
                } else if (type == 20 || ("posted".equals(why) && "poke".equals(refType))) {
                    if (refData != null) {
                        body = refData.optString("suffix_text", "sent you a poke");
                        secondaryImageUrl = refData.optString("graphic_uriref", null);
                    } else {
                        body = "sent you a poke";
                    }
                } else if ("author".equals(why)) {
                    if (type == 3) {
                        body = "would like to recreate what's in this photo";
                    } else if ("photo".equals(refType)) {
                        body = "liked one of your photos";
                    }
                    if (refData != null)
                        secondaryImageUrl = refData.optString("thumbnail_uri", null);
                } else if ("visit".equals(why)) {
                    body = "visited your profile";
                } else {
                    body = obj.optString("message", why);
                }

                item.body = body;
                item.why = why;
                item.secondaryImageUrl = secondaryImageUrl;
                item.eventType = type;
                item.timeIso = obj.optString("when_added", obj.optString("date", ""));
                item.isUnread = obj.optBoolean("is_unread", false);
                item.sexLink = obj.optString("sex_link", "");
                item.nsfwRank = StringUtils.extractRankFromLink(item.sexLink);

                // Post-parsing normalization: detect 'why' from text if missing or generic
                if (item.why == null || item.why.isEmpty() || "notif".equals(item.why)) {
                    String b = item.body.toLowerCase();
                    if (b.contains("visité")) item.why = "visit";
                    else if (b.contains("salu") || b.contains("poke")) item.why = "salutation";
                    else if (b.contains("favori")) item.why = "favorite_of";
                    else if (b.contains("photo") && b.contains("ajouté")) item.why = "your_favorite";
                    else if (b.contains("photo") && (b.contains("aimé") || b.contains("like"))) item.why = "author";
                    else if (b.contains("notification")) item.why = "notification";
                }

                result.add(item);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    // ==============================================================
    // Poke HTML Parsing
    // ==============================================================

    /**
     * Parses the JALF poke page HTML into a list of EventItem objects.
     * Does NOT make any network calls.
     *
     * @param html the raw HTML string from the /ct/pokes endpoint
     * @return list of parsed EventItem objects; items with no otherUserId are
     *         excluded
     */
    public static List<EventAdapter.EventItem> parsePokeHtml(String html) {
        List<EventAdapter.EventItem> result = new ArrayList<>();
        if (html == null || html.isEmpty())
            return result;
        Document doc = Jsoup.parse(html);
        Elements userElements = doc.select("div.one_user");

        for (Element userEl : userElements) {
            EventAdapter.EventItem item = new EventAdapter.EventItem();

            Element onclickLink = userEl.selectFirst("a[onclick*=gotoprofile]");
            if (onclickLink != null) {
                item.title = onclickLink.text();
                String onclick = onclickLink.attr("onclick");
                Matcher m = Pattern.compile("gotoprofile\\((\\d+)").matcher(onclick);
                if (m.find())
                    item.otherUserId = m.group(1);
            }

            Element avatarImg = userEl.selectFirst("a.roll img.picture_border");
            if (avatarImg != null) {
                item.avatarUrl = avatarImg.attr("src");
                if (!item.avatarUrl.startsWith("http")) {
                    item.avatarUrl = ApiConstants.BASE_URL + item.avatarUrl;
                }
            }

            Element cadrePoke = userEl.selectFirst("div.cadrepoke");
            if (cadrePoke != null) {
                Element pokeText = cadrePoke.selectFirst("p.txt_pokes");
                if (pokeText != null) {
                    String body = pokeText.text();
                    if (item.title != null && body.startsWith(item.title)) {
                        body = body.substring(item.title.length()).trim();
                    }
                    item.body = body;
                }
                Element pokeImg = cadrePoke.selectFirst("img[src*=pokes]");
                if (pokeImg != null) {
                    item.secondaryImageUrl = fixPokeImageUrl(pokeImg.attr("src"));
                }
                Element dateEl = cadrePoke.selectFirst("p.date_show");
                if (dateEl != null) {
                    item.timeIso = parseLookerDate(dateEl.text());
                }
            }

            item.body = "sent you a salutation";
            item.why = "salutation";
            item.eventType = -3;

            if (item.otherUserId != null && !item.otherUserId.isEmpty() && item.title != null) {
                result.add(item);
            }
        }
        return result;
    }

    // ==============================================================
    // Event Deduplication
    // ==============================================================

    /**
     * De-duplicates a list of EventItems using userId + ISO timestamp (truncated to
     * minutes)
     * as the composite key. Populates {@code sortTimestamp} from {@code timeIso} if
     * missing.
     * Returns a new list sorted by sortTimestamp descending (most recent first).
     *
     * @param events the combined list of events from all sources
     * @return deduplicated + sorted list
     */
    public static List<EventAdapter.EventItem> deduplicateEvents(List<EventAdapter.EventItem> events) {
        java.util.Map<String, EventAdapter.EventItem> uniqueMap = new java.util.LinkedHashMap<>();
        for (EventAdapter.EventItem item : events) {
            String timeKey = (item.timeIso != null && item.timeIso.length() >= 16)
                    ? item.timeIso.substring(0, 16)
                    : (item.timeIso != null ? item.timeIso : "");
            String key = item.otherUserId + "_" + timeKey;

            if (item.sortTimestamp == 0 && item.timeIso != null) {
                item.sortTimestamp = parseIsoToMillis(item.timeIso);
            }

            if (!uniqueMap.containsKey(key)) {
                uniqueMap.put(key, item);
            }
        }

        List<EventAdapter.EventItem> result = new ArrayList<>(uniqueMap.values());
        java.util.Collections.sort(result, (a, b) -> Long.compare(b.sortTimestamp, a.sortTimestamp));
        return result;
    }

    // ==============================================================
    // Profile Viewers HTML Parsing
    // ==============================================================

    /**
     * Parses the JALF profile viewers page HTML into a list of EventItem objects.
     * Uses three fallback strategies to extract the user ID from each block:
     * <ol>
     * <li>onclick="gotoprofile(ID, ...)" attribute on an anchor element</li>
     * <li>href on {@code div.user_description p a} links containing
     * "/memberProfile/"</li>
     * <li>Numeric {@code id} attribute of inner {@code <div>} elements</li>
     * </ol>
     *
     * @param html the raw HTML string from the profile viewers endpoint
     * @return list of parsed EventItem objects; items missing a usersId or title
     *         are excluded
     */
    public static List<EventAdapter.EventItem> parseProfileViewersHtml(String html) {
        List<EventAdapter.EventItem> result = new ArrayList<>();
        if (html == null || html.isEmpty())
            return result;
        Document doc = Jsoup.parse(html);
        Elements userElements = doc.select("div.one_user");

        for (Element userEl : userElements) {
            EventAdapter.EventItem item = new EventAdapter.EventItem();

            // Strategy 1: onclick="gotoprofile(ID, ...)"
            Element onclickLink = userEl.selectFirst("a[onclick*=gotoprofile]");
            if (onclickLink != null) {
                item.title = onclickLink.text();
                String onclick = onclickLink.attr("onclick");
                Matcher m = Pattern.compile("gotoprofile\\((\\d+)").matcher(onclick);
                if (m.find())
                    item.otherUserId = m.group(1);
            }

            // Strategy 2: href on div.user_description p a
            if (item.otherUserId == null || item.otherUserId.isEmpty()) {
                Element nameLink = userEl.selectFirst("div.user_description p a");
                if (nameLink != null) {
                    if (item.title == null)
                        item.title = nameLink.text();
                    String href = nameLink.attr("href");
                    if (href != null && href.contains("/memberProfile/")) {
                        String[] parts = href.split("/");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("memberProfile") && i + 1 < parts.length) {
                                item.otherUserId = parts[i + 1];
                                break;
                            }
                        }
                    }
                }
            }

            // Strategy 3: numeric id on inner div elements
            if (item.otherUserId == null || item.otherUserId.isEmpty()) {
                Elements idDivs = userEl.select("div[id]");
                for (Element idDiv : idDivs) {
                    if (idDiv != userEl) {
                        String rawId = idDiv.id().replaceAll("[^0-9]", "");
                        if (!rawId.isEmpty()) {
                            item.otherUserId = rawId;
                            break;
                        }
                    }
                }
            }

            // Avatar
            Element avatarImg = userEl.selectFirst("a.roll img.picture_border");
            if (avatarImg != null) {
                item.avatarUrl = avatarImg.attr("src");
                if (!item.avatarUrl.startsWith("http")) {
                    item.avatarUrl = ApiConstants.BASE_URL + item.avatarUrl;
                }
            }

            // Date
            Element dateEl = userEl.selectFirst("p.date_show_profil");
            if (dateEl != null)
                item.timeIso = parseLookerDate(dateEl.text());

            // NSFW Rank
            Element ratingEl = userEl.selectFirst("a[href*=/rating/], a[href*=/sex/], .rating_link, [class*=rating]");
            if (ratingEl != null) {
                String href = ratingEl.attr("href");
                if (href.isEmpty() && ratingEl.hasAttr("src")) href = ratingEl.attr("src");
                item.nsfwRank = StringUtils.extractRankFromLink(href);
            }

            item.body = "visited your profile";
            item.why = "visit";
            item.eventType = -2;

            if (item.otherUserId != null && !item.otherUserId.isEmpty() && item.title != null) {
                result.add(item);
            }
        }
        return result;
    }

    public static boolean parseIsImperial(String html) {
        if (html == null || html.isEmpty())
            return false;
        Document doc = Jsoup.parse(html);
        Element radioImperial = doc.selectFirst("input#flt_imperial");
        return radioImperial != null && radioImperial.hasAttr("checked");
    }

    // ==============================================================
    // Bookmark and Favorite HTML Parsing
    // ==============================================================

    private static void parseUserCard(Element card, FavoriteAdapter.FavoriteItem item) {
        // 1. Extract UserID and Name from onclick="gotoprofile(ID, ...)"
        Element onclickLink = card.selectFirst("a[onclick*=gotoprofile]");
        if (onclickLink != null) {
            String onclick = onclickLink.attr("onclick");
            Matcher m = Pattern.compile("gotoprofile\\((\\d+)").matcher(onclick);
            if (m.find()) {
                item.otherUserId = m.group(1);
                // Synthesize a canonical REST user link so this item can be stored in
                // CACHED_BOOKMARKS_LINKS and matched by isContactTypeNotificationAllowed().
                // HTML scrapers never provide a user_link field, so we build one here.
                if (item.userLink == null || item.userLink.isEmpty()) {
                    item.userLink = "/rest/users/" + item.otherUserId;
                }
            }
            item.name = onclickLink.text().trim();
        }

        // 2. Extract Distance
        String dist = "";
        Element distanceSpan = card.selectFirst("span.distance");
        if (distanceSpan != null) {
            dist = distanceSpan.text().trim().replaceAll("\\s+", " ");
            if (dist.equals("km"))
                dist = "";
        }
        item.distance = dist;

        // 3. Extract City and Age from p.descprof
        Element descProf = card.selectFirst("p.descprof");
        if (descProf != null) {
            String text = descProf.html();
            String[] parts = text.split("<br\\s*/?>");

            if (parts.length > 0) {
                String infoPart = parts[0].replaceAll("&nbsp;", " ").trim();
                Matcher ageMatcher = Pattern.compile("(\\d+)\\s+ans").matcher(infoPart);
                if (ageMatcher.find()) {
                    item.age = ageMatcher.group(0);
                }
            }

            if (parts.length > 1) {
                String localeLine = parts[parts.length - 1].replaceAll("&nbsp;", " ").trim();
                String[] localeParts = localeLine.split(",");
                if (localeParts.length > 0) {
                    item.city = localeParts[localeParts.length - 1].trim();
                }
            }

            StringBuilder details = new StringBuilder();
            if (item.age != null && !item.age.isEmpty()) {
                details.append(item.age);
            }
            if (item.city != null && !item.city.isEmpty()) {
                if (details.length() > 0)
                    details.append(", ");
                details.append(item.city);
            }
            if (!dist.isEmpty()) {
                if (details.length() > 0)
                    details.append(" (").append(dist).append(")");
                else
                    details.append(dist);
            }
            item.details = details.toString();
        }

        // 4. Extract Avatar URL
        Element avatarImg = card.selectFirst("img.picture_border");
        if (avatarImg != null) {
            String src = avatarImg.attr("src");
            if (!src.isEmpty()) {
                item.avatarUrl = src.startsWith("/") ? ApiConstants.BASE_URL + src : src;
            }
        }

        // 5. Extract NSFW Rank
        Element ratingEl = card.selectFirst("a[href*=/rating/], a[href*=/sex/], .rating_link, [class*=rating]");
        if (ratingEl != null) {
            String href = ratingEl.attr("href");
            if (href.isEmpty() && ratingEl.hasAttr("src")) href = ratingEl.attr("src");
            item.nsfwRank = StringUtils.extractRankFromLink(href);
        }

        // 6. Extract Sex Now / On Fire status
        item.isOnfire = card.selectFirst("img[src*=ic_sexnow]") != null;
    }

    /**
     * Parses the bookmarks page HTML into a list of FavoriteItem objects.
     */
    public static List<FavoriteAdapter.FavoriteItem> parseBookmarkHtml(String html) {
        List<FavoriteAdapter.FavoriteItem> result = new ArrayList<>();
        if (html == null || html.isEmpty())
            return result;

        Document doc = Jsoup.parse(html);
        for (Element card : doc.select("div.one_user")) {
            FavoriteAdapter.FavoriteItem item = new FavoriteAdapter.FavoriteItem();
            item.isBookmarked = true;
            item.isOnline = card.selectFirst("img[src*=ic_online]") != null;
            parseUserCard(card, item);
            if (item.otherUserId != null && !item.otherUserId.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Parses the "Mes favoris" page HTML (/ct/online/2) into FavoriteItem objects.
     */
    public static List<FavoriteAdapter.FavoriteItem> parseFavoritesHtml(String html) {
        List<FavoriteAdapter.FavoriteItem> result = new ArrayList<>();
        if (html == null || html.isEmpty())
            return result;

        Document doc = Jsoup.parse(html);
        for (Element card : doc.select("div.one_user")) {
            FavoriteAdapter.FavoriteItem item = new FavoriteAdapter.FavoriteItem();
            item.isFavorite = true;
            item.isOnline = card.selectFirst("img[src*=ic_online]") != null;
            item.isBookmarked = card.selectFirst("img[src*=bookmark_profile]") != null;
            parseUserCard(card, item);
            if (item.otherUserId != null && !item.otherUserId.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }
}
