package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.*;

import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.util.*;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okio.Buffer;

public class SearchApiUtilsTest {

    @Test
    public void testBuildSimpleSearchRequest_Page1() throws IOException {
        Request request = SearchApiUtils.buildSimpleSearchRequest(
                "1", "2", "3", "pseudo", "20", "30",
                true, true, false, false, "CA,QC", "1", 1,
                "test-cookie", "test-suid");

        assertEquals(ApiConstants.BASE_URL + "/ct/searchResults/profile", request.url().toString());
        assertEquals("POST", request.method());
        assertEquals("test-cookie", request.header("Cookie"));
        assertEquals("test-suid", request.header("x-csrftoken"));
        assertEquals(ApiConstants.BASE_URL + "/ct/search/0", request.header("Referer"));

        okhttp3.RequestBody body = request.body();
        assertNotNull(body);
        assertEquals("1", getFormValue(body, "InitiateSearch"));
        assertEquals("1", getFormValue(body, "seek"));
        assertEquals("2", getFormValue(body, "wanted"));
        assertEquals("pseudo", getFormValue(body, "Pseudonyme"));
        assertEquals("1", getFormValue(body, "VIPEnLigne"));
        assertEquals("1", getFormValue(body, "VIPPhotos"));
        assertEquals("CA", getFormValue(body, "cboPays"));
        assertEquals("QC", getFormValue(body, "cboProvince"));
        assertEquals("QC", getFormValue(body, "hidCboProvince"));
        assertEquals("20", getFormValue(body, "VIPAgeD"));
        assertEquals("30", getFormValue(body, "VIPAgeF"));
    }

    @Test
    public void testBuildSimpleSearchRequest_Page2() {
        Request request = SearchApiUtils.buildSimpleSearchRequest(
                "1", "2", "3", "pseudo", "20", "30",
                true, true, false, false, "CA,QC", "1", 2,
                "test-cookie", "test-suid");

        HttpUrl url = request.url();
        assertEquals("0", url.queryParameter("InitiateSearch"));
        assertEquals("2", url.queryParameter("page"));
        assertEquals("GET", request.method());
        assertEquals(ApiConstants.BASE_URL + "/ct/searchResults/profile", request.header("Referer"));
    }

    @Test
    public void testBuildDetailedSearchRequest_Complex() throws IOException {
        SearchSettingsManager.SavedSettings s = new SearchSettingsManager.SavedSettings();
        s.seekDet = Arrays.asList("1", "2");
        s.ageMinDet = "25";
        s.webcamDet = true;

        SearchSettingsManager.SavedSettings.SavedLocation loc = new SearchSettingsManager.SavedSettings.SavedLocation();
        loc.countryValue = "CA";
        loc.provinceValue = "QC";
        s.selectedLocationsDet.add(loc);

        Request request = SearchApiUtils.buildDetailedSearchRequest(s, 1, "test-cookie", "test-suid", "2", "ethnic-99",
                "w1", "w2", "h1", "h2");

        assertEquals(ApiConstants.BASE_URL + "/ct/searchResults/profile", request.url().toString());
        assertEquals("POST", request.method());
        assertEquals("test-suid", request.header("x-csrftoken"));

        okhttp3.RequestBody body = request.body();
        assertNotNull(body);

        assertEquals(2, countFormKey(body, "seek"));
        assertEquals("25", getFormValue(body, "VIPAgeD"));
        assertEquals("YES", getFormValue(body, "VIPWebcam"));
        assertEquals("2", getFormValue(body, "orderBy"));
        assertEquals("ethnic-99", getFormValue(body, "VIPOrigine"));
        assertEquals("CA,QC", getFormValue(body, "Location"));
        assertEquals("w1", getFormValue(body, "VIPPoidsD"));
        assertEquals("h2", getFormValue(body, "VIPGrandeurF"));
    }

    @Test
    public void testBuildDetailedSearchRequest_MultiLocation() throws IOException {
        SearchSettingsManager.SavedSettings s = new SearchSettingsManager.SavedSettings();

        SearchSettingsManager.SavedSettings.SavedLocation loc1 = new SearchSettingsManager.SavedSettings.SavedLocation();
        loc1.countryValue = "CA";
        loc1.provinceValue = "QC";
        s.selectedLocationsDet.add(loc1);

        SearchSettingsManager.SavedSettings.SavedLocation loc2 = new SearchSettingsManager.SavedSettings.SavedLocation();
        loc2.countryValue = "FR";
        loc2.provinceValue = "75";
        loc2.regionValue = "1";
        s.selectedLocationsDet.add(loc2);

        Request request = SearchApiUtils.buildDetailedSearchRequest(s, 1, "cookie", "suid", "1", "", "", "", "", "");
        okhttp3.RequestBody body = request.body();
        assertNotNull(body);

        // Final Location string should be "CA,QC-FR,75,1"
        assertEquals("CA,QC-FR,75,1", getFormValue(body, "Location"));
        // multRegions should be the last one: "FR,75,1"
        assertEquals("FR,75,1", getFormValue(body, "multRegions"));
    }

    private String getFormValue(okhttp3.RequestBody body, String name) throws IOException {
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        String formStr = buffer.readUtf8();
        for (String pair : formStr.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length > 0 && parts[0].equals(name)) {
                return parts.length > 1 ? java.net.URLDecoder.decode(parts[1], "ISO-8859-1") : "";
            }
        }
        return null;
    }

    private int countFormKey(okhttp3.RequestBody body, String name) throws IOException {
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        String formStr = buffer.readUtf8();
        int count = 0;
        for (String pair : formStr.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length > 0 && parts[0].equals(name)) {
                count++;
            }
        }
        return count;
    }
}
