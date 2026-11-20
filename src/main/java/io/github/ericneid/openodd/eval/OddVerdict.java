// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.eval;

/**
 * The three-valued result of checking a record against an ODD. A
 * condition over a concept for which the record carries no value is
 * UNKNOWN; verdicts combine under Kleene logic, so a record is INSIDE
 * only when every condition is provably true. The boolean evaluation
 * API collapses UNKNOWN to not-inside.
 */
public enum OddVerdict {

	/** Every condition holds: the record lies inside the ODD. */
	INSIDE,
	/** A condition is violated: the record lies outside the ODD. */
	OUTSIDE,
	/** A condition cannot be judged because the record carries no value
	 * for the concept it references. */
	UNKNOWN;

	/** The collapse of the boolean API: only INSIDE is true. */
	boolean toBoolean() {
		return this == INSIDE;
	}
}
