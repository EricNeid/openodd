// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.eval;

/**
 * Selects how uncertain values are evaluated. Aleatoric ranges (for
 * example "[10.6 .. 10.7] m") and epistemic lists (for example
 * "[cyclist, motorcycle]") admit several possible points.
 */
public enum UncertaintyMode {
	/**
	 * Evaluates an expression over an uncertain value as true when it
	 * holds for at least one possible point of the value. This is the
	 * default.
	 */
	POSSIBILITY,
	/**
	 * Evaluates an expression over an uncertain value as true only when
	 * it holds for every possible point of the value.
	 */
	CERTAINTY
}
