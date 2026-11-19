// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.samples;

import io.github.ericneid.TestRecords;
import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.eval.EvalOptions;
import io.github.ericneid.openodd.eval.Evaluator;
import io.github.ericneid.openodd.expr.BoundExpr;
import io.github.ericneid.openodd.expr.CmpOp;
import io.github.ericneid.openodd.expr.EqualExpr;
import io.github.ericneid.openodd.expr.ListExpr;
import io.github.ericneid.openodd.expr.RangeExpr;
import io.github.ericneid.openodd.expr.ScalarKind;
import io.github.ericneid.openodd.model.ConceptKind;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.model.SectionOperator;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.validation.Validator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the three example ODDs in testdata/sample_odds. Each file gets
 * its own test that verifies the parsed structure in detail and then
 * exercises the evaluation semantics the file demonstrates.
 */
class SampleOddsTest {

	/** Loads and validates one of the sample ODDs. */
	private static Model load(String name) {
		var m = Loader.loadFile(
				TestUtils.resourcePath("testdata/sample_odds/" + name).toString());
		Validator.validate(m);
		return m;
	}

	/** city_daytime.yaml: single-file ODD with a boolean condition, a categorical list and a numeric range. */
	@Test
	void cityDaytime() {
		var m = load("city_daytime.yaml");

		// Taxonomy: two record roots with one boolean attribute, one
		// categorical and one numeric attribute.
		assertEquals(2, m.taxonomy.roots.size());
		assertEquals(ConceptKind.RECORD, m.taxonomy.concept("environment").kind);
		var isNight = m.taxonomy.concept("environment.is_night");
		assertEquals(ConceptKind.ATTRIBUTE, isNight.kind);
		assertEquals("boolean", isNight.primType);
		var roadState = m.taxonomy.concept("environment.road_state");
		assertEquals(ConceptKind.CATEGORICAL, roadState.kind);
		assertEquals(List.of("dry", "wet", "icy", "snowy"),
				roadState.literals.stream().map(l -> l.name).toList());
		var speed = m.taxonomy.concept("ego_vehicle.speed");
		assertEquals(ConceptKind.ATTRIBUTE, speed.kind);
		assertEquals("float", speed.primType);
		assertEquals("velocity", speed.unitType);

		// Single root module: INCLUDE_AND over all three concepts, no EXCLUDE.
		assertEquals(List.of("city_daytime"), m.rootIds);
		var odd = m.module("city_daytime");
		assertEquals("City driving during the day", odd.title);
		assertNull(odd.exclude);
		assertEquals(SectionOperator.AND, odd.include.operator);
		assertEquals(List.of("is_night", "road_state", "speed"),
				odd.include.conditions.stream().map(c -> c.ref).toList());

		var nightWanted = assertInstanceOf(EqualExpr.class, odd.include.conditions.get(0).expr);
		assertEquals(ScalarKind.BOOL, nightWanted.kind());
		assertFalse(nightWanted.bool());
		var roadWanted = assertInstanceOf(ListExpr.class, odd.include.conditions.get(1).expr);
		assertEquals(List.of("dry", "wet"), roadWanted.items());
		var speedRange = assertInstanceOf(RangeExpr.class, odd.include.conditions.get(2).expr);
		assertEquals("km/h", speedRange.unit());
		assertEquals(0.0, speedRange.min().left().number(), 1e-12);
		assertEquals(50.0, speedRange.max().left().number(), 1e-12);

		// Evaluation: day, dry road and up to 50 km/h stays inside.
		var ev = new Evaluator(m, EvalOptions.DEFAULT);
		var base = TestRecords.record(
				"environment.is_night", false,
				"environment.road_state", "dry",
				"ego_vehicle.speed", "30 km/h");
		assertTrue(ev.insideOdd(base));
		assertTrue(ev.insideOdd(TestRecords.recordWith(base, "environment.road_state", "wet")));
		// the speed range is closed on both ends
		assertTrue(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.speed", "50 km/h")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.speed", "60 km/h")));
		// night and winter road states put the record outside
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "environment.is_night", "true")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "environment.road_state", "icy")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "environment.road_state", "snowy")));
		// missing values do not satisfy the conditions
		assertFalse(ev.insideOdd(TestRecords.record()));
	}

	/** highway.yaml: derived categorical literals with an induced order and an EXCLUDE_OR section. */
	@Test
	void highway() {
		var m = load("highway.yaml");

		// Taxonomy: four rain literals, each defined by one rainfall_rate range.
		var rainLevel = m.taxonomy.concept("environment.rain_level");
		assertEquals(ConceptKind.CATEGORICAL, rainLevel.kind);
		assertEquals(List.of("no_rain", "light_rain", "moderate_rain", "heavy_rain"),
				rainLevel.literals.stream().map(l -> l.name).toList());
		for (var lit : rainLevel.literals) {
			assertEquals(1, lit.constraints.size());
			assertEquals("rainfall_rate", lit.constraints.get(0).ref());
		}
		var noRain = assertInstanceOf(RangeExpr.class,
				rainLevel.literals.get(0).constraints.get(0).expr());
		assertEquals("mm/h", noRain.unit());

		// Root module: INCLUDE_AND with a categorical bound and a speed
		// range; EXCLUDE_OR with night and winter road states.
		assertEquals(List.of("highway"), m.rootIds);
		var odd = m.module("highway");
		assertEquals("Highway driving", odd.title);
		assertEquals(SectionOperator.AND, odd.include.operator);
		assertEquals(SectionOperator.OR, odd.exclude.operator);
		assertEquals(List.of("rain_level", "speed"),
				odd.include.conditions.stream().map(c -> c.ref).toList());
		var rainBound = assertInstanceOf(BoundExpr.class, odd.include.conditions.get(0).expr);
		assertEquals(CmpOp.LESS_EQUAL, rainBound.op());
		assertEquals("moderate_rain", rainBound.term().left().ref());
		var speedRange = assertInstanceOf(RangeExpr.class, odd.include.conditions.get(1).expr);
		assertEquals("km/h", speedRange.unit());
		assertEquals(80.0, speedRange.min().left().number(), 1e-12);
		assertEquals(130.0, speedRange.max().left().number(), 1e-12);
		assertEquals(List.of("is_night", "road_state"),
				odd.exclude.conditions.stream().map(c -> c.ref).toList());
		var nightWanted = assertInstanceOf(EqualExpr.class, odd.exclude.conditions.get(0).expr);
		assertTrue(nightWanted.bool());
		var roadWanted = assertInstanceOf(ListExpr.class, odd.exclude.conditions.get(1).expr);
		assertEquals(List.of("icy", "snowy"), roadWanted.items());

		// Evaluation with a derived literal: day, dry, highway speed and
		// moderate rain is inside.
		var ev = new Evaluator(m, EvalOptions.DEFAULT);
		var base = TestRecords.record(
				"time_of_day.is_night", false,
				"environment.road_state", "dry",
				"ego_vehicle.speed", "100 km/h",
				"environment.rainfall_rate", "5 mm/h");
		assertTrue(ev.insideOdd(base));
		// the rain level derives from the rainfall rate and passes the
		// bound through the induced literal order
		assertTrue(ev.insideOdd(TestRecords.recordWith(base, "environment.rainfall_rate", "0.05 mm/h")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "environment.rainfall_rate", "10 mm/h")));
		// an explicit categorical value takes the same bound
		var withLevel = TestRecords.record(
				"time_of_day.is_night", false,
				"environment.road_state", "dry",
				"ego_vehicle.speed", "100 km/h",
				"environment.rain_level", "light_rain");
		assertTrue(ev.insideOdd(withLevel));
		assertFalse(ev.insideOdd(TestRecords.recordWith(withLevel, "environment.rain_level", "heavy_rain")));
		// the speed range is closed on both ends
		assertTrue(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.speed", "130 km/h")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.speed", "140 km/h")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.speed", "70 km/h")));
		// EXCLUDE_OR: night or winter road states put the record outside
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "time_of_day.is_night", "true")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "environment.road_state", "icy")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "environment.road_state", "snowy")));
	}

	/** parking.yaml: composed ODD with a module reference and a parameterized bound. */
	@Test
	void parking() {
		var m = load("parking.yaml");

		// Taxonomy: the minimum lateral distance is a "$" parameter.
		var minDistance = m.taxonomy.concept("ego_vehicle.$min_lateral_distance");
		assertTrue(minDistance.parameter);
		assertEquals(ConceptKind.ATTRIBUTE, minDistance.kind);
		assertEquals("length", minDistance.unitType);

		// Modules: one root ODD referencing one helper module.
		assertEquals(List.of("parking"), m.rootIds);
		var odd = m.module("parking");
		assertEquals("Automated parking", odd.title);
		assertTrue(odd.root);
		var cautious = m.module("cautious_driving");
		assertFalse(cautious.root);
		assertEquals("Slow on intact surfaces", cautious.title);
		assertEquals(SectionOperator.AND, cautious.include.operator);
		assertEquals(List.of("speed", "road_state"),
				cautious.include.conditions.stream().map(c -> c.ref).toList());

		// The ODD includes the helper module and a parameterized bound.
		assertEquals(List.of("cautious_driving", "lateral_distance"),
				odd.include.conditions.stream().map(c -> c.ref).toList());
		var moduleRef = assertInstanceOf(EqualExpr.class, odd.include.conditions.get(0).expr);
		assertEquals(ScalarKind.BOOL, moduleRef.kind());
		assertTrue(moduleRef.bool());
		var bound = assertInstanceOf(BoundExpr.class, odd.include.conditions.get(1).expr);
		assertEquals(CmpOp.GREATER, bound.op());
		assertEquals(0.5, bound.term().left().number(), 1e-12);
		assertEquals('*', bound.term().op());
		assertEquals("min_lateral_distance", bound.term().right().ref());

		// Evaluation: slow, dry road and enough lateral distance.
		var ev = new Evaluator(m, EvalOptions.DEFAULT);
		var base = TestRecords.record(
				"environment.road_state", "dry",
				"ego_vehicle.speed", "5 km/h",
				"ego_vehicle.lateral_distance", "1.5 m",
				"ego_vehicle.$min_lateral_distance", "2 m");
		assertTrue(ev.insideOdd(base));
		assertTrue(ev.evaluate(base, "cautious_driving"));
		// the helper module converts units: 2 m/s = 7.2 km/h stays below 10 km/h
		assertTrue(ev.evaluate(TestRecords.recordWith(base, "ego_vehicle.speed", "2 m/s"), "cautious_driving"));
		// the module reference propagates: a fast ego vehicle fails the
		// helper and therefore the ODD
		assertFalse(ev.evaluate(
				TestRecords.recordWith(base, "ego_vehicle.speed", "15 km/h"), "cautious_driving"));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.speed", "15 km/h")));
		// icy roads fail the helper as well
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "environment.road_state", "icy")));
		// parameterized bound (strict ">"): 0.9 m and the boundary 1 m = 0.5*2 m
		// fail, 1.1 m passes
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.lateral_distance", "0.9 m")));
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.lateral_distance", "1 m")));
		assertTrue(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.lateral_distance", "1.1 m")));
		// a larger parameter tightens the bound: 0.5*4 m = 2 m
		assertFalse(ev.insideOdd(TestRecords.recordWith(base, "ego_vehicle.$min_lateral_distance", "4 m")));
		assertTrue(ev.insideOdd(TestRecords.recordWith(
				TestRecords.recordWith(base, "ego_vehicle.lateral_distance", "2.5 m"),
				"ego_vehicle.$min_lateral_distance", "4 m")));
	}
}
