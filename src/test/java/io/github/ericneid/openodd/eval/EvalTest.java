// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.eval;

import io.github.ericneid.TestRecords;
import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.parser.Cod;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.validation.Validator;
import io.github.ericneid.openodd.value.Record;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the evaluation engine. */
class EvalTest {

	private static Evaluator newEvaluator(Model m, EvalOptions options) {
		return new Evaluator(m, options);
	}

	@Test
	void evaluateWeatherExample() {
		// arrange
		var m = Loader.loadFile(TestUtils.resourcePath("testdata/weather/odd.yaml").toString());
		Validator.validate(m);
		var ev = newEvaluator(m, EvalOptions.DEFAULT);
		var cod = Cod.parseCsv(new StringReader(
				TestUtils.resourceText("testdata/weather/cod.csv")));
		cod.resolve(new ConceptIndex(m.taxonomy));
		var want = List.of(
				// Moderate rain, day, wet road, gentle wind, enough clearance.
				Map.of("example_module_1", true, "example_module_2", true, "night_module", false,
						"weather_watcher", true),
				// Heavy rain, night, icy road, strong wind, clearance fine.
				Map.of("example_module_1", false, "example_module_2", true, "night_module", true,
						"weather_watcher", true),
				// Everything unknown, insufficient clearance.
				Map.of("example_module_1", false, "example_module_2", false, "night_module", false,
						"weather_watcher", false));
		// action
		var results = ev.evaluateCod(cod);
		// verify
		for (var i = 0; i < want.size(); i++) {
			for (var e : want.get(i).entrySet()) {
				assertTrue(results.get(i).get(e.getKey()).equals(e.getValue()),
						"record " + (i + 1) + ", module \"" + e.getKey() + "\": got "
								+ results.get(i).get(e.getKey()) + ", want " + e.getValue());
			}
		}
	}

