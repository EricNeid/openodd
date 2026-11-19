// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

/**
 * Classifies the value of an {@link EqualExpr}.
 */
public enum ScalarKind {
	/** A numeric value with an optional unit. */
	NUMBER,
	/** A boolean value; also used for module and label references. */
	BOOL,
	/** A categorical literal or plain string value. */
	LITERAL,
	/** The missing-value keyword. */
	UNKNOWN
}
