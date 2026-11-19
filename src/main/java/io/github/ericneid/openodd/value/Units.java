// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

import io.github.ericneid.openodd.OpenOddException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The process-wide unit database. The built-in registry covers the unit
 * types used by the ASAM OpenODD standard; additional unit types and
 * units can be registered before parsing.
 */
public final class Units {

	private static final Map<String, Entry> TYPES = new ConcurrentHashMap<>();

	private record Entry(UnitType type, Map<String, Unit> units) {
	}

	private Units() {
	}

	static {
		// Length, base m. Factor converts base values into the unit:
		// v = v_base*Factor + Offset.
		mustRegisterUnitType("length", "m");
		mustRegisterUnit("length", new Unit("m", "meter", 1, 0));
		mustRegisterUnit("length", new Unit("km", "kilometer", 0.001, 0));
		mustRegisterUnit("length", new Unit("dm", "decimeter", 10, 0));
		mustRegisterUnit("length", new Unit("cm", "centimeter", 100, 0));
		mustRegisterUnit("length", new Unit("mm", "millimeter", 1000, 0));
		mustRegisterUnit("length", new Unit("um", "micrometer", 1e6, 0));
		mustRegisterUnit("length", new Unit("nm", "nanometer", 1e9, 0));
		mustRegisterUnit("length", new Unit("ft", "foot", 1.0 / 0.3048, 0));
		mustRegisterUnit("length", new Unit("in", "inch", 1.0 / 0.0254, 0));
		mustRegisterUnit("length", new Unit("mi", "mile", 1.0 / 1609.344, 0));
		mustRegisterUnit("length", new Unit("yd", "yard", 1.0 / 0.9144, 0));

		// Velocity, base m/s.
		mustRegisterUnitType("velocity", "m/s");
		mustRegisterUnit("velocity", new Unit("m/s", "meters per second", 1, 0));
		mustRegisterUnit("velocity", new Unit("km/h", "kilometers per hour", 3.6, 0));
		mustRegisterUnit("velocity", new Unit("kph", "kilometers per hour", 3.6, 0));
		mustRegisterUnit("velocity", new Unit("mph", "miles per hour", 1.0 / 0.44704, 0));
		mustRegisterUnit("velocity", new Unit("kn", "knot", 1.0 / 0.5144444444444444, 0));

		// Acceleration, base m/s2.
		mustRegisterUnitType("acceleration", "m/s2");
		mustRegisterUnit("acceleration", new Unit("m/s2", "meters per square second", 1, 0));
		mustRegisterUnit("acceleration", new Unit("m/s^2", "meters per square second", 1, 0));

		// Time, base s.
		mustRegisterUnitType("time", "s");
		mustRegisterUnit("time", new Unit("s", "second", 1, 0));
		mustRegisterUnit("time", new Unit("sec", "second", 1, 0));
		mustRegisterUnit("time", new Unit("msec", "millisecond", 1e3, 0));
		mustRegisterUnit("time", new Unit("ms", "millisecond", 1e3, 0));
		mustRegisterUnit("time", new Unit("usec", "microsecond", 1e6, 0));
		mustRegisterUnit("time", new Unit("us", "microsecond", 1e6, 0));
		mustRegisterUnit("time", new Unit("min", "minute", 1.0 / 60, 0));
		mustRegisterUnit("time", new Unit("h", "hour", 1.0 / 3600, 0));
		mustRegisterUnit("time", new Unit("hr", "hour", 1.0 / 3600, 0));
		mustRegisterUnit("time", new Unit("hour", "hour", 1.0 / 3600, 0));
		mustRegisterUnit("time", new Unit("day", "day", 1.0 / 86400, 0));

		// Angle, base rad.
		mustRegisterUnitType("angle", "rad");
		mustRegisterUnit("angle", new Unit("rad", "radian", 1, 0));
		mustRegisterUnit("angle", new Unit("deg", "degree", 180 / Math.PI, 0));
		mustRegisterUnit("angle", new Unit("degree", "degree", 180 / Math.PI, 0));
		mustRegisterUnit("angle", new Unit("grad", "gradian", 200 / Math.PI, 0));

		// Mass, base kg.
		mustRegisterUnitType("mass", "kg");
		mustRegisterUnit("mass", new Unit("kg", "kilogram", 1, 0));
		mustRegisterUnit("mass", new Unit("g", "gram", 1000, 0));
		mustRegisterUnit("mass", new Unit("t", "ton", 0.001, 0));
		mustRegisterUnit("mass", new Unit("lb", "pound", 1.0 / 0.45359237, 0));

		// Temperature, base C: v_C = (v - Offset) / Factor.
		mustRegisterUnitType("temperature", "C");
		mustRegisterUnit("temperature", new Unit("C", "celsius", 1, 0));
		mustRegisterUnit("temperature", new Unit("celsius", "celsius", 1, 0));
		mustRegisterUnit("temperature", new Unit("F", "fahrenheit", 1.8, 32));
		mustRegisterUnit("temperature", new Unit("fahrenheit", "fahrenheit", 1.8, 32));
		mustRegisterUnit("temperature", new Unit("K", "kelvin", 1, 273.15));
		mustRegisterUnit("temperature", new Unit("kelvin", "kelvin", 1, 273.15));

		// Precipitation rate, base mm/h.
		mustRegisterUnitType("precipitation_rate", "mm/h");
		mustRegisterUnit("precipitation_rate", new Unit("mm/h", "millimeters per hour", 1, 0));
		mustRegisterUnit("precipitation_rate", new Unit("mm/hr", "millimeters per hour", 1, 0));
		mustRegisterUnit("precipitation_rate", new Unit("cm/h", "centimeters per hour", 0.1, 0));
		mustRegisterUnit("precipitation_rate", new Unit("in/h", "inches per hour", 1.0 / 25.4, 0));

		// Data rate, base bit/s.
		mustRegisterUnitType("data_rate", "bit/s");
		mustRegisterUnit("data_rate", new Unit("bit/s", "bits per second", 1, 0));
		mustRegisterUnit("data_rate", new Unit("bps", "bits per second", 1, 0));
		mustRegisterUnit("data_rate", new Unit("kbps", "kilobits per second", 1e-3, 0));
		mustRegisterUnit("data_rate", new Unit("Mbps", "megabits per second", 1e-6, 0));
		mustRegisterUnit("data_rate", new Unit("Gbps", "gigabits per second", 1e-9, 0));
		mustRegisterUnit("data_rate", new Unit("MB/s", "megabytes per second", 1.0 / 8e6, 0));

		// Occurrence rate, base 1/hr.
		mustRegisterUnitType("occurrence_rate", "1/hr");
		mustRegisterUnit("occurrence_rate", new Unit("1/hr", "occurrences per hour", 1, 0));
		mustRegisterUnit("occurrence_rate", new Unit("occ/hr", "occurrences per hour", 1, 0));
		mustRegisterUnit("occurrence_rate", new Unit("occ/s", "occurrences per second", 3600, 0));

		// Fraction (dimensionless ratio), base 1.
		mustRegisterUnitType("fraction", "1");
		mustRegisterUnit("fraction", new Unit("1", "fraction", 1, 0));
		mustRegisterUnit("fraction", new Unit("", "fraction", 1, 0));
		mustRegisterUnit("fraction", new Unit("%", "percent", 100, 0));
		mustRegisterUnit("fraction", new Unit("percent", "percent", 100, 0));
		mustRegisterUnit("fraction", new Unit("ppm", "parts per million", 1e6, 0));

		// Count, base count (dimensionless).
		mustRegisterUnitType("count", "count");
		mustRegisterUnit("count", new Unit("count", "count", 1, 0));
		mustRegisterUnit("count", new Unit("", "count", 1, 0));

		// Area, base m2.
		mustRegisterUnitType("area", "m2");
		mustRegisterUnit("area", new Unit("m2", "square meter", 1, 0));
		mustRegisterUnit("area", new Unit("km2", "square kilometer", 1e-6, 0));
		mustRegisterUnit("area", new Unit("cm2", "square centimeter", 1e4, 0));

		// Volume, base m3.
		mustRegisterUnitType("volume", "m3");
		mustRegisterUnit("volume", new Unit("m3", "cubic meter", 1, 0));
		mustRegisterUnit("volume", new Unit("L", "liter", 1000, 0));
		mustRegisterUnit("volume", new Unit("l", "liter", 1000, 0));
		mustRegisterUnit("volume", new Unit("ml", "milliliter", 1e6, 0));
	}

