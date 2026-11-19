// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

import io.github.ericneid.openodd.OpenOddException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the unit registry and conversions. */
class UnitsTest {

	private record Case(String unitType, String from, String to, double in, double want) {
	}

	@Test
	void convertUnit() {
		// arrange
		var tests = List.of(
				new Case("velocity", "km/h", "m/s", 72, 20),
				new Case("velocity", "m/s", "km/h", 10, 36),
				new Case("velocity", "mph", "m/s", 10, 4.4704),
				new Case("length", "km", "m", 2, 2000),
				new Case("length", "in", "cm", 10, 25.4),
				new Case("temperature", "F", "C", 32, 0),
				new Case("temperature", "C", "F", 100, 212),
				new Case("temperature", "K", "C", 283.15, 10),
				new Case("precipitation_rate", "mm/h", "in/h", 25.4, 1),
				new Case("fraction", "%", "1", 91, 0.91),
				new Case("time", "h", "s", 1.5, 5400));
		for (var tt : tests) {
			// action
			var got = Units.convertUnit(tt.unitType(), tt.from(), tt.to(), tt.in());
			// verify
			assertEquals(tt.want(), got, 1e-9, tt.unitType() + ": " + tt.from() + " to " + tt.to());
		}
		assertThrows(OpenOddException.class,
				() -> Units.convertUnit("velocity", "kg", "m/s", 1),
				"expected an error converting a mass unit as velocity");
		assertThrows(OpenOddException.class,
				() -> Units.convertUnit("no_such_type", "a", "b", 1),
				"expected an error for an unknown unit type");
	}

	@Test
	void unitTypeOfUnit() {
		{
			// action
			var ut = Units.unitTypeOfUnit("km/h");
			// verify
			assertEquals("velocity", ut, "km/h");
		}
		{
			// action
			var ut = Units.unitTypeOfUnit("mm/h");
			// verify
			assertEquals("precipitation_rate", ut, "mm/h");
		}
		{
			// action
			var ut = Units.unitTypeOfUnit("bogus");
			// verify
			assertNull(ut, "bogus unit should not resolve");
		}
	}

	/** Sequence for run-unique unit type IDs across repeated test runs. */
	private static final AtomicInteger REGISTER_TEST_SEQ = new AtomicInteger();

	@Test
	void registerUnit() {
		// arrange
		// The registry is process-global; use run-unique IDs so repeated
		// test runs do not collide with earlier registrations.
		var typeID = "test_frequency_" + REGISTER_TEST_SEQ.incrementAndGet();

		// action
		Units.registerUnitType(typeID, "Hz");
		// verify: registering the base unit succeeds.
		Units.registerUnit(typeID, "Hz", "hertz", 1, 0);

		// action
		assertThrows(OpenOddException.class, () -> Units.registerUnitType("velocity", "x"),
				"expected an error re-registering velocity");

		// action
		Units.registerUnit(typeID, "kHz", "kilohertz", 0.001, 0);

		// action
		assertThrows(OpenOddException.class, () -> Units.registerUnit(typeID, "kHz", "kilohertz", 1, 0),
				"expected an error re-registering kHz");

		// action
		assertThrows(OpenOddException.class, () -> Units.registerUnit("no_such_type", "x", "x", 1, 0),
				"expected an error registering under an unknown type");

		// action
		assertThrows(OpenOddException.class, () -> Units.registerUnit(typeID, "zero", "zero", 0, 0),
				"expected an error for a zero factor");

		// verify
		assertTrue(Units.hasUnit(typeID, "kHz") && Units.hasUnitType(typeID), "registered unit not found");
		assertNotNull(Units.unitTypeOfUnit("kHz"));

		// action
		var got = Units.convertUnit(typeID, "kHz", "Hz", 3);
		// verify
		assertEquals(3000, got, 1e-9);
	}
}
