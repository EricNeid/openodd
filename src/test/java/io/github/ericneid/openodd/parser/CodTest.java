// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.value.ValueKind;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the COD/OD table readers. */
class CodTest {

	private static ConceptIndex weatherIndex() {
		var doc = YamlParser.parse(TestUtils.WEATHER_TAXONOMY_YAML);
		return new ConceptIndex(doc.taxonomy);
	}

	@Test
	void parseCODCSV() {
		// arrange
		var csvText = String.join("\n",
				"TEMPORAL_EXTENT,SPATIAL_EXTENT,rainfall_rate;mm/h,rain_level,wind_speed;km/h,is_night,road_state,ego_width;m,lateral_distance;m",
				"2024-03-01T10:00:00,zone_a,5,moderate_rain,36,false,wet,2,1.5",
				"2024-03-01T11:00:00,zone_a,10,heavy_rain,95,true,icy,2,2.5",
				"2024-03-01T12:00:00,zone_b,unknown,unknown,20,false,dry,2,0.8",
				""); // trailing newline
		// action
		var cod = Cod.parseCsv(new StringReader(csvText));
		// verify
		assertEquals(9, cod.columns.size());
		assertTrue(cod.columns.get(0).temporal && cod.columns.get(1).spatial,
				"extent columns not recognized");
		assertTrue(cod.columns.get(2).name.equals("rainfall_rate") && cod.columns.get(2).unit.equals("mm/h"),
				"rainfall column: " + cod.columns.get(2).name + ";" + cod.columns.get(2).unit);
		assertEquals(3, cod.records.size());
		var first = cod.records.get(0);
		assertTrue(first.temporal().equals("2024-03-01T10:00:00") && first.spatial().equals("zone_a"),
				"extents: " + first.temporal() + " " + first.spatial());
		var v = first.values().get("rainfall_rate");
		assertTrue(v.kind() == ValueKind.NUMBER && v.num() == 5 && v.unit().equals("mm/h")
				&& v.unitType().equals("precipitation_rate"), "rainfall value: " + v);
		v = first.values().get("rain_level");
		assertTrue(v.kind() == ValueKind.CATEGORICAL && v.str().equals("moderate_rain"),
				"rain level value: " + v);
		v = first.values().get("is_night");
		assertTrue(v.kind() == ValueKind.BOOL && !v.bool(), "is_night value: " + v);
		v = first.values().get("ego_width");
		assertTrue(v.kind() == ValueKind.NUMBER && v.num() == 2 && v.unitType().equals("length"),
				"ego width value: " + v);
		// Unknown cells are missing values.
		var third = cod.records.get(2);
		var miss = third.values().get("rainfall_rate");
		assertTrue(miss != null && miss.kind() == ValueKind.MISSING, "unknown cell: " + miss);
	}

	@Test
	void parseCODCSVEmptyAndErrors() {
		assertThrows(OpenOddException.class, () -> Cod.parseCsv(new StringReader("")),
				"expected an error for empty input");
		// Short rows are tolerated.
		Cod.parseCsv(new StringReader("a,b\n1\n"));
		assertThrows(OpenOddException.class, () -> Cod.parseCsv(new StringReader(",b\n1,2\n")),
				"expected an error for an empty column header");
	}

	@Test
	void codResolve() {
		// arrange
		var idx = weatherIndex();
		{
			// arrange
			var csvText = "TEMPORAL_EXTENT,rainfall_rate;mm/h,rain_level,RAIN_LEVEL,wind_speed;km/h\n"
					+ "t0,5,moderate_rain,moderate_rain,36";
			var cod = Cod.parseCsv(new StringReader(csvText));
			// action
			cod.resolve(idx);
			// verify
			var rec = cod.records.get(0);
			var v = rec.values().get("environmental_conditions.weather.rainfall_rate");
			assertTrue(v != null && v.num() == 5, "resolved rainfall: " + v);
			// Column names resolve case-insensitively.
			assertNotNull(rec.values().get("environmental_conditions.weather.rain_level"),
					"case-insensitive column not resolved: " + rec.values());
		}
		{
			// arrange
			var cod = Cod.parseCsv(new StringReader("no_such_concept\n1\n"));
			// action
			var err = assertThrows(OpenOddException.class, () -> cod.resolve(idx));
			// verify
			assertTrue(err.getMessage().contains("no_such_concept"),
					"expected a resolution error, got " + err.getMessage());
		}
	}

	@Test
	void parseCODYAMLAndResolveRecords() {
		// arrange
		var idx = weatherIndex();
		var src = """
                COD:
                  - TEMPORAL_EXTENT: t0
                    rainfall_rate;mm/h: 5
                    rain_level: moderate_rain
                OD:
                  - TEMPORAL_EXTENT: t1
                    rainfall_rate;mm/h: 10
                    rain_level: heavy_rain
                """;
		// action
		var records = Cod.parseYaml(src);
		Cod.resolveRecords(records, idx);
		// verify
		assertEquals(2, records.size());
		assertNotNull(records.get(0).values().get("environmental_conditions.weather.rainfall_rate"),
				"COD record not canonicalized");
		assertNotNull(records.get(1).values().get("environmental_conditions.weather.rainfall_rate"),
				"OD record not canonicalized");
	}

	@Test
	void codMerge() {
		// arrange
		var a = Cod.parseCsv(new StringReader("x;m\n1\n"));
		var b = Cod.parseCsv(new StringReader("y;m\n2\n"));
		// action
		a.merge(b);
		// verify
		assertTrue(a.columns.size() == 2 && a.records.size() == 2,
				"merge: " + a.columns.size() + " columns, " + a.records.size() + " records");
	}

	private record MarkerCase(String marker, String unit, String unitType) {
	}

	@Test
	void interpretMarker() {
		// arrange
		var tests = List.of(
				new MarkerCase("mm/h", "mm/h", "precipitation_rate"),
				new MarkerCase("km/h", "km/h", "velocity"),
				new MarkerCase("categorical_literal", "", ""),
				new MarkerCase("boolean", "", ""),
				new MarkerCase("", "", ""),
				new MarkerCase("furlongs", "furlongs", ""));
		for (var tt : tests) {
			// action
			var got = Cod.interpretMarker(tt.marker());
			// verify
			assertTrue(got[0].equals(tt.unit()) && got[1].equals(tt.unitType()),
					"interpretMarker(%s) = %s, %s; want %s, %s"
							.formatted(tt.marker(), got[0], got[1], tt.unit(), tt.unitType()));
		}
	}
}
