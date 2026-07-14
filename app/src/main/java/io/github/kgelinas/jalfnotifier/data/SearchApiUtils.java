package io.github.kgelinas.jalfnotifier.data;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Request;
import io.github.kgelinas.jalfnotifier.util.NetworkUtils.Param;

/**
 * Stateless utility class for building JALF search requests.
 * Extracts the complex URL and FormBody construction from MainActivity.
 */
public class SearchApiUtils {

    private SearchApiUtils() {
        // Utility class
    }

    /**
     * Builds an okhttp3.Request for a Detailed Search.
     *
     * @param s          the detailed search settings
     * @param page       the page number (1-indexed)
     * @param fullCookie the session cookie
     * @param suid       the CSRF token (x-csrftoken)
     * @param orderBy    the order by value ("1", "2", "3")
     * @param ethnicTag  the selected ethnic group value
     * @param weightD    start weight ID (optional)
     * @param weightF    end weight ID (optional)
     * @param heightD    start height ID (optional)
     * @param heightF    end height ID (optional)
     * @return the constructed Request
     */
    public static Request buildDetailedSearchRequest(SearchSettingsManager.SavedSettings s, int page, String fullCookie,
            String suid, String orderBy, @Nullable String ethnicTag,
            @Nullable String weightD, @Nullable String weightF,
            @Nullable String heightD, @Nullable String heightF) {

        List<Param> params = new ArrayList<>();
        params.add(new Param("InitiateSearch", page == 1 ? "1" : "0"));
        params.add(new Param("search_detl_flg", "1"));

        addStringsToParams(params, s.seekDet, "seek");
        addStringsToParams(params, s.wantedDet, "wanted");
        addStringsToParams(params, s.orientDet, "orient");
        addStringsToParams(params, s.statusDet, "status");
        addStringsToParams(params, s.relCherDet, "relCher");
        addStringsToParams(params, s.availDet, "VIPDisponible");
        addStringsToParams(params, s.smokerDet, "VIPFumeur");
        addStringsToParams(params, s.zodiacDet, "VIPSigne");

        if (!s.selectedLocationsDet.isEmpty()) {
            StringBuilder locBuilder = new StringBuilder();
            for (int i = 0; i < s.selectedLocationsDet.size(); i++) {
                if (i > 0)
                    locBuilder.append("-");
                locBuilder.append(getFullLocationId(s.selectedLocationsDet.get(i)));
            }
            params.add(new Param("Location", locBuilder.toString()));
            SearchSettingsManager.SavedSettings.SavedLocation last = s.selectedLocationsDet
                    .get(s.selectedLocationsDet.size() - 1);
            params.add(new Param("cboPays", last.countryValue));
            if (last.provinceValue != null)
                params.add(new Param("cboProvince", last.provinceValue));
            if (last.regionValue != null)
                params.add(new Param("cboRegion", last.regionValue));
            params.add(new Param("multRegions", getFullLocationId(last)));
        }

        if (s.fantasyIdsDet != null && !s.fantasyIdsDet.isEmpty()) {
            StringBuilder fantasies = new StringBuilder();
            for (int i = 0; i < s.fantasyIdsDet.size(); i++) {
                if (i > 0)
                    fantasies.append(",");
                fantasies.append(s.fantasyIdsDet.get(i));
            }
            params.add(new Param("VIPFantasme", fantasies.toString()));
        }

        if (ethnicTag != null && !ethnicTag.equals("0") && !ethnicTag.isEmpty()) {
            params.add(new Param("VIPOrigine", ethnicTag));
        }

        params.add(new Param("tab_id", "1"));
        params.add(new Param("hiddenSId", ""));
        if (s.speedMeetingDet) params.add(new Param("VIPSpeedMeeting", "1"));
        params.add(new Param("h_pos_y", "500"));
        params.add(new Param("h_pos_x", "500"));
        params.add(new Param("btnPager", "1"));
        params.add(new Param("pager_on", "1"));

        params.add(new Param("VIPAgeD", s.ageMinDet.isEmpty() ? "18" : s.ageMinDet));
        params.add(new Param("VIPAgeF", s.ageMaxDet.isEmpty() ? "99" : s.ageMaxDet));

        if (weightD != null)
            params.add(new Param("VIPPoidsD", weightD));
        if (weightF != null)
            params.add(new Param("VIPPoidsF", weightF));
        if (heightD != null)
            params.add(new Param("VIPGrandeurD", heightD));
        if (heightF != null)
            params.add(new Param("VIPGrandeurF", heightF));

        params.add(new Param("orderBy", orderBy));

        HttpUrl.Builder urlBuilder = HttpUrl.parse(ApiConstants.BASE_URL + "/ct/searchResults/profile")
                .newBuilder();
        if (page > 1) {
            urlBuilder.addQueryParameter("InitiateSearch", "0");
            urlBuilder.addQueryParameter("page", String.valueOf(page));
            urlBuilder.addQueryParameter("orderBy", orderBy);
            urlBuilder.addQueryParameter("tab_id", "1");
            if (ethnicTag != null && !ethnicTag.equals("0") && !ethnicTag.isEmpty()) {
                urlBuilder.addQueryParameter("VIPOrigine", ethnicTag);
            }
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT);

        if (page > 1) {
            requestBuilder.get();
            requestBuilder.addHeader("Referer", ApiConstants.BASE_URL + "/ct/searchResults/profile");
        } else {
            requestBuilder.post(NetworkUtils.createIsoFormBody(params));
            requestBuilder.addHeader("Referer", ApiConstants.BASE_URL + "/ct/modSearchParamDet/1");
        }

        return requestBuilder.build();
    }

