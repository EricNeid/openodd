// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import io.github.ericneid.openodd.internal.Texts;

import java.util.Objects;

/**
 * A Range expression such as "[2 .. 10] mm/h".
 *
 * @param min  the lower bound term
 * @param max  the upper bound term
 * @param unit the unit of the bounds, "" for dimensionless
 */
public record RangeExpr(Term min, Term max, String unit) implements Expression {

	/** Rejects missing bounds and normalizes the unit. */
	public RangeExpr {
		Objects.requireNonNull(min);
		Objects.requireNonNull(max);
		unit = unit == null ? "" : unit;
	}

	@Override
	public String toString() {
		return "[" + min + " .. " + max + "]" + Texts.suffixUnit(unit);
	}
}
