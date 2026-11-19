// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

/**
 * Classifies a taxonomy concept.
 */
public enum ConceptKind {
	/** A container or user-defined record type; records aggregate child concepts. */
	RECORD("record"),
	/** A leaf concept with a primitive type, for example "wind_speed: float velocity". */
	ATTRIBUTE("attribute"),
	/** A leaf concept with a list of categorical literals. */
	CATEGORICAL("categorical"),
	/** An attribute whose type is a user-defined record or another categorical. */
	TYPE_REFERENCE("type reference");

	private final String label;

	ConceptKind(String label) {
		this.label = label;
	}

	/** The human readable name used in error messages. */
	public String label() {
		return label;
	}
}
