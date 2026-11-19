// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A categorical literal of a categorical concept. Literals may carry
 * defining constraints over other concepts; numeric range constraints
 * induce an order between the literals.
 */
public final class Literal {

	/** The literal name. */
	public final String name;
	/** The defining constraints; multiple constraints combine conjunctively. */
	public final List<Constraint> constraints = new ArrayList<>();

	public Literal(String name) {
		this.name = name;
	}
}
