// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import java.util.Objects;

/**
 * A {@code <NumericTerm>} per Code 88 of the standard: a factor, optionally
 * combined with exactly one other factor by multiplication or division.
 */
public record Term(Factor left, char op, Factor right) {

	/** A single-factor term. */
	public static Term of(Factor left) {
		return new Term(left, (char) 0, null);
	}

	public Term {
		Objects.requireNonNull(left);
		if (op == 0) {
			right = null;
		} else {
			Objects.requireNonNull(right);
		}
	}

	@Override
	public String toString() {
		if (op == 0) {
			return left.toString();
		}
		return left + String.valueOf(op) + right;
	}
}
