package io.github.kgelinas.jalfnotifier.data;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class SearchSettingsManager {
    private static final String TAG = "SearchSettingsManager";
    private static final String PREF_NAME = "search_prefs";
    private static final String KEY_SAVED_SEARCHES_DET = "named_searches_det";
    private static final String KEY_SAVED_SEARCHES_SIMPLE = "named_searches_simple";
    private static final String KEY_ADVANCED_TOGGLE_STATE = "advanced_toggle_state";

    public static void saveAdvancedToggleState(Context context, boolean isAdvanced) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ADVANCED_TOGGLE_STATE, isAdvanced).apply();
    }

    public static boolean loadAdvancedToggleState(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ADVANCED_TOGGLE_STATE, false);
    }

    public static class SavedSettings {
        // Search Parameters (Advanced Search API)
        public List<String> seekDet = new ArrayList<>();
        public List<String> wantedDet = new ArrayList<>();
        public List<String> orientDet = new ArrayList<>();
        public List<String> statusDet = new ArrayList<>();
        public List<String> relCherDet = new ArrayList<>();
        public List<String> availDet = new ArrayList<>();
        public List<String> smokerDet = new ArrayList<>();
        public List<String> zodiacDet = new ArrayList<>();

        public static class SavedLocation {
            public String countryLabel, countryValue, countryLink;
            public String provinceLabel, provinceValue, provinceLink;
            public String regionLabel, regionValue, regionLink;
        }

        public List<SavedLocation> selectedLocationsDet = new ArrayList<>();
        public List<Integer> fantasyIdsDet = new ArrayList<>();
        public String ageMinDet = "18";
        public String ageMaxDet = "99";
        public float weightMinDet = 0, weightMaxDet = 200;
        public float heightMinDet = 100, heightMaxDet = 250;
        public boolean webcamDet = false;
        public boolean speedMeetingDet = false;
        public boolean isAdvancedSearch = false;
        public String orderByDet = "1";
        public String ethnicDet = "";

        // Simple search fields
        public String seekSimple = "", seekLabelSimple = "";
        public String wantedSimple = "", wantedLabelSimple = "";
        public String orientSimple = "", orientLabelSimple = "";
        public String pseudoSimple = "";
        public String ageMinSimple = "18";
        public String ageMaxSimple = "99";
        public boolean onlineSimple = false;
        public boolean excludeChatted = false;
        public boolean photoSimple = false;
        public boolean myRegionSimple = false;
        public boolean desiresTodaySimple = false;
        public String orderBySimple = "1";
        public String countryLabelSimple = "";
        public String countryLinkSimple = "";
        public String countryValueSimple = "";
        public String provinceLabelSimple = "";
        public String provinceLinkSimple = "";
        public String provinceValueSimple = "";
        public String regionLabelSimple = "";
        public String regionLinkSimple = "";
        public String regionValueSimple = "";
    }

    public static class NamedSearch {
        public String name;
        public SavedSettings settings;

        public NamedSearch(String name, SavedSettings settings) {
            this.name = name;
            this.settings = settings;
        }
    }

    public static void saveNamedSearch(Context context, String name, SavedSettings settings, boolean unusedIsDetailed) {
        try {
            JSONObject allSearches = loadAllSearchesJson(context);
            allSearches.put(name, serializeSettings(settings));
            saveAllSearchesJson(context, allSearches);
        } catch (Exception e) {
            Log.e(TAG, "Error saving named search: " + name, e);
        }
    }

    public static List<NamedSearch> loadNamedSearches(Context context, boolean unusedIsDetailed) {
        List<NamedSearch> list = new ArrayList<>();
        try {
            JSONObject allSearches = loadAllSearchesJson(context);
            JSONArray names = allSearches.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.getString(i);
                    list.add(new NamedSearch(name, parseSettings(allSearches.getJSONObject(name))));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading named searches", e);
        }
        return list;
    }

    public static void deleteNamedSearch(Context context, String name, boolean unusedIsDetailed) {
        try {
            JSONObject allSearches = loadAllSearchesJson(context);
            allSearches.remove(name);
            saveAllSearchesJson(context, allSearches);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting named search: " + name, e);
        }
    }

    // ─── Simple search CRUD ──────────────────────────────────────────────────

    public static void saveNamedSearchSimple(Context context, String name, SavedSettings settings) {
        try {
            JSONObject all = loadSearchesJson(context, KEY_SAVED_SEARCHES_SIMPLE);
            all.put(name, serializeSimpleSettings(settings));
            saveSearchesJson(context, KEY_SAVED_SEARCHES_SIMPLE, all);
        } catch (Exception e) {
            Log.e(TAG, "Error saving simple named search: " + name, e);
        }
    }

    public static List<NamedSearch> loadNamedSearchesSimple(Context context) {
        List<NamedSearch> list = new ArrayList<>();
        try {
            JSONObject all = loadSearchesJson(context, KEY_SAVED_SEARCHES_SIMPLE);
            JSONArray names = all.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String n = names.getString(i);
                    list.add(new NamedSearch(n, parseSimpleSettings(all.getJSONObject(n))));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading simple named searches", e);
        }
        return list;
    }

    public static void deleteNamedSearchSimple(Context context, String name) {
        try {
            JSONObject all = loadSearchesJson(context, KEY_SAVED_SEARCHES_SIMPLE);
            all.remove(name);
            saveSearchesJson(context, KEY_SAVED_SEARCHES_SIMPLE, all);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting simple named search: " + name, e);
        }
    }

    public static void renameNamedSearchSimple(Context context, String oldName, String newName) {
        try {
            JSONObject all = loadSearchesJson(context, KEY_SAVED_SEARCHES_SIMPLE);
            if (all.has(oldName)) {
                Object s = all.get(oldName);
                all.remove(oldName);
                all.put(newName, s);
                saveSearchesJson(context, KEY_SAVED_SEARCHES_SIMPLE, all);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error renaming simple named search from " + oldName + " to " + newName, e);
        }
    }

    // ─── Detailed search CRUD ────────────────────────────────────────────────

    public static void renameNamedSearch(Context context, String oldName, String newName, boolean unusedIsDetailed) {
        try {
            JSONObject allSearches = loadAllSearchesJson(context);
            if (allSearches.has(oldName)) {
                Object settings = allSearches.get(oldName);
                allSearches.remove(oldName);
                allSearches.put(newName, settings);
                saveAllSearchesJson(context, allSearches);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error renaming named search from " + oldName + " to " + newName, e);
        }
    }

    private static JSONObject loadAllSearchesJson(Context context) throws Exception {
        return loadSearchesJson(context, KEY_SAVED_SEARCHES_DET);
    }

    private static JSONObject loadSearchesJson(Context context, String key) throws Exception {
        String raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(key, "{}");
        return new JSONObject(raw);
    }

    private static void saveAllSearchesJson(Context context, JSONObject json) {
        saveSearchesJson(context, KEY_SAVED_SEARCHES_DET, json);
    }

    private static void saveSearchesJson(Context context, String key, JSONObject json) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(key, json.toString()).apply();
    }

    private static JSONObject serializeSettings(SavedSettings settings) throws Exception {
        JSONObject json = new JSONObject();
        // Chips
        json.put("seekDet", new JSONArray(settings.seekDet));
        json.put("wantedDet", new JSONArray(settings.wantedDet));
        json.put("orientDet", new JSONArray(settings.orientDet));
        json.put("statusDet", new JSONArray(settings.statusDet));
        json.put("relCherDet", new JSONArray(settings.relCherDet));
        json.put("availDet", new JSONArray(settings.availDet));
        json.put("smokerDet", new JSONArray(settings.smokerDet));
        json.put("zodiacDet", new JSONArray(settings.zodiacDet));

        // Locations
        JSONArray locs = new JSONArray();
        for (SavedSettings.SavedLocation l : settings.selectedLocationsDet) {
            JSONObject lj = new JSONObject();
            lj.put("cLb", l.countryLabel);
            lj.put("cVl", l.countryValue);
            lj.put("cLk", l.countryLink);
            if (l.provinceValue != null) {
                lj.put("pLb", l.provinceLabel);
                lj.put("pVl", l.provinceValue);
                lj.put("pLk", l.provinceLink);
            }
            if (l.regionValue != null) {
                lj.put("rLb", l.regionLabel);
                lj.put("rVl", l.regionValue);
                lj.put("rLk", l.regionLink);
            }
            locs.put(lj);
        }
        json.put("selectedLocationsDet", locs);

        // Other
        json.put("fantasyIdsDet", new JSONArray(settings.fantasyIdsDet));
        json.put("ageMinDet", settings.ageMinDet);
        json.put("ageMaxDet", settings.ageMaxDet);
        json.put("weightMinDet", (double) settings.weightMinDet);
        json.put("weightMaxDet", (double) settings.weightMaxDet);
        json.put("heightMinDet", (double) settings.heightMinDet);
        json.put("heightMaxDet", (double) settings.heightMaxDet);
        json.put("webcamDet", settings.webcamDet);
        json.put("speedMeetingDet", settings.speedMeetingDet);
        json.put("isAdvancedSearch", settings.isAdvancedSearch);
        json.put("orderByDet", settings.orderByDet);
        json.put("ethnicDet", settings.ethnicDet);
        json.put("excludeChatted", settings.excludeChatted);
        return json;
    }

    private static SavedSettings parseSettings(JSONObject json) throws Exception {
        SavedSettings settings = new SavedSettings();
        
        settings.seekDet = toStringList(json.optJSONArray("seekDet"));
        settings.wantedDet = toStringList(json.optJSONArray("wantedDet"));
        settings.orientDet = toStringList(json.optJSONArray("orientDet"));
        settings.statusDet = toStringList(json.optJSONArray("statusDet"));
        settings.relCherDet = toStringList(json.optJSONArray("relCherDet"));
        settings.availDet = toStringList(json.optJSONArray("availDet"));
        settings.smokerDet = toStringList(json.optJSONArray("smokerDet"));
        settings.zodiacDet = toStringList(json.optJSONArray("zodiacDet"));

        JSONArray locs = json.optJSONArray("selectedLocationsDet");
        if (locs != null) {
            for (int i = 0; i < locs.length(); i++) {
                JSONObject lj = locs.getJSONObject(i);
                SavedSettings.SavedLocation l = new SavedSettings.SavedLocation();
                l.countryLabel = lj.optString("cLb");
                l.countryValue = lj.optString("cVl");
                l.countryLink = lj.optString("cLk");
                l.provinceLabel = lj.optString("pLb", null);
                l.provinceValue = lj.optString("pVl", null);
                l.provinceLink = lj.optString("pLk", null);
                l.regionLabel = lj.optString("rLb", null);
                l.regionValue = lj.optString("rVl", null);
                l.regionLink = lj.optString("rLk", null);
                settings.selectedLocationsDet.add(l);
            }
        }

        JSONArray fids = json.optJSONArray("fantasyIdsDet");
        if (fids != null) {
            for (int i = 0; i < fids.length(); i++)
                settings.fantasyIdsDet.add(fids.getInt(i));
        }

        settings.ageMinDet = json.optString("ageMinDet", "18");
        settings.ageMaxDet = json.optString("ageMaxDet", "99");
        settings.weightMinDet = (float) json.optDouble("weightMinDet", 0);
        settings.weightMaxDet = (float) json.optDouble("weightMaxDet", 200);
        settings.heightMinDet = (float) json.optDouble("heightMinDet", 100);
        settings.heightMaxDet = (float) json.optDouble("heightMaxDet", 250);
        settings.webcamDet = json.optBoolean("webcamDet", false);
        settings.speedMeetingDet = json.optBoolean("speedMeetingDet", false);
        settings.isAdvancedSearch = json.optBoolean("isAdvancedSearch", false);
        settings.orderByDet = json.optString("orderByDet", "1");
        settings.ethnicDet = json.optString("ethnicDet", "");
        settings.excludeChatted = json.optBoolean("excludeChatted", false);
        return settings;
    }

    private static List<String> toStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++)
                list.add(arr.optString(i));
        }
        return list;
    }

    private static JSONObject serializeSimpleSettings(SavedSettings s) throws Exception {
        JSONObject j = new JSONObject();
        j.put("seekSimple",         s.seekSimple);
        j.put("seekLabelSimple",    s.seekLabelSimple);
        j.put("wantedSimple",       s.wantedSimple);
        j.put("wantedLabelSimple",  s.wantedLabelSimple);
        j.put("orientSimple",       s.orientSimple);
        j.put("orientLabelSimple",  s.orientLabelSimple);
        j.put("pseudoSimple",       s.pseudoSimple);
        j.put("ageMinSimple",       s.ageMinSimple);
        j.put("ageMaxSimple",       s.ageMaxSimple);
        j.put("onlineSimple",       s.onlineSimple);
        j.put("excludeChatted",     s.excludeChatted);
        j.put("photoSimple",        s.photoSimple);
        j.put("myRegionSimple",     s.myRegionSimple);
        j.put("desiresTodaySimple", s.desiresTodaySimple);
        j.put("orderBySimple",      s.orderBySimple);
        j.put("countryLabelSimple",   s.countryLabelSimple);
        j.put("countryLinkSimple",    s.countryLinkSimple);
        j.put("countryValueSimple",   s.countryValueSimple);
        j.put("provinceLabelSimple",  s.provinceLabelSimple);
        j.put("provinceLinkSimple",   s.provinceLinkSimple);
        j.put("provinceValueSimple",  s.provinceValueSimple);
        j.put("regionLabelSimple",    s.regionLabelSimple);
        j.put("regionLinkSimple",     s.regionLinkSimple);
        j.put("regionValueSimple",    s.regionValueSimple);
        return j;
    }

    private static SavedSettings parseSimpleSettings(JSONObject j) throws Exception {
        SavedSettings s = new SavedSettings();
        s.seekSimple         = j.optString("seekSimple", "");
        s.seekLabelSimple    = j.optString("seekLabelSimple", "");
        s.wantedSimple       = j.optString("wantedSimple", "");
        s.wantedLabelSimple  = j.optString("wantedLabelSimple", "");
        s.orientSimple       = j.optString("orientSimple", "");
        s.orientLabelSimple  = j.optString("orientLabelSimple", "");
        s.pseudoSimple       = j.optString("pseudoSimple", "");
        s.ageMinSimple       = j.optString("ageMinSimple", "18");
        s.ageMaxSimple       = j.optString("ageMaxSimple", "99");
        s.onlineSimple       = j.optBoolean("onlineSimple", false);
        s.excludeChatted     = j.optBoolean("excludeChatted", false);
        s.photoSimple        = j.optBoolean("photoSimple", false);
        s.myRegionSimple     = j.optBoolean("myRegionSimple", false);
        s.desiresTodaySimple = j.optBoolean("desiresTodaySimple", false);
        s.orderBySimple      = j.optString("orderBySimple", "1");
        s.countryLabelSimple   = j.optString("countryLabelSimple", "");
        s.countryLinkSimple    = j.optString("countryLinkSimple", "");
        s.countryValueSimple   = j.optString("countryValueSimple", "");
        s.provinceLabelSimple  = j.optString("provinceLabelSimple", "");
        s.provinceLinkSimple   = j.optString("provinceLinkSimple", "");
        s.provinceValueSimple  = j.optString("provinceValueSimple", "");
        s.regionLabelSimple    = j.optString("regionLabelSimple", "");
        s.regionLinkSimple     = j.optString("regionLinkSimple", "");
        s.regionValueSimple    = j.optString("regionValueSimple", "");
        return s;
    }
}
