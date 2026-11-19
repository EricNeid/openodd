// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.expr.EqualExpr;
import io.github.ericneid.openodd.expr.RangeExpr;
import io.github.ericneid.openodd.model.Concept;
import io.github.ericneid.openodd.model.ConceptKind;
import io.github.ericneid.openodd.model.Document;
import io.github.ericneid.openodd.model.SectionOperator;
import io.github.ericneid.openodd.value.ValueKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the YAML front end. */
class YamlTest {

	private static Document mustParseYAML(String src) {
		return YamlParser.parse(src);
	}

	@Test
	void parseYAMLTaxonomy() {
		// arrange
		var src = """
                TAXONOMY:
                  environmental_conditions:
                    weather:
                      rainfall_rate: float precipitation_rate
                      road_state: [dry, wet, icy, snowy]
                      rain_level:
                        no_rain: {rainfall_rate: "[0 .. 0.1] mm/h"}
                        light_rain: {rainfall_rate: "[0.1 .. 2.5] mm/h"}
                    time_of_day:
                      is_night: boolean
                  ego_vehicle:
                    ego_width: float length
                    $min_lateral_distance: float length
                  types:
                    radial_vector:
                      angle: float angle
                      speed: float velocity
                  wind_trajectory: radial_vector
                """;
		// action
		var doc = mustParseYAML(src);
		// verify
		java.util.function.Function<String, Concept> mustConcept = id -> {
			var c = doc.taxonomy.concepts.get(id);
			assertNotNull(c, "concept \"" + id + "\" missing");
			return c;
		};
		// Attribute with unit type.
		var c = mustConcept.apply("environmental_conditions.weather.rainfall_rate");
		assertTrue(c.kind == ConceptKind.ATTRIBUTE && c.primType.equals("float")
				&& c.unitType.equals("precipitation_rate"), "rainfall_rate: " + c);
		// Categorical sequence form.
		c = mustConcept.apply("environmental_conditions.weather.road_state");
		assertTrue(c.kind == ConceptKind.CATEGORICAL && c.literals.size() == 4
				&& c.literals.get(0).name.equals("dry"), "road_state: " + c);
		// Categorical constraint-map form with expression constraints.
		c = mustConcept.apply("environmental_conditions.weather.rain_level");
		assertTrue(c.kind == ConceptKind.CATEGORICAL && c.literals.size() == 2, "rain_level: " + c);
		var lit = c.literal("light_rain");
		assertNotNull(lit, "light_rain: " + c);
		assertTrue(lit.constraints.size() == 1 && lit.constraints.get(0).ref().equals("rainfall_rate"),
				"light_rain: " + lit);
		var rng = assertInstanceOf(RangeExpr.class, lit.constraints.get(0).expr());
		assertTrue(rng.min().left().number() == 0.1 && rng.max().left().number() == 2.5
				&& rng.unit().equals("mm/h"), "light_rain constraint: " + rng);
		// Boolean attribute.
		c = mustConcept.apply("environmental_conditions.time_of_day.is_night");
		assertTrue(c.kind == ConceptKind.ATTRIBUTE && c.primType.equals("boolean")
				&& c.unitType.isEmpty(), "is_night: " + c);
		// Parameter.
		c = mustConcept.apply("ego_vehicle.$min_lateral_distance");
		assertTrue(c.parameter && c.name.equals("min_lateral_distance"), "parameter: " + c);
		// Record and children.
		c = mustConcept.apply("types.radial_vector");
		assertTrue(c.kind == ConceptKind.RECORD && c.children.size() == 2, "radial_vector: " + c);
		// Type reference.
		c = mustConcept.apply("wind_trajectory");
		assertTrue(c.kind == ConceptKind.TYPE_REFERENCE && c.ref.equals("radial_vector"),
				"wind_trajectory: " + c);
		// Records preserve declaration order of children.
		assertEquals("angle,speed", String.join(",", mustConcept.apply("types.radial_vector").childOrder));
	}

