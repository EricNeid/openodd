// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the model loader with imports. */
class LoaderTest {

	@Test
	void loadFileWithImports() {
		// action
		var m = Loader.loadFile(TestUtils.resourcePath("testdata/weather/odd.yaml").toString());
		// verify
		Validator.validate(m);
		// Taxonomy merged from the imported file.
		for (var id : List.of(
				"environmental_conditions.weather.rainfall_rate",
				"environmental_conditions.weather.rain_level",
				"environmental_conditions.wind.wind_trajectory",
				"ego_vehicle.ego_width",
				"obstacle.lateral_distance",
				"radial_vector.angle")) {
			assertNotNull(m.taxonomy.concepts.get(id), "concept \"" + id + "\" missing");
		}
		// All three files contributed modules.
		for (var id : List.of("example_module_1", "example_module_2", "night_module",
				"clearance_module", "weather_watcher")) {
			assertNotNull(m.modules.get(id), "module \"" + id + "\" missing");
		}
		// Root computation: ODD modules are roots, referenced MODULES modules
		// are not, unreferenced MODULES modules are.
		assertEquals(List.of("example_module_1", "example_module_2", "night_module", "weather_watcher"),
				m.rootIds);
		assertTrue(!m.modules.get("clearance_module").root, "referenced clearance_module must not be a root");
		// Labels merged across files.
		assertTrue(m.labels.get("odd_weather").size() == 2, "label odd_weather: " + m.labels);
		// Import chain recorded.
		assertTrue(m.imports.size() == 3, "imports: " + m.imports);
	}

	@Test
	void loaderWithMapResolver() {
		// arrange
		var files = new MapResolver(Map.of(
				"base.yaml", "TAXONOMY:\n  a: boolean\n",
				"mid.yaml", "IMPORT: base.yaml\nMODULES:\n  helper:\n    INCLUDE_AND:\n      a: true\n",
				"top.yaml", "IMPORT: [mid.yaml, base.yaml]\nODD:\n  root:\n    INCLUDE_AND:\n      helper: true\n"));
		// action
		var m = new Loader(files).load("top.yaml");
		// verify
		assertTrue(m.taxonomy.concepts.get("a") != null && m.modules.get("helper") != null
				&& m.modules.get("root") != null, "merge incomplete");
		// The same file imported twice is loaded once; concepts stay unique.
		assertTrue(m.taxonomy.concepts.size() == 1, "concepts: " + m.taxonomy.concepts);
		assertEquals(List.of("root"), m.rootIds);
	}

	@Test
	void loaderCycle() {
		// arrange
		var files = new MapResolver(Map.of(
				"a.yaml", "IMPORT: b.yaml\nODD:\n  m:\n    INCLUDE_AND: {x: true}\n",
				"b.yaml", "IMPORT: a.yaml\n"));
		// action
		var err = assertThrows(OpenOddException.class, () -> new Loader(files).load("a.yaml"));
		// verify
		assertTrue(err.getMessage().contains("cyclic"), "expected a cycle error, got " + err.getMessage());
	}

	@Test
	void loaderDuplicates() {
		// arrange
		var files = new MapResolver(Map.of(
				"a.yaml", "TAXONOMY:\n  shared: boolean\nODD:\n  m:\n    INCLUDE_AND: {x: true}\n",
				"b.yaml", "TAXONOMY:\n  shared: boolean\nODD:\n  n:\n    INCLUDE_AND: {x: true}\n"));
		var l = new Loader(files);
		l.load("a.yaml");
		// action
		var err = assertThrows(OpenOddException.class, () -> l.load("b.yaml"));
		// verify
		assertTrue(err.getMessage().contains("duplicate concept"),
				"expected a duplicate concept error, got " + err.getMessage());
	}

	@Test
	void loadFiles() {
		// action
		var m = Loader.loadFiles(
				TestUtils.resourcePath("testdata/weather/taxonomy.yaml").toString(),
				TestUtils.resourcePath("testdata/weather/modules.yaml").toString());
		// verify
		assertNotNull(m.taxonomy.concepts.get("environmental_conditions.weather.rainfall_rate"),
				"taxonomy missing");
		assertNotNull(m.modules.get("night_module"), "module missing");
		// Both the taxonomy file and the module file loaded standalone:
		// night_module is unreferenced and becomes a root.
		assertTrue(m.modules.get("night_module").root, "night_module should be a root");
	}

	@Test
	void loaderMissingFile() {
		// action & verify
		assertThrows(OpenOddException.class,
				() -> Loader.loadFile(TestUtils.resourcePath("testdata") + "/no-such-file.yaml"),
				"expected an error for a missing file");
	}

	@Test
	void loaderInvalidImport() {
		// arrange
		var files = new MapResolver(Map.of("top.yaml", "IMPORT: missing.yaml\n"));
		// action & verify
		assertThrows(OpenOddException.class, () -> new Loader(files).load("top.yaml"),
				"expected an error for an unresolvable import");
	}
}
