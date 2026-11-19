// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

import io.github.ericneid.openodd.OpenOddException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the value model and the COD cell parser. */
class ValueTest {

	private record Case(String in, String unit, String unitType, Value want) {
	}

	@Test
	void parseScalarValue() {
		// arrange
		var tests = java.util.List.of(
				new Case("5", "mm/h", "precipitation_rate", Value.number(5, "mm/h", "precipitation_rate")),
				new Case("5", "", "", Value.number(5)),
				new Case("5 mm/h", "", "", Value.number(5, "mm/h", "precipitation_rate")),
				new Case("91%", "", "", Value.number(91, "%", "fraction")),
				new Case("2.5e1", "", "", Value.number(25)),
				new Case("true", "", "", Value.bool(true)),
				new Case("false", "", "", Value.bool(false)),
				new Case("unknown", "", "", Value.missing()),
				new Case("none", "", "", Value.missing()),
				new Case("", "", "", Value.missing()),
				new Case("wet", "", "", Value.categorical("wet")));
		for (var tt : tests) {
			// action
			var got = Values.parseScalarValue(tt.in(), tt.unit(), tt.unitType());
			// verify
			assertEquals(tt.want(), got, tt.in() + " @" + tt.unit());
		}
	}

	@Test
	void parseCellValue() {
		{
			// arrange
			var want = Value.range(1, 2, "m", "length");
			// action
			var got = Values.parseCellValue("[1 .. 2] m", "m", "length");
			// verify
			assertEquals(want, got);
		}
		{
			// action
			var got = Values.parseCellValue("[10.6 .. 10.7] m", "", "");
			// verify
			assertTrue(got.unit().equals("m") && got.unitType().equals("length"),
					"unit after the bracket was not applied: " + got);
		}
		{
			// action
			var got = Values.parseCellValue("[cyclist, motorcycle]", "", "");
			// verify
			assertTrue(got.kind() == ValueKind.LIST && got.items().size() == 2
					&& got.items().get(0).str().equals("cyclist")
					&& got.items().get(1).str().equals("motorcycle"),
					"unexpected list cell: " + got);
		}
		{
			// action
			var got = Values.parseCellValue("RQ31;RQ36", "", "");
			// verify
			assertTrue(got.kind() == ValueKind.LIST && got.items().size() == 2
					&& got.items().get(0).str().equals("RQ31"),
					"unexpected semicolon list cell: " + got);
		}
		{
			// action
			var got = Values.parseCellValue("unknown", "mm/h", "precipitation_rate");
			// verify
			assertTrue(got.kind() == ValueKind.MISSING,
					"unknown should parse as a missing value: " + got);
		}
		{
			// action
			var got = Values.parseCellValue("{\"center\": {\"lat\": 48.1, \"lon\": 11.5}, \"name\": \"home\"}", "", "");
			// verify
			assertInstanceOf(Value.class, got);
			assertTrue(got.kind() == ValueKind.RECORD, "expected a record value, got " + got.kind());
			assertTrue(got.rec().get("center").kind() == ValueKind.RECORD,
					"nested record: " + got.rec().get("center"));
			assertTrue(got.rec().get("name").kind() == ValueKind.CATEGORICAL
					&& got.rec().get("name").str().equals("home"),
					"string field: " + got.rec().get("name"));
		}
		{
			// action & verify
			assertThrows(OpenOddException.class, () -> Values.parseCellValue("[a, b", "", ""),
					"expected an error for a malformed list");
		}
	}

	@Test
	void valueString() {
		// arrange
		var tests = java.util.List.of(
				new Object[]{Value.missing(), "unknown"},
				new Object[]{Value.number(5, "mm/h", ""), "5 mm/h"},
				new Object[]{Value.bool(true), "true"},
				new Object[]{Value.categorical("wet"), "wet"},
				new Object[]{Value.range(1, 2, "m", ""), "[1 .. 2] m"});
		for (var tt : tests) {
			// action
			var got = tt[0].toString();
			// verify
			assertEquals(tt[1], got);
		}
	}

	@Test
	void recordValueLookup() {
		// arrange
		var rec = new Record();
		rec.values().put("weather.wind_speed", Value.number(3));
		rec.values().put("parent.$ego_width", Value.number(2));
		rec.values().put("trajectory", Value.record(Map.of("angle", Value.number(90))));
		{
			// action
			var v = rec.value("weather.wind_speed");
			// verify
			assertTrue(v.isPresent() && v.get().num() == 3, "plain lookup failed: " + v);
		}
		{
			// action
			var v = rec.value("parent.$ego_width");
			// verify
			assertTrue(v.isPresent() && v.get().num() == 2, "lookup with $ failed: " + v);
		}
		{
			// action
			var v = rec.value("parent.ego_width");
			// verify
			assertTrue(v.isPresent() && v.get().num() == 2, "lookup without $ failed: " + v);
		}
		{
			// action
			var v = rec.value("nope");
			// verify
			assertFalse(v.isPresent(), "expected a miss for an unknown key");
		}
	}
}