	@Test
	void evaluateUnits() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel("""
                TAXONOMY:
                  weather:
                    temperature: float temperature
                    wind_speed: float velocity
                    confidence: float fraction
                ODD:
                  m:
                    INCLUDE_AND:
                      temperature: "[-10 .. 40] C"
                      wind_speed: "< 20 m/s"
                      confidence: ">= 90 %"
                """), EvalOptions.DEFAULT);
		{
			// arrange
			var rec = TestRecords.record("weather.temperature", "100 F",
					"weather.wind_speed", "72 km/h", "weather.confidence", "91%");
			// action
			var got = ev.evaluate(rec, "m");
			// verify
			assertFalse(got, "20 m/s is not below 20 m/s; the module must not match");
		}
		{
			// arrange
			var rec = TestRecords.record("weather.temperature", "100 F",
					"weather.wind_speed", "71 km/h", "weather.confidence", "91%");
			// action
			var got = ev.evaluate(rec, "m");
			// verify
			assertTrue(got, "expected a match: " + got);
		}
		{
			// arrange
			var rec = TestRecords.record("weather.temperature", "105 F",
					"weather.wind_speed", "1 km/h", "weather.confidence", "95%");
			// action
			var got = ev.evaluate(rec, "m");
			// verify
			assertFalse(got, "expected no match: " + got);
		}
		{
			// arrange
			var boiling = newEvaluator(TestUtils.loadModel("""
                    TAXONOMY:
                      weather:
                        temperature: float temperature
                    ODD:
                      boiling:
                        INCLUDE_AND:
                          temperature: 212 F
                    """), EvalOptions.DEFAULT);
			var rec = TestRecords.record("weather.temperature", "100 C");
			// action
			var got = boiling.evaluate(rec, "boiling");
			// verify
			assertTrue(got, "100 C should equal 212 F: " + got);
		}
	}

	private record CategoricalCase(String rainLevel, String rainfall, boolean below, boolean upTo, boolean exactly) {
	}

	@Test
	void evaluateCategoricalBound() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel(TestUtils.WEATHER_TAXONOMY_YAML + """
                ODD:
                  below_heavy:
                    INCLUDE_AND:
                      rain_level: < heavy_rain
                  up_to_heavy:
                    INCLUDE_AND:
                      rain_level: <= heavy_rain
                  exactly:
                    INCLUDE_AND:
                      rain_level: moderate_rain
                  derived:
                    INCLUDE_AND:
                      rain_level: <= moderate_rain
                """), EvalOptions.DEFAULT);
		// Literal order induced by the rainfall-rate constraints.
		var cases = List.of(
				new CategoricalCase("light_rain", "", true, true, false),
				new CategoricalCase("moderate_rain", "", true, true, true),
				new CategoricalCase("heavy_rain", "", false, true, false),
				// Derivation from a numeric rainfall rate.
				new CategoricalCase("", "5 mm/h", true, true, true),
				new CategoricalCase("", "10 mm/h", false, true, false));
		for (var tt : cases) {
			var name = tt.rainLevel().isEmpty() ? tt.rainfall() : tt.rainLevel();
			// arrange
			var rec = tt.rainLevel().isEmpty()
					? TestRecords.record("environmental_conditions.weather.rainfall_rate", tt.rainfall())
					: TestRecords.record("environmental_conditions.weather.rain_level", tt.rainLevel());
			var want = Map.of(
					"below_heavy", tt.below(), "up_to_heavy", tt.upTo(), "exactly", tt.exactly());
			for (var e : want.entrySet()) {
				// action
				var got = ev.evaluate(rec, e.getKey());
				// verify
				assertTrue(got == e.getValue(),
						"rain \"" + tt.rainLevel() + "\"/\"" + tt.rainfall() + "\", module \""
								+ e.getKey() + "\": got " + got + ", want " + e.getValue());
			}
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.record("environmental_conditions.weather.rainfall_rate", "5 mm/h"), "derived");
			// verify
			assertTrue(got, "derived: " + got);
			// action
			got = ev.evaluate(
					TestRecords.record("environmental_conditions.weather.rainfall_rate", "8 mm/h"), "derived");
			// verify
			assertFalse(got, "derived out of range: " + got);
		}
	}

	private record UncertaintyCase(String name, Record rec, String mod, boolean poss, boolean cert) {
	}

	@Test
	void evaluateUncertainty() {
		// arrange
		var m = TestUtils.loadModel("""
                TAXONOMY:
                  s:
                    speed: float velocity
                    type: [car, truck]
                ODD:
                  fast:
                    INCLUDE_AND:
                      speed: "> 25 m/s"
                  inside:
                    INCLUDE_AND:
                      speed: "[0 .. 10] m/s"
                  mid:
                    INCLUDE_AND:
                      speed: 4.5
                  risky:
                    INCLUDE_AND:
                      type: [truck, bus]
                  both:
                    INCLUDE_AND:
                      type: [car, truck]
                  cars:
                    INCLUDE_AND:
                      type: [car]
                """);
		var rangeRec = TestRecords.record("s.speed", "[24 .. 26] m/s");
		var tests = List.of(
				new UncertaintyCase("range above bound", rangeRec, "fast", true, false),
				new UncertaintyCase("range disjoint", rangeRec, "inside", false, false),
				new UncertaintyCase("range misses equality", rangeRec, "mid", false, false),
				new UncertaintyCase("range inside",
						TestRecords.record("s.speed", "[4 .. 5] m/s"), "inside", true, true),
				new UncertaintyCase("equality inside range",
						TestRecords.record("s.speed", "[4 .. 5] m/s"), "mid", true, false),
				new UncertaintyCase("range straddles upper bound",
						TestRecords.record("s.speed", "[8 .. 12] m/s"), "inside", true, false),
				new UncertaintyCase("epistemic list hit",
						TestRecords.record("s.type", "[car, truck]"), "risky", true, false),
				new UncertaintyCase("epistemic list miss",
						TestRecords.record("s.type", "car"), "risky", false, false),
				new UncertaintyCase("epistemic list all satisfying",
						TestRecords.record("s.type", "[car, truck]"), "both", true, true),
				new UncertaintyCase("epistemic list none satisfying",
						TestRecords.record("s.type", "[truck, bus]"), "cars", false, false));
		for (var tt : tests) {
			// arrange
			var poss = newEvaluator(m, EvalOptions.of(UncertaintyMode.POSSIBILITY));
			var cert = newEvaluator(m, EvalOptions.of(UncertaintyMode.CERTAINTY));
			// action
			var gotPoss = poss.evaluate(tt.rec(), tt.mod());
			var gotCert = cert.evaluate(tt.rec(), tt.mod());
			// verify
			assertTrue(tt.poss() == gotPoss, tt.name() + " possibility: got " + gotPoss);
			assertTrue(tt.cert() == gotCert, tt.name() + " certainty: got " + gotCert);
		}
	}

	@Test
	void evaluateModuleAndLabelReferences() {
		// arrange
		var m = Loader.loadFile(TestUtils.resourcePath("testdata/inactive.yaml").toString());
		var ev = newEvaluator(m, EvalOptions.DEFAULT);
		// References to inactive modules are satisfied.
		for (var mod : List.of("root_mod", "negated_root")) {
			// action
			var got = ev.evaluate(TestRecords.record(), mod);
			// verify
			assertTrue(got, mod + " should be satisfied by the inactive-module rule: " + got);
		}
		{
			// action
			var got = ev.evaluate(TestRecords.record(), "inactive_mod");
			// verify
			assertTrue(got, "inactive module: " + got);
		}
		{
			// action
			var got = ev.evaluate(TestRecords.record(), "meta_only");
			// verify
			assertTrue(got, "section-less module: " + got);
		}
		{
			// action
			var err = false;
			try {
				ev.evaluate(TestRecords.record(), "nope");
			} catch (OpenOddException e) {
				err = true;
			}
			// verify
			assertTrue(err, "expected an error for an unknown module");
		}
	}

	private record MissingSectionsCase(String speed, Map<String, Boolean> want) {
	}

	@Test
	void evaluateMissingSections() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel("""
                TAXONOMY:
                  s:
                    speed: float velocity
                ODD:
                  only_include:
                    INCLUDE_AND:
                      speed: "> 1 m/s"
                  only_exclude:
                    EXCLUDE_OR:
                      speed: "> 25 m/s"
                  no_sections:
                    TITLE: Nothing to see
                """), EvalOptions.DEFAULT);
		var tests = List.of(
				new MissingSectionsCase("2 m/s",
						Map.of("only_include", true, "only_exclude", true, "no_sections", true)),
				new MissingSectionsCase("0.5 m/s",
						Map.of("only_include", false, "only_exclude", true, "no_sections", true)),
				new MissingSectionsCase("30 m/s",
						Map.of("only_include", true, "only_exclude", false, "no_sections", true)));
		for (var tt : tests) {
			// arrange
			var rec = TestRecords.record("s.speed", tt.speed());
			for (var e : tt.want().entrySet()) {
				// action
				var got = ev.evaluate(rec, e.getKey());
				// verify
				assertTrue(got == e.getValue(),
						"speed " + tt.speed() + ", module " + e.getKey() + ": got " + got
								+ ", want " + e.getValue());
			}
		}
	}

	@Test
	void evaluateUnknownCondition() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel("""
                TAXONOMY:
                  s:
                    speed: float velocity
                ODD:
                  wants_unknown:
                    INCLUDE_AND:
                      speed: unknown
                """), EvalOptions.DEFAULT);
		{
			// action
			var got = ev.evaluate(TestRecords.record(), "wants_unknown");
			// verify
			assertTrue(got, "missing value should satisfy unknown: " + got);
		}
		{
			// action
			var got = ev.evaluate(TestRecords.record("s.speed", "5 m/s"), "wants_unknown");
			// verify
			assertFalse(got, "known value must not satisfy unknown: " + got);
		}
	}

	@Test
	void evaluateParameterizedExpressions() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel("""
                TAXONOMY:
                  v:
                    ego_width: float length
                    lateral_distance: float length
                    min_distance: float length
                  t:
                    obstacle_type: [car, truck, pedestrian]
                    $expected_type: [car, truck]
                ODD:
                  relative:
                    INCLUDE_AND:
                      lateral_distance: "> 0.5*ego_width"
                  parametric_range:
                    INCLUDE_AND:
                      lateral_distance: "[0.5*ego_width .. 2*ego_width]"
                  param_list:
                    INCLUDE_AND:
                      obstacle_type: [$expected_type]
                """), EvalOptions.DEFAULT);
		var base = TestRecords.record("v.ego_width", "2 m");
		{
			// action
			var got = ev.evaluate(
					TestRecords.recordWith(base, "v.lateral_distance", "1.5 m"), "relative");
			// verify
			assertTrue(got, "1.5 m should be above 0.5*2 m: " + got);
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.recordWith(base, "v.lateral_distance", "0.9 m"), "relative");
			// verify
			assertFalse(got, "0.9 m should not be above 0.5*2 m: " + got);
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.recordWith(base, "v.lateral_distance", "3.9 m"), "parametric_range");
			// verify
			assertTrue(got, "3.9 m should be inside [0.5*2 .. 2*2] m: " + got);
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.recordWith(base, "v.lateral_distance", "4.5 m"), "parametric_range");
			// verify
			assertFalse(got, "4.5 m should be above [0.5*2 .. 2*2] m: " + got);
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.record("v.lateral_distance", "1.5 m"), "relative");
			// verify
			assertFalse(got, "missing factor: " + got);
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.record("t.obstacle_type", "car", "t.$expected_type", "car"), "param_list");
			// verify
			assertTrue(got, "param_list: " + got);
			// action
			got = ev.evaluate(
					TestRecords.record("t.obstacle_type", "pedestrian", "t.$expected_type", "car"),
					"param_list");
			// verify
			assertFalse(got, "param_list miss: " + got);
		}
	}

	@Test
	void insideODD() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel("""
                TAXONOMY:
                  s:
                    speed: float velocity
                ODD:
                  slow:
                    INCLUDE_AND:
                      speed: < 10 m/s
                """), EvalOptions.DEFAULT);
		{
			// action
			var got = ev.insideOdd(TestRecords.record("s.speed", "5 m/s"));
			// verify
			assertTrue(got, "5 m/s should be inside: " + got);
		}
		{
			// action
			var got = ev.insideOdd(TestRecords.record("s.speed", "15 m/s"));
			// verify
			assertFalse(got, "15 m/s should be outside: " + got);
		}
	}

	@Test
	void evaluateBoolConcepts() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel("""
                TAXONOMY:
                  t:
                    is_night: boolean
                ODD:
                  day:
                    INCLUDE_AND:
                      is_night: false
                """), EvalOptions.DEFAULT);
		{
			// action
			var got = ev.evaluate(TestRecords.record("t.is_night", false), "day");
			// verify
			assertTrue(got, "boolean false: " + got);
		}
		{
			// action
			var got = ev.evaluate(TestRecords.record("t.is_night", true), "day");
			// verify
			assertFalse(got, "boolean true: " + got);
		}
		{
			// action
			var got = ev.evaluate(TestRecords.record(), "day");
			// verify
			assertFalse(got, "a missing boolean value should not satisfy either polarity: " + got);
		}
	}

	@Test
	void evaluateRecordPaths() {
		// arrange
		var ev = newEvaluator(TestUtils.loadModel("""
                TAXONOMY:
                  types:
                    radial_vector:
                      angle: float angle
                      speed: float velocity
                  wind:
                    trajectory: radial_vector
                ODD:
                  moderate_wind:
                    INCLUDE_AND:
                      trajectory.speed: "[0 .. 5] m/s"
                """), EvalOptions.DEFAULT);
		{
			// arrange
			var rec = TestRecords.record("wind.trajectory", "{\"angle\": 90, \"speed\": 3}");
			// action
			var got = ev.evaluate(rec, "moderate_wind");
			// verify
			assertTrue(got, "record field: " + got);
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.record("wind.trajectory.speed", "4 m/s"), "moderate_wind");
			// verify
			assertTrue(got, "flattened field: " + got);
		}
		{
			// action
			var got = ev.evaluate(
					TestRecords.record("wind.trajectory.speed", "6 m/s"), "moderate_wind");
			// verify
			assertFalse(got, "flattened field above the range: " + got);
		}
	}
}
