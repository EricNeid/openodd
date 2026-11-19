// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The collection of concepts of a loaded model.
 */
public final class Taxonomy {

	/** Concepts keyed by their dotted IDs. */
	public final Map<String, Concept> concepts = new LinkedHashMap<>();
	/** The top-level concepts in declaration order. */
	public final Map<String, Concept> roots = new LinkedHashMap<>();

	/** Returns the concept with the given exact ID, or {@code null}. */
	public Concept concept(String id) {
		return concepts.get(id);
	}
}
