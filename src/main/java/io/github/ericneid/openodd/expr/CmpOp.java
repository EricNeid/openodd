// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

/**
 * A comparison operator of a bound expression.
 */
public enum CmpOp {
	/** The (implicit) equality operator. */
	NONE(""),
	/** "&lt;". */
	LESS("<"),
	/** "&lt;=". */
	LESS_EQUAL("<="),
	/** "&gt;". */
	GREATER(">"),
	/** "&gt;=". */
	GREATER_EQUAL(">=");

	private final String symbol;

	CmpOp(String symbol) {
		this.symbol = symbol;
	}

	/** The operator symbol of the YAML notation, "" for equality. */
	public String symbol() {
		return symbol;
	}

	@Override
	public String toString() {
		return symbol;
	}
}
