// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.expr.Expressions;
import io.github.ericneid.openodd.internal.Texts;
import io.github.ericneid.openodd.model.Concept;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parsers for the value spellings of COD/OD cells and condition values.
 */
public final class Values {

	private Values() {
	}

	/**
	 * Parses a scalar COD/OD cell or condition value. Numbers are
	 * recognized with an optional trailing unit, for example "5", "5 mm/h"
	 * or "91%". Booleans, the unknown keywords and categorical literals
	 * are recognized as well. defaultUnit (with its unit type) is applied
	 * to bare numbers, typically the unit from a table column header.
	 */
	public static Value parseScalarValue(String input, String defaultUnit, String defaultUnitType) {
		var s = input.trim();
		if (s.isEmpty()) {
			return Value.missing();
		}
		if (Texts.isUnknownKeyword(s)) {
			return Value.missing();
		}
		switch (s.toLowerCase()) {
			case "true" -> {
				return Value.bool(true);
			}
			case "false" -> {
				return Value.bool(false);
			}
			default -> {
			}
		}
		var nu = Texts.splitNumberUnit(s);
		if (nu != null) {
			var unit = nu.unit();
			var unitType = defaultUnitType;
			if (!unit.isEmpty()) {
				var ut = Units.unitTypeOfUnit(unit);
				var known = ut != null;
				if (!known && Units.unitTypeHasUnits(defaultUnitType)) {
					throw new OpenOddException("unknown unit \"%s\"".formatted(unit));
				}
				if (known) {
					unitType = ut;
				}
			} else {
				unit = defaultUnit;
			}
			return Value.number(nu.num(), unit, unitType);
		}
		// A leading comparison or range marker denotes an expression, not
		// a plain value; the caller handles those. Guard against silently
		// accepting them here.
		if (s.startsWith("[")) {
			throw new OpenOddException("unexpected range or list \"%s\"".formatted(s));
		}
		return Value.categorical(s);
	}

	/**
	 * Parses a single COD/OD table cell. Cells support numbers, booleans,
	 * categorical literals, uncertainty ranges "[a .. b]", alternative
	 * lists "[x, y]" or "x;y", JSON record structures and the unknown
	 * keywords.
	 */
	public static Value parseCellValue(String cell, String defaultUnit, String defaultUnitType) {
		var c = cell.trim();
		if (c.isEmpty()) {
			return Value.missing();
		}
		if (Texts.isUnknownKeyword(c)) {
			return Value.missing();
		}
		if (c.startsWith("{") && c.endsWith("}")) {
			return parseRecordValue(c);
		}
		if (c.startsWith("[")) {
			var end = c.indexOf(']');
			if (end < 0) {
				throw new OpenOddException("malformed range or list \"%s\"".formatted(c));
			}
			var inner = c.substring(1, end).trim();
			// A unit may follow the closing bracket, for example
			// "[10.6 .. 10.7] m"; it overrides the column default unit.
			var cellUnit = c.substring(end + 1).trim();
			if (inner.contains("..")) {
				return parseRangeCell(inner, cellUnit, defaultUnit, defaultUnitType);
			}
			if (!cellUnit.isEmpty()) {
				throw new OpenOddException("list value \"%s\" must not carry a unit".formatted(c));
			}
			var items = new java.util.ArrayList<Value>();
			for (var item : Texts.splitListItems(inner)) {
				items.add(parseScalarValue(item, defaultUnit, defaultUnitType));
			}
			return Value.list(items);
		}
		if (c.contains(";") && !c.matches(".*[ \\t].*")) {
			// Semicolon separated multi-values as used for categorical
			// alternatives in tabular exports (for example "RQ31;RQ36").
			var anyKnown = false;
			for (var it : c.split(";", -1)) {
				if (!it.trim().isEmpty()) {
					anyKnown = true;
				}
			}
			if (anyKnown) {
				var items = new java.util.ArrayList<Value>();
				for (var item : c.split(";", -1)) {
					item = item.trim();
					if (item.isEmpty()) {
						continue;
					}
					items.add(parseScalarValue(item, defaultUnit, defaultUnitType));
				}
				return Value.list(items);
			}
		}
		return parseScalarValue(c, defaultUnit, defaultUnitType);
	}

	/**
	 * Parses the inner part of "[a .. b]". cellUnit is the unit written
	 * after the closing bracket, if any; defaultUnit comes from the column
	 * header. A unit on a range bound overrides both.
	 */
	static Value parseRangeCell(String inner, String cellUnit, String defaultUnit, String defaultUnitType) {
		var parts = Expressions.splitOnDoubleDot(inner);
		var lo = parseRangeBound(parts[0].trim(), defaultUnit);
		var hi = parseRangeBound(parts[1].trim(), defaultUnit);
		var unit = defaultUnit;
		if (!cellUnit.isEmpty()) {
			unit = cellUnit;
		}
		if (!lo.unit().isEmpty()) {
			unit = lo.unit();
		} else if (!hi.unit().isEmpty()) {
			unit = hi.unit();
		}
		if (!lo.unit().isEmpty() && !hi.unit().isEmpty() && !lo.unit().equals(hi.unit())) {
			throw new OpenOddException(
					"range bounds use different units \"%s\" and \"%s\"".formatted(lo.unit(), hi.unit()));
		}
		var unitType = defaultUnitType;
		if (!unit.isEmpty()) {
			var ut = Units.unitTypeOfUnit(unit);
			if (ut != null) {
				unitType = ut;
			}
		}
		return Value.range(lo.num(), hi.num(), unit, unitType);
	}

	record RangeBound(double num, String unit) {
	}

