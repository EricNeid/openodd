// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.samples;

import io.github.ericneid.TestRecords;
import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.eval.EvalOptions;
import io.github.ericneid.openodd.eval.Evaluator;
import io.github.ericneid.openodd.eval.OddVerdict;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.parser.Cod;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Checks three-valued classification on the delivery sample. OD rows
 * that do not carry every attribute value — here the road width the
 * ODD requires — classify as UNKNOWN instead of inside or outside,
 * while the boolean API keeps collapsing them to false.
 */
class MissingValueVerdictTest {

	/** Loads and validates the delivery ODD under test. */
	private static Model loadOdd() {
		var model = Loader.loadFile(
				TestUtils.resourcePath("testdata/sample_odds/delivery.yaml").toString());
		Validator.validate(model);
		assertEquals(List.of("delivery"), model.rootIds, "root modules");
		return model;
	}

	/**
	 * Parses an OD table, resolves its columns against the taxonomy of
	 * the ODD and classifies every row against the delivery module. The
	 * result lists the verdict per OD row.
	 */
	private static List<OddVerdict> classifyOd(Model model, String odCsv) {
		var od = Cod.parseCsvText(odCsv);
		od.resolve(new ConceptIndex(model.taxonomy));
		return new Evaluator(model, EvalOptions.DEFAULT).classifyCod(od)
				.stream().map(row -> row.get("delivery")).toList();
	}

	/** Rows with every value present classify as inside or outside. */
	@Test
	void completeRowsClassifyInsideOrOutside() {
		// dry and wet roads within the width and speed bounds stay
		// inside; a road wider than 5 m and night driving are outside.
		var verdicts = classifyOd(loadOdd(), """
				road_state,is_night,speed;km/h,road_width;m
				dry,false,20,4
				wet,false,25,3.5
				dry,false,20,6
				dry,true,20,4
				""");
		assertEquals(
				List.of(OddVerdict.INSIDE, OddVerdict.INSIDE, OddVerdict.OUTSIDE, OddVerdict.OUTSIDE), verdicts);
	}

	/** A row without the road width column is UNKNOWN, not outside. */
	@Test
	void missingRoadWidthIsUnknown() {
		// the OD does not record road_width, so the INCLUDE condition on
		// it cannot be judged: the row violates nothing, but there is no
		// evidence that it is inside either.
		var verdicts = classifyOd(loadOdd(), """
				road_state,is_night,speed;km/h
				dry,false,20
				""");
		assertEquals(List.of(OddVerdict.UNKNOWN), verdicts);
	}

	/** A missing EXCLUDE value also leaves the verdict UNKNOWN. */
	@Test
	void missingExcludeValueIsUnknown() {
		// the row carries no is_night column: the EXCLUDE_OR condition is
		// UNKNOWN and NOT unknown stays unknown (Kleene logic), so the
		// row does not become inside just because the exclusion cannot
		// fire.
		var verdicts = classifyOd(loadOdd(), """
				road_state,speed;km/h,road_width;m
				dry,20,4
				""");
		assertEquals(List.of(OddVerdict.UNKNOWN), verdicts);
	}

	/** The boolean API keeps collapsing UNKNOWN to false. */
	@Test
	void booleanApiCollapsesUnknownToFalse() {
		// without evidence for every condition the record is not inside:
		// insideOdd mirrors the conservative collapse of evaluateCod.
		var ev = new Evaluator(loadOdd(), EvalOptions.DEFAULT);
		var rec = TestRecords.record(
				"environment.road_state", "dry",
				"time_of_day.is_night", false,
				"ego_vehicle.speed", "20 km/h");
		// action
		var verdict = ev.classify(rec);
		var inside = ev.insideOdd(rec);
		// verify
		assertEquals(OddVerdict.UNKNOWN, verdict);
		assertFalse(inside);
	}

	/** Single records classify with the same verdicts as table rows. */
	@Test
	void recordsClassifyLikeRows() {
		var ev = new Evaluator(loadOdd(), EvalOptions.DEFAULT);
		// action
		var inside = ev.classify(TestRecords.record(
				"environment.road_state", "dry",
				"time_of_day.is_night", false,
				"infrastructure.road_width", "4 m",
				"ego_vehicle.speed", "20 km/h"));
		var outside = ev.classify(TestRecords.record(
				"environment.road_state", "dry",
				"time_of_day.is_night", false,
				"infrastructure.road_width", "6 m",
				"ego_vehicle.speed", "20 km/h"));
		// verify
		assertEquals(OddVerdict.INSIDE, inside);
		assertEquals(OddVerdict.OUTSIDE, outside);
	}
}