	@Test
	void parseYAMLModules() {
		{
			// arrange
			var src = """
                    ODD:
                      example_module_1:
                        TITLE: Example module 1
                        LABELS: [odd_weather]
                        METADATA:
                          version: "1.0"
                        INCLUDE_AND:
                          rain_level: <= moderate_rain
                          is_night: false
                          AND:
                            wind_speed: "< 20 m/s"
                          OR:
                            temperature: "[-10 .. 40] C"
                            humidity: ">= 30 %"
                        EXCLUDE_OR:
                          road_state: [icy, snowy]
                          other_module: true
                    TOD:
                      tod_module:
                        INCLUDE_AND:
                          rain_level: light_rain
                    MODULES:
                      helper:
                        ACTIVE: false
                        INCLUDE_AND:
                          is_night: true
                    """;
			// action
			var doc = mustParseYAML(src);
			// verify
			assertTrue(doc.rootIds.size() == 2 && doc.rootIds.get(0).equals("example_module_1")
					&& doc.rootIds.get(1).equals("tod_module"), "root IDs: " + doc.rootIds);
			var mod = doc.modules.get("example_module_1");
			assertTrue(mod != null && mod.root && mod.kind.equals("ODD") && mod.active, "module: " + mod);
			assertTrue(mod.title.equals("Example module 1") && mod.labels.size() == 1
					&& mod.metadata.get("version").equals("1.0"), "module metadata: " + mod);
			assertTrue(doc.labels.get("odd_weather").size() == 1, "labels: " + doc.labels);
			var inc = mod.include;
			assertTrue(inc.operator == SectionOperator.AND && inc.conditions.size() == 2
					&& inc.subsections.size() == 2, "include section: " + inc);
			// Nested subsections.
			var and = inc.subsections.get(0);
			var or = inc.subsections.get(1);
			assertTrue(and.operator == SectionOperator.AND && or.operator == SectionOperator.OR,
					"subsection operators: " + and.operator + " " + or.operator);
			assertTrue(and.conditions.size() == 1 && and.conditions.get(0).ref.equals("wind_speed"),
					"AND subsection: " + and);
			// EXCLUDE_OR.
			var exc = mod.exclude;
			assertTrue(exc.operator == SectionOperator.OR && exc.conditions.size() == 2,
					"exclude section: " + exc);
			var ref = assertInstanceOf(EqualExpr.class, exc.conditions.get(1).expr);
			assertTrue(ref.bool(), "module reference condition: " + ref);
			// TOD kind.
			assertEquals("TOD", doc.modules.get("tod_module").kind);
			// Inactive MODULES module.
			var h = doc.modules.get("helper");
			assertTrue(h != null && !h.root && !h.active && h.kind.equals("Standard"), "helper module: " + h);
		}
		{
			// action
			var doc = mustParseYAML("MODULES:\n  meta_only:\n    TITLE: Only metadata\n");
			// verify
			assertNotNull(doc.modules.get("meta_only"), "section-less module rejected");
		}
	}

	@Test
	void parseYAMLCODRecords() {
		// arrange
		var src = """
                COD:
                  - TEMPORAL_EXTENT: "2024-03-01T10:00:00"
                    SPATIAL_EXTENT: zone_a
                    rainfall_rate;mm/h: 5
                    rain_level: moderate_rain
                    wind_speed;km/h: [24 .. 26]
                    is_night: false
                    obstacle_type: [car, truck]
                """;
		// action
		var doc = mustParseYAML(src);
		// verify
		assertEquals(1, doc.cod.size());
		var rec = doc.cod.get(0);
		assertTrue(rec.temporal().equals("2024-03-01T10:00:00") && rec.spatial().equals("zone_a"),
				"extents: " + rec.temporal() + " " + rec.spatial());
		var v = rec.values().get("rainfall_rate");
		assertTrue(v.kind() == ValueKind.NUMBER && v.num() == 5 && v.unit().equals("mm/h"),
				"rainfall_rate: " + v);
		v = rec.values().get("rain_level");
		assertTrue(v.kind() == ValueKind.CATEGORICAL && v.str().equals("moderate_rain"),
				"rain_level: " + v);
		v = rec.values().get("wind_speed");
		assertTrue(v.kind() == ValueKind.RANGE && v.min() == 24 && v.max() == 26 && v.unit().equals("km/h"),
				"wind_speed range: " + v);
		v = rec.values().get("is_night");
		assertTrue(v.kind() == ValueKind.BOOL && !v.bool(), "is_night: " + v);
		v = rec.values().get("obstacle_type");
		assertTrue(v.kind() == ValueKind.LIST && v.items().size() == 2, "obstacle_type: " + v);
	}

	private record ErrorCase(String name, String src, String want) {
	}

	@Test
	void parseYAMLErrors() {
		// arrange
		var tests = java.util.List.of(
				new ErrorCase("unknown top-level", "BOGUS: 1", "unknown top-level element"),
				new ErrorCase("duplicate key", "TAXONOMY:\n  a: boolean\n  a: boolean\n", "duplicate key"),
				new ErrorCase("duplicate module",
						"ODD:\n  m:\n    INCLUDE_AND: {x: true}\nMODULES:\n  m:\n    INCLUDE_AND: {x: true}\n",
						"duplicate module"),
				new ErrorCase("duplicate literal", "TAXONOMY:\n  r:\n    a: [x, x]\n",
						"duplicate categorical literal"),
				new ErrorCase("unknown module", "MODULES: 1", "expected a YAML mapping"),
				new ErrorCase("bad type spec", "TAXONOMY:\n  a: floaty velocity fast\n",
						"invalid type specification"),
				new ErrorCase("unit on boolean", "TAXONOMY:\n  a: boolean length\n", "takes no unit type"),
				new ErrorCase("module named unknown", "ODD:\n  unknown:\n    INCLUDE_AND: {x: true}\n",
						"must not be"));
		for (var tt : tests) {
			// action
			var err = assertThrows(OpenOddException.class, () -> YamlParser.parse(tt.src()),
					tt.name());
			// verify
			assertTrue(err.getMessage().contains(tt.want()),
					"ParseYAML error = " + err.getMessage() + ", want \"" + tt.want() + "\"");
		}
	}

	@Test
	void parseYAMLEmpty() {
		// action
		var doc = YamlParser.parse("");
		// verify
		assertTrue(doc.taxonomy.concepts.isEmpty(), "expected an empty taxonomy");
	}
}