	private static RangeBound parseRangeBound(String s, String defaultUnit) {
		var nu = Texts.splitNumberUnit(s);
		if (nu == null) {
			throw new OpenOddException("invalid range bound \"%s\"".formatted(s));
		}
		var unit = nu.unit();
		if (unit.isEmpty()) {
			unit = defaultUnit;
		}
		return new RangeBound(nu.num(), unit);
	}

	/**
	 * Parses a JSON serialized record structure per Section 8.3.4.4. The
	 * returned value's record map is keyed by paths relative to the
	 * record root, for example "center.lat".
	 */
	static Value parseRecordValue(String s) {
		JsonElement root;
		try {
			root = JsonParser.parseString(s);
		} catch (JsonParseException e) {
			throw new OpenOddException("invalid record JSON: " + e.getMessage(), e);
		}
		if (!root.isJsonObject()) {
			throw new OpenOddException("invalid record JSON: expected an object");
		}
		return Value.record(flattenInto(null, "", root.getAsJsonObject()));
	}

	/**
	 * Walks a decoded JSON object. Returns the record fields keyed by
	 * paths relative to the current prefix, and additionally writes every
	 * scalar field into dst (when non-null) under its full prefixed path.
	 */
	static Map<String, Value> flattenInto(Map<String, Value> dst, String prefix, JsonObject raw) {
		var rec = new LinkedHashMap<String, Value>();
		for (var entry : raw.entrySet()) {
			var key = entry.getKey();
			var v = entry.getValue();
			var full = prefix.isEmpty() ? key : prefix + "." + key;
			if (v.isJsonObject()) {
				var sub = flattenInto(dst, full, v.getAsJsonObject());
				rec.put(key, Value.record(sub));
			} else if (v.isJsonNull()) {
				rec.put(key, Value.missing());
				if (dst != null) {
					dst.put(full, Value.missing());
				}
			} else if (v.isJsonPrimitive()) {
				var p = v.getAsJsonPrimitive();
				Value val;
				if (p.isBoolean()) {
					val = Value.bool(p.getAsBoolean());
				} else if (p.isNumber()) {
					val = Value.number(p.getAsDouble(), "", "");
				} else {
					val = parseJsonScalar(p.getAsString());
				}
				rec.put(key, val);
				if (dst != null) {
					dst.put(full, val);
				}
			} else {
				var val = Value.categorical(v.toString());
				rec.put(key, val);
				if (dst != null) {
					dst.put(full, val);
				}
			}
		}
		return rec;
	}

	static Value parseJsonScalar(String s) {
		if (Texts.isUnknownKeyword(s)) {
			return Value.missing();
		}
		var nu = Texts.splitNumberUnit(s);
		if (nu != null) {
			var unitType = "";
			if (!nu.unit().isEmpty()) {
				var ut = Units.unitTypeOfUnit(nu.unit());
				if (ut != null) {
					unitType = ut;
				}
			}
			return Value.number(nu.num(), nu.unit(), unitType);
		}
		return parseAnyScalar(s);
	}

	static Value parseAnyScalar(String s) {
		switch (s.trim().toLowerCase()) {
			case "true" -> {
				return Value.bool(true);
			}
			case "false" -> {
				return Value.bool(false);
			}
			default -> {
				return Value.categorical(s.trim());
			}
		}
	}

	/**
	 * Flattens a record value into dst, prefixing each relative field
	 * path with prefix. Used by table loaders to expose JSON record cells
	 * under their full concept paths.
	 */
	public static void recordFieldValues(Value v, String prefix, Map<String, Value> dst) {
		for (var e : v.rec().entrySet()) {
			var full = prefix + "." + e.getKey();
			dst.put(full, e.getValue());
			if (e.getValue().kind() == ValueKind.RECORD) {
				recordFieldValues(e.getValue(), full, dst);
			}
		}
	}

	/**
	 * Refines a parsed value with taxonomy knowledge: numeric values
	 * without a unit type inherit the unit type of their concept.
	 */
	public static Value withConcept(Value v, Concept c) {
		return switch (v.kind()) {
			case NUMBER, RANGE -> {
				var unitType = v.unitType();
				var unit = v.unit();
				if (unitType.isEmpty() && !c.unitType.isEmpty() && unit.isEmpty()) {
					unitType = c.unitType;
				}
				if (!unit.isEmpty() && unitType.isEmpty()) {
					var ut = Units.unitTypeOfUnit(unit);
					unitType = ut != null ? ut : c.unitType;
				}
				if (v.kind() == ValueKind.NUMBER) {
					yield Value.number(v.num(), unit, unitType);
				}
				yield Value.range(v.min(), v.max(), unit, unitType);
			}
			case LIST -> {
				var items = new java.util.ArrayList<Value>(v.items().size());
				for (var item : v.items()) {
					items.add(withConcept(item, c));
				}
				yield Value.list(items);
			}
			default -> v;
		};
	}

	/** Serializes the fields of a record value as a JSON object, keys sorted. */
	static String flattenJson(Value v) {
		return writeJson(v.rec());
	}

	private static String writeJson(Map<String, Value> rec) {
		var sb = new StringBuilder("{");
		var first = true;
		for (var e : new TreeMap<>(rec).entrySet()) {
			if (!first) {
				sb.append(",");
			}
			first = false;
			sb.append(jsonString(e.getKey())).append(":").append(writeJsonValue(e.getValue()));
		}
		return sb.append("}").toString();
	}

	private static String writeJsonValue(Value v) {
		return switch (v.kind()) {
			case RECORD -> writeJson(v.rec());
			case NUMBER -> Texts.formatNumber(v.num());
			case BOOL -> Boolean.toString(v.bool());
			case MISSING -> "null";
			default -> jsonString(v.str());
		};
	}

	static String jsonString(String s) {
		var sb = new StringBuilder("\"");
		for (var i = 0; i < s.length(); i++) {
			var c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append("\"").toString();
	}
}