	/**
	 * Registers a new unit type with the given base unit symbol. Throws
	 * when the ID is empty or already registered.
	 */
	public static void registerUnitType(String id, String base) {
		if (id == null || id.isEmpty()) {
			throw new OpenOddException("unit type ID must not be empty");
		}
		if (TYPES.putIfAbsent(id, new Entry(new UnitType(id, base), new ConcurrentHashMap<>())) != null) {
			throw new OpenOddException("unit type \"%s\" already registered".formatted(id));
		}
	}

	/**
	 * Registers a unit symbol for a previously registered unit type.
	 * Throws when the unit type is unknown or the symbol is already
	 * registered for the type.
	 */
	public static void registerUnit(String unitType, String symbol, String name, double factor, double offset) {
		if (factor == 0) {
			throw new OpenOddException(
					"unit \"%s\" of type \"%s\" has zero factor".formatted(symbol, unitType));
		}
		var entry = TYPES.get(unitType);
		if (entry == null) {
			throw new OpenOddException("unknown unit type \"%s\"".formatted(unitType));
		}
		if (entry.units().putIfAbsent(symbol, new Unit(symbol, name, factor, offset)) != null) {
			throw new OpenOddException(
					"unit \"%s\" already registered for type \"%s\"".formatted(symbol, unitType));
		}
	}

