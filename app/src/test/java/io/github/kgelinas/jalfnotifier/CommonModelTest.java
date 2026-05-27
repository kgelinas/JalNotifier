package io.github.kgelinas.jalfnotifier;

import static org.junit.Assert.*;

import org.junit.Test;

public class CommonModelTest {

    @Test
    public void testRegionDet() {
        MainActivity.RegionDet region = new MainActivity.RegionDet("Quebec", "/rest/regions/123");
        assertEquals("Quebec", region.name);
        assertEquals("/rest/regions/123", region.link);
        assertEquals("123", region.id);
        assertEquals("Quebec", region.toString());
    }

    @Test
    public void testWeightOption() {
        MainActivity.WeightOption weight = new MainActivity.WeightOption(1, 70, 154);
        assertEquals(1, weight.id);
        assertEquals(70, weight.kg);
        assertEquals(154, weight.lbs);
    }

    @Test
    public void testHeightOption() {
        MainActivity.HeightOption height = new MainActivity.HeightOption(5, "180 cm", "5'11\"");
        assertEquals(5, height.id);
        assertEquals("180 cm", height.metric);
        assertEquals("5'11\"", height.imperial);
    }

    @Test
    public void testSelectedLocation() {
        MainActivity.RegionDet c = new MainActivity.RegionDet("Canada", "/1");
        MainActivity.RegionDet p = new MainActivity.RegionDet("Quebec", "/1/2");
        MainActivity.RegionDet r = new MainActivity.RegionDet("Montreal", "/1/2/3");

        MainActivity.SelectedLocation loc = new MainActivity.SelectedLocation(c, p, r);
        assertEquals("1,2,3", loc.getFullId());
        assertEquals("Canada > Quebec > Montreal", loc.toString());

        MainActivity.SelectedLocation partial = new MainActivity.SelectedLocation(c, null, null);
        assertEquals("1", partial.getFullId());
        assertEquals("Canada", partial.toString());
    }

    @Test
    public void testFantasyModels() {
        MainActivity.Fantasy f = new MainActivity.Fantasy(10, "Latex");
        assertEquals(10, f.id);
        assertEquals("Latex", f.name);

        MainActivity.FantasyCategory fc = new MainActivity.FantasyCategory("Costumes");
        assertEquals("Costumes", fc.name);
        fc.fantasies.add(f);
        assertEquals(1, fc.fantasies.size());
    }

    @Test
    public void testSearchOption() {
        SearchOption opt = new SearchOption("val1", "Label 1");
        assertEquals("val1", opt.value);
        assertEquals("Label 1", opt.label);
        assertEquals("Label 1", opt.toString());
    }
}