    /**
     * Builds an okhttp3.Request for a Simple Search (JALF /ct/search/0).
     *
     * @param seek          sex to search for (e.g. "F", "H", "C" – empty string for "any")
     * @param wanted        profile type wanted (same values as seek, empty = any)
     * @param orient        orientation value (empty = any)
     * @param pseudo        pseudo/username substring to search (empty = any)
     * @param ageMin        minimum age string (e.g. "18")
     * @param ageMax        maximum age string (e.g. "99")
     * @param online        whether to filter online-only
     * @param hasPhoto      whether to filter profiles with at least one photo
     * @param myRegion      whether to restrict to user's own region
     * @param desiresToday  whether to filter speed-meeting (desires today)
     * @param location      encoded location string (country[,province[,region]]) – empty = no filter
     * @param orderBy       "1" (last visit), "2" (registration), "3" (fantasies match)
     * @param page          1-indexed page number
     * @param fullCookie    session cookie
     * @param suid          CSRF token
     * @return the constructed Request
     */
    public static Request buildSimpleSearchRequest(
            String seek, String wanted, String orient,
            String pseudo, String ageMin, String ageMax,
            boolean online, boolean hasPhoto, boolean myRegion, boolean desiresToday,
            String location, String orderBy, int page,
            String fullCookie, String suid) {

        List<Param> params = new ArrayList<>();
        params.add(new Param("InitiateSearch", page == 1 ? "1" : "0"));
        params.add(new Param("search_detl_flg", "0"));
        params.add(new Param("tab_id", "0"));

        if (seek != null && !seek.isEmpty())
            params.add(new Param("seek", seek));
        if (wanted != null && !wanted.isEmpty())
            params.add(new Param("wanted", wanted));
        if (orient != null && !orient.isEmpty())
            params.add(new Param("orient", orient));
        if (pseudo != null && !pseudo.trim().isEmpty())
            params.add(new Param("pseudo", pseudo.trim()));

        params.add(new Param("VIPAgeD", ageMin == null || ageMin.isEmpty() ? "18" : ageMin));
        params.add(new Param("VIPAgeF", ageMax == null || ageMax.isEmpty() ? "99" : ageMax));

        if (online)        params.add(new Param("online",         "1"));
        if (hasPhoto)      params.add(new Param("a_photo",        "1"));
        if (myRegion)      params.add(new Param("maRegion",       "1"));
        if (desiresToday)  params.add(new Param("VIPSpeedMeeting", "1"));

        if (location != null && !location.isEmpty())
            params.add(new Param("Location", location));

        params.add(new Param("orderBy", orderBy != null ? orderBy : "1"));
        params.add(new Param("h_pos_y",   "500"));
        params.add(new Param("h_pos_x",   "500"));
        params.add(new Param("btnPager",   "1"));
        params.add(new Param("pager_on",   "1"));

        HttpUrl.Builder urlBuilder = HttpUrl.parse(ApiConstants.BASE_URL + "/ct/searchResults/profile")
                .newBuilder();
        if (page > 1) {
            urlBuilder.addQueryParameter("InitiateSearch", "0");
            urlBuilder.addQueryParameter("page", String.valueOf(page));
            urlBuilder.addQueryParameter("orderBy", orderBy);
            urlBuilder.addQueryParameter("tab_id", "0");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT);

        if (page > 1) {
            requestBuilder.get();
            requestBuilder.addHeader("Referer", ApiConstants.BASE_URL + "/ct/searchResults/profile");
        } else {
            requestBuilder.post(NetworkUtils.createIsoFormBody(params));
            requestBuilder.addHeader("Referer", ApiConstants.BASE_URL + "/ct/search/0");
        }

        return requestBuilder.build();
    }

    private static void addStringsToParams(List<Param> params, List<String> values, String paramName) {
        if (values == null)
            return;
        for (String val : values) {
            params.add(new Param(paramName, val));
        }
    }

    private static String getFullLocationId(SearchSettingsManager.SavedSettings.SavedLocation loc) {
        if (loc == null)
            return "";
        StringBuilder sb = new StringBuilder();
        if (loc.countryValue != null)
            sb.append(loc.countryValue);
        if (loc.provinceValue != null)
            sb.append(",").append(loc.provinceValue);
        if (loc.regionValue != null)
            sb.append(",").append(loc.regionValue);
        return sb.toString();
    }
}
