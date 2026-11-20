// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import io.github.ericneid.openodd.internal.Texts;

import java.util.Objects;

/**
 * An Equal expression: true when the concept value equals the specified
 * value. An {@link ScalarKind#UNKNOWN} value compares true against
 * missing record values only.
 *
 * @param kind the kind of the compared value
 * @param num  the numeric value, for {@link ScalarKind#NUMBER}
 * @param unit the unit of the numeric value, "" for dimensionless
 * @param bool the boolean value, for {@link ScalarKind#BOOL}
 * @param str  the literal, for {@link ScalarKind#LITERAL}
 */
public record EqualExpr(ScalarKind kind, double num, String unit, boolean bool, String str) implements Expression {

	/**
	 * A numeric equality.
	 *
	 * @param num  the numeric value to compare against
	 * @param unit the unit of the value, "" for dimensionless
	 * @return the equality expression
	 */
	public static EqualExpr number(double num, String unit) {
		return new EqualExpr(ScalarKind.NUMBER, num, unit, false, "");
	}

	/**
	 * A boolean equality.
	 *
	 * @param b the boolean value to compare against
	 * @return the equality expression
	 */
	public static EqualExpr boolValue(boolean b) {
		return new EqualExpr(ScalarKind.BOOL, 0, "", b, "");
	}

	/**
	 * A literal equality.
	 *
	 * @param s the literal to compare against
	 * @return the equality expression
	 */
	public static EqualExpr literal(String s) {
		return new EqualExpr(ScalarKind.LITERAL, 0, "", false, s);
	}

	/**
	 * The unknown keyword.
	 *
	 * @return the equality expression
	 */
	public static EqualExpr unknown() {
		return new EqualExpr(ScalarKind.UNKNOWN, 0, "", false, "");
	}

	/** Normalizes null components to defaults. */
	public EqualExpr {
		Objects.requireNonNull(kind);
		unit = unit == null ? "" : unit;
		str = str == null ? "" : str;
	}

	@Override
	public String toString() {
		return switch (kind) {
			case NUMBER -> Texts.formatNumber(num) + Texts.suffixUnit(unit);
			case BOOL -> Boolean.toString(bool);
			case UNKNOWN -> "unknown";
			case LITERAL -> str;
		};
	}
}
