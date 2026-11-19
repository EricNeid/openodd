// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.samples;

import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.eval.EvalOptions;
import io.github.ericneid.openodd.eval.Evaluator;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.parser.Cod;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks a theoretical OD (operational domain) for compliance with an
 * ODD (operational design domain), using the highway sample. Every test
 * evaluates its own slice of the OD against the highway ODD.
 */
class HighwayComplianceTest {

	/** Loads and validates the highway ODD under test. */
	private static Model loadOdd() {
		var model = Loader.loadFile(
				TestUtils.resourcePath("testdata/sample_odds/highway.yaml").toString());
		Validator.validate(model);
		assertEquals(List.of("highway"), model.rootIds, "root modules");
		return model;
	}

	/**
	 * Parses an OD table, resolves its columns against the taxonomy of
	 * the ODD and evaluates every row against the highway module. The
	 * result lists the verdict per OD row.
	 */
	private static List<Boolean> evaluateOd(Model model, String odCsv) {
		var od = Cod.parseCsvText(odCsv);
		od.resolve(new ConceptIndex(model.taxonomy));
		return new Evaluator(model, EvalOptions.DEFAULT).evaluateCod(od)
				.stream().map(row -> row.get("highway")).toList();
	}

	/** Compliant driving scenarios stay inside the ODD. */
	@Test
	void compliantScenariosStayInside() {
		// light rain by day on a dry road at cruise speed, and moderate
		// rain on a wet road: every INCLUDE_AND condition holds and no
		// EXCLUDE_OR condition trips.
		var verdicts = evaluateOd(loadOdd(), """
				rainfall_rate;mm/h,rain_level,road_state,is_night,speed;km/h
				0.5,light_rain,dry,false,100
				5,moderate_rain,wet,false,120
				""");
		assertEquals(List.of(true, true), verdicts);
	}

	/** The speed range "[80 .. 130] km/h" is closed on both ends. */
	@Test
	void speedRangeIsClosedOnBothEnds() {
		// 80 and 130 km/h sit exactly on the bounds and stay inside;
		// 70 and 140 km/h are just outside the range.
		var verdicts = evaluateOd(loadOdd(), """
				rainfall_rate;mm/h,rain_level,road_state,is_night,speed;km/h
				0,no_rain,dry,false,80
				1,light_rain,dry,false,130
				1,light_rain,dry,false,70
				1,light_rain,dry,false,140
				""");
		assertEquals(List.of(true, true, false, false), verdicts);
	}

	/** The EXCLUDE_OR section puts night and winter road states outside. */
	@Test
	void excludeOrPutsScenariosOutside() {
		// every row satisfies the INCLUDE_AND conditions, but each one
		// trips one EXCLUDE_OR condition: night, icy and snowy roads.
		var verdicts = evaluateOd(loadOdd(), """
				rainfall_rate;mm/h,rain_level,road_state,is_night,speed;km/h
				1,light_rain,dry,true,100
				1,light_rain,icy,false,100
				1,light_rain,snowy,false,100
				""");
		assertEquals(List.of(false, false, false), verdicts);
	}

	/** The rain level bound {@code <= moderate_rain} also applies to derived levels. */
	@Test
	void rainLevelBoundAndDerivation() {
		// heavy rain exceeds the bound; an unknown rain level is derived
		// from the measured rainfall rate (moderate rain) and passes.
		var verdicts = evaluateOd(loadOdd(), """
				rainfall_rate;mm/h,rain_level,road_state,is_night,speed;km/h
				10,heavy_rain,dry,false,100
				5,unknown,dry,false,100
				""");
		assertEquals(List.of(false, true), verdicts);
	}
}