	private static void mustRegisterUnitType(String id, String base) {
		registerUnitType(id, base);
	}

	private static void mustRegisterUnit(String unitType, Unit u) {
		registerUnit(unitType, u.id(), u.name(), u.factor(), u.offset());
	}

	/** Returns the IDs of all registered unit types. */
	public static List<String> unitTypes() {
		return new ArrayList<>(new TreeMap<>(TYPES).keySet());
	}

	/** Reports whether a unit type with the given ID is registered. */
	public static boolean hasUnitType(String id) {
		return TYPES.containsKey(id);
	}

	/** Reports whether the unit type knows the unit symbol. */
	public static boolean hasUnit(String unitType, String symbol) {
		var entry = TYPES.get(unitType);
		return entry != null && entry.units().containsKey(symbol);
	}

	/** Converts a numeric value between two units of the same unit type. */
	public static double convertUnit(String unitType, String from, String to, double v) {
		if (from.equals(to)) {
			return v;
		}
		var entry = TYPES.get(unitType);
		if (entry == null) {
			// Unknown unit types are compared symbolically.
			throw new OpenOddException(
					"cannot convert \"%s\" to \"%s\": unknown unit type \"%s\"".formatted(from, to, unitType));
		}
		var fromUnit = entry.units().get(from);
		if (fromUnit == null) {
			throw new OpenOddException(
					"unknown unit \"%s\" for unit type \"%s\"".formatted(from, unitType));
		}
		var toUnit = entry.units().get(to);
		if (toUnit == null) {
			throw new OpenOddException(
					"unknown unit \"%s\" for unit type \"%s\"".formatted(to, unitType));
		}
		var base = (v - fromUnit.offset()) / fromUnit.factor();
		return base * toUnit.factor() + toUnit.offset();
	}

	/**
	 * Returns the registered unit type for a unit symbol, or {@code null}.
	 * Used to interpret column markers such as ";count" or ";mm/hr".
	 */
	public static String unitTypeOfUnit(String symbol) {
		if (symbol == null || symbol.isEmpty()) {
			return "";
		}
		for (var e : TYPES.entrySet()) {
			if (e.getValue().units().containsKey(symbol)) {
				return e.getValue().type().id();
			}
		}
		return null;
	}

	/** Reports whether the symbol is a registered unit of the given unit type. */
	public static boolean isKnownUnitSymbol(String unitType, String symbol) {
		if (symbol == null || symbol.isEmpty()) {
			return true;
		}
		var entry = TYPES.get(unitType);
		return entry != null && entry.units().containsKey(symbol);
	}

	/** Reports whether any unit is registered for the type. */
	public static boolean unitTypeHasUnits(String id) {
		var entry = TYPES.get(id);
		return entry != null && !entry.units().isEmpty();
	}

	/** Returns the base unit symbol of a unit type, or {@code null} when unknown. */
	public static String baseUnitOf(String unitType) {
		var entry = TYPES.get(unitType);
		return entry == null ? null : entry.type().base();
	}
}
