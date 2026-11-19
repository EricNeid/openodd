// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

/**
 * The kinds of values a COD/OD record can hold for a taxonomy concept.
 */
public enum ValueKind {
	/** Unknown, absent or empty; interpreted as "unknown" per Section 8.3.2.1. */
	MISSING("missing"),
	/** A numeric value with an optional unit symbol. */
	NUMBER("number"),
	/** A boolean value. */
	BOOL("boolean"),
	/** A categorical literal value. */
	CATEGORICAL("categorical"),
	/** A set of alternative values modeling epistemic uncertainty. */
	LIST("list"),
	/** A numeric interval modeling aleatoric uncertainty. */
	RANGE("range"),
	/** A serialized user-defined structure (JSON). */
	RECORD("record");

	private final String label;

	ValueKind(String label) {
		this.label = label;
	}

	/** The name used in error messages. */
	public String label() {
		return label;
	}

	@Override
	public String toString() {
		return label;
	}
}
