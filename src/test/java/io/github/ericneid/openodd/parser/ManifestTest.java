// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.OpenOddException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the COD/OD manifest reader. */
class ManifestTest {

	@Test
	void parseManifestCSV() {
		// arrange
		var src = String.join("\n",
				"FIELD_ID,FILE_NAME,TYPE,COLUMN,TAXONOMY_ID,UNIT,COMMENT",
				"1,taxonomy.yaml,taxonomy,,,,",
				"2,cod.csv,cod,rainfall_rate,environmental_conditions.weather.rainfall_rate,mm/h,Rainfall rate",
				"3,cod.csv,od,wind_speed,environmental_conditions.weather.wind.wind_speed,km/h,Wind speed");
		// action
		var m = Manifest.parseCsv(new StringReader(src));
		// verify
		assertEquals(3, m.entries.size());
		var e = m.entries.get(1);
		assertTrue(e.fieldId().equals("2") && e.fileName().equals("cod.csv") && e.type().equals("cod")
				&& e.taxonomyId().equals("environmental_conditions.weather.rainfall_rate")
				&& e.unit().equals("mm/h"), "entry: " + e);
		assertEquals(java.util.List.of("taxonomy.yaml"), m.taxonomyFiles());
		assertEquals(java.util.List.of("cod.csv"), m.dataFiles());
	}

	@Test
	void parseManifestCSVErrors() {
		assertThrows(OpenOddException.class, () -> Manifest.parseCsv(new StringReader("")),
				"expected an error for an empty manifest");
		assertThrows(OpenOddException.class,
				() -> Manifest.parseCsv(new StringReader("FIELD_ID,FILE_NAME,TYPE,COLUMN,TAXONOMY_ID,UNIT,WRONG\n")),
				"expected an error for a wrong header");
		assertThrows(OpenOddException.class,
				() -> Manifest.parseCsv(new StringReader(
						"FIELD_ID,FILE_NAME,TYPE,COLUMN,TAXONOMY_ID,UNIT,COMMENT\n" + "1,cod.csv,cod,x")),
				"expected an error for a short row");
	}

	@Test
	void loadCODWithManifest() {
		// arrange
		var manifest = Manifest.parseCsv(
				new StringReader(TestUtils.resourceText("testdata/weather/manifest.csv")));
		// action
		var cod = Manifest.loadCodWithManifest(manifest, new OsResolver(),
				TestUtils.resourcePath("testdata/weather").toString());
		// verify
		assertEquals(3, cod.records.size());
		// Manifest-bound columns carry full taxonomy IDs and their units.
		var rec = cod.records.get(0);
		var v = rec.values().get("environmental_conditions.weather.rainfall_rate");
		assertTrue(v != null && v.num() == 5 && v.unit().equals("mm/h"), "manifest column: " + v);
		v = rec.values().get("environmental_conditions.weather.wind.wind_speed");
		assertTrue(v != null && v.num() == 36 && v.unit().equals("km/h"), "manifest wind column: " + v);
		// Columns without manifest entries keep their header names.
		assertNotNull(rec.values().get("rain_level"), "unmapped column lost: " + rec.values());
		// A missing data file is an error.
		assertThrows(OpenOddException.class,
				() -> Manifest.loadCodWithManifest(manifest, new MapResolver(Map.of()),
						TestUtils.resourcePath("testdata/weather").toString()),
				"expected an error for a missing data file");
	}
}
