// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

/**
 * A boolean expression over a taxonomy concept; the concrete types match
 * the expression subclasses of Section 6.4.8 of the standard.
 */
public sealed interface Expression permits EqualExpr, BoundExpr, RangeExpr, ListExpr {

	/** Renders the expression in the YAML notation of the standard. */
	@Override
	String toString();
}
