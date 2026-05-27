package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.*;

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
        SearchSettingsManager.SavedSettings s = new SearchSettingsManager.SavedSettings();
        s.seek = "1";
        s.wanted = "2";
        s.ageMin = "20";
        s.ageMax = "30";
        s.fantasyIds = Arrays.asList(10, 20);

        Request request = SearchApiUtils.buildSimpleSearchRequest(s, 1, "test-cookie");

        assertEquals(ApiConstants.BASE_URL + "/ct/searchResults/profile", request.url().toString());
        assertEquals("POST", request.method());
        assertEquals("test-cookie", request.header("Cookie"));
        assertEquals(ApiConstants.BASE_URL + "/ct/search/profile", request.header("Referer"));

        FormBody body = (FormBody) request.body();
        assertNotNull(body);
        assertEquals("1", getFormValue(body, "InitiateSearch"));
        assertEquals("1", getFormValue(body, "seek"));
        assertEquals("2", getFormValue(body, "wanted"));
        assertEquals("20", getFormValue(body, "VIPAgeD"));
        assertEquals("30", getFormValue(body, "VIPAgeF"));
        assertEquals("10,20", getFormValue(body, "VIPFantasme"));
    }

    @Test
    public void testBuildSimpleSearchRequest_Page2() {
        SearchSettingsManager.SavedSettings s = new SearchSettingsManager.SavedSettings();
        Request request = SearchApiUtils.buildSimpleSearchRequest(s, 2, "test-cookie");

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

        FormBody body = (FormBody) request.body();
        assertNotNull(body);

        // Multi-value check
        int seekCount = 0;
        for (int i = 0; i < body.size(); i++) {
            if ("seek".equals(body.name(i)))
                seekCount++;
        }
        assertEquals(2, seekCount);

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
        FormBody body = (FormBody) request.body();

        // Final Location string should be "CA,QC-FR,75,1"
        assertEquals("CA,QC-FR,75,1", getFormValue(body, "Location"));
        // multRegions should be the last one: "FR,75,1"
        assertEquals("FR,75,1", getFormValue(body, "multRegions"));
    }

    private String getFormValue(FormBody body, String name) {
        for (int i = 0; i < body.size(); i++) {
            if (body.name(i).equals(name)) {
                return body.value(i);
            }
        }
        return null;
    }
}
