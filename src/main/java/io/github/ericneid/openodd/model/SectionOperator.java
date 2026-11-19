// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

/**
 * Combines the conditions of a section.
 */
public enum SectionOperator {
	/** Requires all conditions to hold. */
	AND,
	/** Requires at least one condition to hold. */
	OR;

	@Override
	public String toString() {
		return name();
	}
}
