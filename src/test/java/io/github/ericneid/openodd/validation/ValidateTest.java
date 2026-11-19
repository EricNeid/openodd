// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.validation;

import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.parser.MapResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the semantic model validation. */
class ValidateTest {

	@Test
	void validateOK() {
		// arrange
		var m = Loader.loadFile(TestUtils.resourcePath("testdata/weather/odd.yaml").toString());
		// action & verify
		Validator.validate(m);
	}

	/**
	 * Loads a single-file model and validates it, returning the first
	 * error or null.
	 */
	private static OpenOddException loadModelOrErr(String src) {
		try {
			var m = new Loader(new MapResolver(Map.of("spec.yaml", src))).load("spec.yaml");
			Validator.validate(m);
			return null;
		} catch (OpenOddException e) {
			return e;
		}
	}

	private record ErrorCase(String name, String src, String want) {
	}

	@Test
	void validateErrors() {
		// arrange
		var tests = List.of(
				new ErrorCase("unknown concept reference",
						"TAXONOMY:\n  t:\n    x: boolean\nODD:\n  m:\n    INCLUDE_AND:\n      no_such_concept: true\n",
						"unknown concept"),
				new ErrorCase("unknown type reference",
						"TAXONOMY:\n  t:\n    x: no_such_type\n",
						"references unknown type"),
				new ErrorCase("unknown unit symbol",
						"TAXONOMY:\n  t:\n    speed: float velocity\nODD:\n  m:\n    INCLUDE_AND:\n      speed: \"< 5 furlongs\"\n",
						"not registered"),
				new ErrorCase("unit type mismatch",
						"TAXONOMY:\n  t:\n    speed: float velocity\n    rain: float precipitation_rate\nODD:\n  m:\n    INCLUDE_AND:\n      speed: \"< 5 mm/h\"\n",
						"belongs to unit type"),
				new ErrorCase("unit type on boolean",
						"TAXONOMY:\n  t:\n    x: boolean velocity\n",
						"takes no unit type"),
				new ErrorCase("numeric equality on categorical",
						"TAXONOMY:\n  t:\n    level: [a, b]\nODD:\n  m:\n    INCLUDE_AND:\n      level: 3\n",
						"numeric equality needs a numeric concept"),
				new ErrorCase("unknown list literal",
						"TAXONOMY:\n  t:\n    level: [a, b]\nODD:\n  m:\n    INCLUDE_AND:\n      level: [a, z]\n",
						"is not a literal"),
				new ErrorCase("label duplicates module",
						"ODD:\n  m:\n    LABELS: [m]\n    INCLUDE_AND: {x: true}\n",
						"duplicates a module ID"),
				new ErrorCase("label collides with concept",
						"TAXONOMY:\n  t:\n    speed: float velocity\nODD:\n  m:\n    LABELS: [speed]\n    INCLUDE_AND: {x: true}\n",
						"collides with a taxonomy concept"),
				new ErrorCase("module collides with concept",
						"TAXONOMY:\n  t:\n    speed: float velocity\nODD:\n  speed:\n    INCLUDE_AND: {x: true}\n",
						"collides with a taxonomy concept"),
				new ErrorCase("module reference with expression",
						"MODULES:\n  a:\n    TITLE: Helper\nODD:\n  m:\n    INCLUDE_AND:\n      a: \"< 5 m/s\"\n",
						"take true or false"),
				new ErrorCase("module cycle",
						"MODULES:\n  a:\n    INCLUDE_AND:\n      b: true\n  b:\n    INCLUDE_AND:\n      a: true\n",
						"cyclic"),
				new ErrorCase("unknown literal constraint reference",
						"TAXONOMY:\n  t:\n    rate: float precipitation_rate\n    level:\n      a: {no_such: \"< 5 mm/h\"}\n",
						"unknown concept"),
				new ErrorCase("parameterized expression with unit",
						"TAXONOMY:\n  t:\n    w: float length\n    d: float length\nODD:\n  m:\n    INCLUDE_AND:\n      d: \"> 0.5*w m\"\n",
						"take no units"),
				new ErrorCase("non numeric term factor",
						"TAXONOMY:\n  t:\n    flag: boolean\n    d: float length\nODD:\n  m:\n    INCLUDE_AND:\n      d: \"> 0.5*flag\"\n",
						"not a numeric attribute"));
		for (var tt : tests) {
			// action
			var err = loadModelOrErr(tt.src());
			// verify
			assertTrue(err != null && err.getMessage().contains(tt.want()),
					tt.name() + ": error = " + (err == null ? null : err.getMessage())
							+ ", want \"" + tt.want() + "\"");
		}
	}

	@Test
	void validateEmptyCategorical() {
		{
			// arrange
			var m = TestUtils.loadModel("TAXONOMY:\n  t:\n    level:\n      a: {}\n");
			// action & verify
			Validator.validate(m);
		}
		{
			// arrange
			var m = TestUtils.loadModel("TAXONOMY:\n  t:\n    level: []\n");
			// action
			var err = assertThrows(OpenOddException.class, () -> Validator.validate(m));
			// verify
			assertTrue(err.getMessage().contains("no literals"),
					"expected an empty categorical error, got " + err.getMessage());
		}
	}
}
