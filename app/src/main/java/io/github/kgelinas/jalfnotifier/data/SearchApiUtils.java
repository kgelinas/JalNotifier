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
 * Matches the exact form field names from /ct/search/0 (search_detl_flg=0)
 * and /ct/search_det/1 (search_detl_flg=1).
 */
public class SearchApiUtils {

    private SearchApiUtils() {
        // Utility class
    }

    /**
     * Builds an okhttp3.Request for a Detailed Search (/ct/search_det/1).
     *
     * @param s          the detailed search settings
     * @param page       the page number (1-indexed)
     * @param fullCookie the session cookie
     * @param suid       the CSRF token (x-csrftoken)
     * @param orderBy    the order by value ("1", "2", "3")
     * @param ethnicTag  the selected ethnic group value
     * @param weightD    start weight ID (optional, defaults to "1")
     * @param weightF    end weight ID (optional, defaults to "35")
     * @param heightD    start height ID (optional, defaults to "1")
     * @param heightF    end height ID (optional, defaults to "37")
     * @return the constructed Request
     */
    public static Request buildDetailedSearchRequest(SearchSettingsManager.SavedSettings s, int page, String fullCookie,
            String suid, String orderBy, @Nullable String ethnicTag,
            @Nullable String weightD, @Nullable String weightF,
            @Nullable String heightD, @Nullable String heightF) {

        List<Param> params = new ArrayList<>();
        params.add(new Param("InitiateSearch", page == 1 ? "1" : "0"));
        params.add(new Param("search_detl_flg", "1"));
        params.add(new Param("tab_id", "1"));
        params.add(new Param("hiddenSId", ""));
        params.add(new Param("mod_flg", ""));

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
            String cboPays = (last.countryValue != null && !last.countryValue.isEmpty()) ? last.countryValue : "0";
            String cboProv = (last.provinceValue != null && !last.provinceValue.isEmpty()) ? last.provinceValue : "0";
            String cboReg  = (last.regionValue != null && !last.regionValue.isEmpty()) ? last.regionValue : "0";
            params.add(new Param("cboPays", cboPays));
            params.add(new Param("cboProvince", cboProv));
            params.add(new Param("cboRegion", cboReg));
            params.add(new Param("hidCboProvince", cboProv));
            params.add(new Param("hidCboRegion", cboReg));
            params.add(new Param("multRegions", getFullLocationId(last)));
        } else {
            params.add(new Param("Location", ""));
            params.add(new Param("cboPays", "0"));
        }

        if (s.fantasyIdsDet != null && !s.fantasyIdsDet.isEmpty()) {
            StringBuilder fantasies = new StringBuilder();
            for (int i = 0; i < s.fantasyIdsDet.size(); i++) {
                if (i > 0)
                    fantasies.append(",");
                fantasies.append(s.fantasyIdsDet.get(i));
            }
            params.add(new Param("VIPFantasme", fantasies.toString()));
        } else {
            params.add(new Param("VIPFantasme", ""));
        }

        if (ethnicTag != null && !ethnicTag.equals("0") && !ethnicTag.isEmpty()) {
            params.add(new Param("VIPOrigine", ethnicTag));
        }

        // Common checkboxes & username (sent in POST body in case backend accepts them on detailed search)
        if (s.onlineSimple)   params.add(new Param("VIPEnLigne", "1"));
        if (s.photoSimple)    params.add(new Param("VIPPhotos", "1"));
        if (s.myRegionSimple) params.add(new Param("my_region", "1"));
        if (s.pseudoSimple != null && !s.pseudoSimple.trim().isEmpty()) {
            params.add(new Param("Pseudonyme", s.pseudoSimple.trim()));
        }

        if (s.speedMeetingDet) params.add(new Param("VIPSpeedMeeting", "1"));
        if (s.webcamDet)       params.add(new Param("VIPWebcam", "YES"));
        params.add(new Param("h_pos_y", "500"));
        params.add(new Param("h_pos_x", "500"));
        params.add(new Param("btnPager", "1"));
        params.add(new Param("pager_on", "1"));

        String ageD = (s.ageMinDet == null || s.ageMinDet.isEmpty()) ? "18" : s.ageMinDet;
        String ageF = (s.ageMaxDet == null || s.ageMaxDet.isEmpty()) ? "99" : s.ageMaxDet;
        params.add(new Param("VIPAgeD", ageD));
        params.add(new Param("VIPAgeF", ageF));

        params.add(new Param("VIPPoidsD", (weightD != null && !weightD.isEmpty()) ? weightD : "1"));
        params.add(new Param("VIPPoidsF", (weightF != null && !weightF.isEmpty()) ? weightF : "35"));
        params.add(new Param("VIPGrandeurD", (heightD != null && !heightD.isEmpty()) ? heightD : "1"));
        params.add(new Param("VIPGrandeurF", (heightF != null && !heightF.isEmpty()) ? heightF : "37"));

