// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

import io.github.ericneid.openodd.model.ConceptIndex;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A single COD/OD row: the measured values of taxonomy concepts at a
 * specific temporal and spatial extent. Values are keyed by concept ID;
 * loaders produce canonical IDs once a taxonomy is attached.
 */
public final class Record {

	private final Map<String, Value> values;
	/** The TEMPORAL_EXTENT column (timestamp or interval). */
	private String temporal = "";
	/** The SPATIAL_EXTENT column (position or shape reference). */
	private String spatial = "";

	/** Creates an empty record. */
	public Record() {
		this.values = new LinkedHashMap<>();
	}

	/** Creates a record with the given values. */
	public Record(Map<String, Value> values) {
		this.values = new LinkedHashMap<>(values);
	}

	/** The concept values of the record; mutable for construction. */
	public Map<String, Value> values() {
		return values;
	}

	public String temporal() {
		return temporal;
	}

	public Record temporal(String temporal) {
		this.temporal = temporal == null ? "" : temporal;
		return this;
	}

	public String spatial() {
		return spatial;
	}

	public Record spatial(String spatial) {
		this.spatial = spatial == null ? "" : spatial;
		return this;
	}

	/**
	 * Returns the value for a concept ID. The optional is empty when the
	 * key is absent; a present key with an unknown value yields
	 * {@link Value#missing()}.
	 */
	public Optional<Value> value(String conceptId) {
		var v = values.get(conceptId);
		if (v != null) {
			return Optional.of(v);
		}
		for (var key : ConceptIndex.recordKeyVariants(conceptId)) {
			v = values.get(key);
			if (v != null) {
				return Optional.of(v);
			}
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		var keys = values.keySet().stream().sorted().toList();
		return "record" + keys;
	}
}
