// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import io.github.ericneid.openodd.internal.Texts;

import java.util.Objects;

/**
 * An UpperBound or LowerBound expression such as "&lt; 40 km/h" or
 * "&gt;= medium_rain". The term may reference taxonomy attributes or
 * parameters, for example "1.75*ego_width".
 *
 * @param op   the comparison operator
 * @param term the compared term
 * @param unit the unit of the bound, "" for dimensionless
 */
public record BoundExpr(CmpOp op, Term term, String unit) implements Expression {

	/** Rejects missing components and normalizes the unit. */
	public BoundExpr {
		Objects.requireNonNull(op);
		Objects.requireNonNull(term);
		unit = unit == null ? "" : unit;
	}

	@Override
	public String toString() {
		return op.symbol() + " " + term + Texts.suffixUnit(unit);
	}
}
