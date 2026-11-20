// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

import io.github.ericneid.openodd.internal.Texts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single taxonomy concept value of a COD or OD record.
 */
public final class Value {

	private final ValueKind kind;
	private final double num;
	private final String unit;
	private final String unitType;
	private final boolean bool;
	private final String str;
	private final List<Value> items;
	private final double min;
	private final double max;
	private final Map<String, Value> rec;

	private Value(ValueKind kind, double num, String unit, String unitType, boolean bool,
				  String str, List<Value> items, double min, double max, Map<String, Value> rec) {
		this.kind = Objects.requireNonNull(kind);
		this.num = num;
		this.unit = unit == null ? "" : unit;
		this.unitType = unitType == null ? "" : unitType;
		this.bool = bool;
		this.str = str == null ? "" : str;
		this.items = items == null ? List.of() : List.copyOf(items);
		this.min = min;
		this.max = max;
		this.rec = rec == null ? Map.of() : Map.copyOf(rec);
	}

	/**
	 * The missing/unknown value.
	 *
	 * @return the missing value
	 */
	public static Value missing() {
		return new Value(ValueKind.MISSING, 0, "", "", false, "", null, 0, 0, null);
	}

	/**
	 * A numeric value with an optional unit and unit type.
	 *
	 * @param num      the numeric value
	 * @param unit     the unit symbol, "" for dimensionless
	 * @param unitType the unit type ID, "" for untyped
	 * @return the numeric value
	 */
	public static Value number(double num, String unit, String unitType) {
		return new Value(ValueKind.NUMBER, num, unit, unitType, false, "", null, 0, 0, null);
	}

	/**
	 * A plain numeric value.
	 *
	 * @param num the numeric value
	 * @return the numeric value
	 */
	public static Value number(double num) {
		return number(num, "", "");
	}

	/**
	 * A boolean value.
	 *
	 * @param b the boolean value
	 * @return the boolean value
	 */
	public static Value bool(boolean b) {
		return new Value(ValueKind.BOOL, 0, "", "", b, "", null, 0, 0, null);
	}

	/**
	 * A categorical literal value.
	 *
	 * @param s the literal name
	 * @return the categorical value
	 */
	public static Value categorical(String s) {
		return new Value(ValueKind.CATEGORICAL, 0, "", "", false, s, null, 0, 0, null);
	}

	/**
	 * An epistemic list of alternatives.
	 *
	 * @param items the alternative values
	 * @return the list value
	 */
	public static Value list(List<Value> items) {
		return new Value(ValueKind.LIST, 0, "", "", false, "", items, 0, 0, null);
	}

	/**
	 * An aleatoric range with an optional unit and unit type.
	 *
	 * @param min      the lower bound
	 * @param max      the upper bound
	 * @param unit     the unit symbol, "" for dimensionless
	 * @param unitType the unit type ID, "" for untyped
	 * @return the range value
	 */
	public static Value range(double min, double max, String unit, String unitType) {
		return new Value(ValueKind.RANGE, min, unit, unitType, false, "", null, min, max, null);
	}

	/**
	 * A record structure keyed by paths relative to the record root.
	 *
	 * @param rec the field values keyed by their relative paths
	 * @return the record value
	 */
	public static Value record(Map<String, Value> rec) {
		return new Value(ValueKind.RECORD, 0, "", "", false, "", null, 0, 0, rec);
	}

	/**
	 * Creates a copy of a range value with new bounds.
	 *
	 * @param min the lower bound
	 * @param max the upper bound
	 * @return the range value
	 */
	public static Value rangeOf(double min, double max) {
		return new Value(ValueKind.RANGE, min, "", "", false, "", null, min, max, null);
	}

	/**
	 * The kind of the value.
	 *
	 * @return the kind
	 */
	public ValueKind kind() {
		return kind;
	}

	/**
	 * The numeric value of a NUMBER value.
	 *
	 * @return the number
	 */
	public double num() {
		return num;
	}

	/**
	 * The unit symbol of a NUMBER or RANGE value.
	 *
	 * @return the unit symbol
	 */
	public String unit() {
		return unit;
	}

	/**
	 * The unit type ID of a NUMBER or RANGE value.
	 *
	 * @return the unit type ID
	 */
	public String unitType() {
		return unitType;
	}

	/**
	 * The boolean value of a BOOL value.
	 *
	 * @return the boolean
	 */
	public boolean bool() {
		return bool;
	}

	/**
	 * The literal name of a CATEGORICAL value.
	 *
	 * @return the literal name
	 */
	public String str() {
		return str;
	}

	/**
	 * The alternatives of a LIST value.
	 *
	 * @return the alternative values
	 */
	public List<Value> items() {
		return items;
	}

	/**
	 * The lower bound of a RANGE value.
	 *
	 * @return the lower bound
	 */
	public double min() {
		return min;
	}

	/**
	 * The upper bound of a RANGE value.
	 *
	 * @return the upper bound
	 */
	public double max() {
		return max;
	}

	/**
	 * The fields of a RECORD value.
	 *
	 * @return the fields keyed by relative path
	 */
	public Map<String, Value> rec() {
		return rec;
	}

	/**
	 * Reports whether the value is missing or explicitly unknown.
	 *
	 * @return whether the value is unknown
	 */
	public boolean isUnknown() {
		return kind == ValueKind.MISSING;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Value other)) {
			return false;
		}
		return kind == other.kind
				&& Double.doubleToLongBits(num) == Double.doubleToLongBits(other.num)
				&& unit.equals(other.unit)
				&& unitType.equals(other.unitType)
				&& bool == other.bool
				&& str.equals(other.str)
				&& items.equals(other.items)
				&& Double.doubleToLongBits(min) == Double.doubleToLongBits(other.min)
				&& Double.doubleToLongBits(max) == Double.doubleToLongBits(other.max)
				&& rec.equals(other.rec);
	}

	@Override
	public int hashCode() {
		return Objects.hash(kind, num, unit, unitType, bool, str, items, min, max, rec);
	}

	@Override
	public String toString() {
		return switch (kind) {
			case MISSING -> "unknown";
			case NUMBER -> Texts.formatNumber(num) + Texts.suffixUnit(unit);
			case BOOL -> Boolean.toString(bool);
			case CATEGORICAL -> str;
			case LIST -> {
				var parts = new ArrayList<String>(items.size());
				for (var item : items) {
					parts.add(item.toString());
				}
				yield "[" + String.join(", ", parts) + "]";
			}
			case RANGE -> "[" + Texts.formatNumber(min) + " .. " + Texts.formatNumber(max) + "]"
					+ Texts.suffixUnit(unit);
			case RECORD -> Values.flattenJson(this);
		};
	}
}