        params.add(new Param("orderBy", orderBy != null ? orderBy : "1"));

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
            requestBuilder.addHeader("Referer", ApiConstants.BASE_URL + "/ct/search_det/1");
        }

        return requestBuilder.build();
    }

    /**
     * Builds an okhttp3.Request for a Simple Search (JALF /ct/search/0).
     * Uses exact form field names from search.html:
     * - Pseudonyme (not pseudo)
     * - VIPEnLigne (not online)
     * - VIPPhotos (not a_photo)
     * - my_region (not maRegion)
     * - cboPays, cboProvince, cboRegion, hidCboProvince, hidCboRegion
     */
    public static Request buildSimpleSearchRequest(
            String seek, String wanted, String orient,
            String pseudo, String ageMin, String ageMax,
            boolean online, boolean hasPhoto, boolean myRegion, boolean desiresToday,
            String location, String orderBy, int page,
            String fullCookie, String suid) {
        return buildSimpleSearchRequest(
                seek, wanted, orient, pseudo, ageMin, ageMax,
                online, hasPhoto, myRegion, desiresToday, false,
                location, null, null, null, orderBy, page, fullCookie, suid);
    }

    public static Request buildSimpleSearchRequest(
            String seek, String wanted, String orient,
            String pseudo, String ageMin, String ageMax,
            boolean online, boolean hasPhoto, boolean myRegion, boolean desiresToday, boolean hasWebcam,
            String location, @Nullable String status, @Nullable String relCher, @Nullable String fantasiesCsv,
            String orderBy, int page,
            String fullCookie, String suid) {

        List<Param> params = new ArrayList<>();
        params.add(new Param("InitiateSearch", page == 1 ? "1" : "0"));
        params.add(new Param("search_detl_flg", "0"));
        params.add(new Param("tab_id", "0"));
        params.add(new Param("hiddenSId", ""));
        params.add(new Param("mod_flg", ""));

        params.add(new Param("seek", seek != null ? seek : ""));
        params.add(new Param("wanted", wanted != null ? wanted : ""));
        params.add(new Param("orient", orient != null ? orient : ""));
        params.add(new Param("status", status != null ? status : ""));
        params.add(new Param("relCher", relCher != null ? relCher : ""));
        params.add(new Param("VIPFantasme", fantasiesCsv != null ? fantasiesCsv : ""));

        String cleanPseudo = (pseudo != null) ? pseudo.trim() : "";
        params.add(new Param("Pseudonyme", cleanPseudo));
        if (!cleanPseudo.isEmpty()) {
            // Legacy alias in case any endpoint variant reads 'pseudo'
            params.add(new Param("pseudo", cleanPseudo));
        }

        params.add(new Param("VIPAgeD", ageMin == null || ageMin.isEmpty() ? "18" : ageMin));
        params.add(new Param("VIPAgeF", ageMax == null || ageMax.isEmpty() ? "99" : ageMax));

        if (online) {
            params.add(new Param("VIPEnLigne", "1"));
            params.add(new Param("online", "1"));
        }
        if (hasPhoto) {
            params.add(new Param("VIPPhotos", "1"));
            params.add(new Param("a_photo", "1"));
        }
        if (myRegion) {
            params.add(new Param("my_region", "1"));
            params.add(new Param("maRegion", "1"));
        }
        if (desiresToday) {
            params.add(new Param("VIPSpeedMeeting", "1"));
        }
        if (hasWebcam) {
            params.add(new Param("VIPWebcam", "YES"));
            params.add(new Param("VIPVideo", "1"));
        }

        // Parse "country[,province[,region]]" into cboPays / cboProvince / cboRegion + hidCboProvince / hidCboRegion
        String cboPays = "0";
        String cboProvince = "0";
        String cboRegion = "0";
        if (location != null && !location.trim().isEmpty()) {
            String[] parts = location.trim().split(",");
            if (parts.length >= 1 && !parts[0].isEmpty() && !"null".equals(parts[0])) {
                cboPays = parts[0];
            }
            if (parts.length >= 2 && !parts[1].isEmpty() && !"null".equals(parts[1])) {
                cboProvince = parts[1];
            }
            if (parts.length >= 3 && !parts[2].isEmpty() && !"null".equals(parts[2])) {
                cboRegion = parts[2];
            }
            params.add(new Param("Location", location.trim()));
        }
        params.add(new Param("cboPays", cboPays));
        params.add(new Param("cboProvince", cboProvince));
        params.add(new Param("cboRegion", cboRegion));
        params.add(new Param("hidCboProvince", cboProvince));
        params.add(new Param("hidCboRegion", cboRegion));

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
        if (loc.countryValue != null && !loc.countryValue.isEmpty())
            sb.append(loc.countryValue);
        if (loc.provinceValue != null && !loc.provinceValue.isEmpty() && !"0".equals(loc.provinceValue))
            sb.append(",").append(loc.provinceValue);
        if (loc.regionValue != null && !loc.regionValue.isEmpty() && !"0".equals(loc.regionValue))
            sb.append(",").append(loc.regionValue);
        return sb.toString();
    }
}
