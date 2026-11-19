// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.value.Record;
import io.github.ericneid.openodd.value.Value;
import io.github.ericneid.openodd.value.ValueKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reference resolution over a taxonomy. Resolves the reference spellings
 * used in conditions, terms and COD/OD column headers: full dotted IDs,
 * unambiguous suffixes of dotted IDs and leaf names. References starting
 * with "$" name parameters; the "$" is optional when resolving.
 */
public final class ConceptIndex {

	/** Guards against cycles when expanding user-defined record types. */
	private static final int MAX_TYPE_REF_DEPTH = 64;

	private final Taxonomy taxonomy;
	/** Synthesized concepts from type reference expansion. */
	private final Map<String, Concept> expanded = new TreeMap<>();
	/** Union of taxonomy and expanded concepts. */
	private final Map<String, Concept> all = new TreeMap<>();

	/** Builds a resolution index over the taxonomy, expanding type references. */
	public ConceptIndex(Taxonomy taxonomy) {
		this.taxonomy = taxonomy;
		all.putAll(taxonomy.concepts);
		expandAll();
	}

	private void expandAll() {
		for (var root : taxonomy.roots.values()) {
			expandConcept(root, "", 0);
		}
	}

	private void expandConcept(Concept c, String prefix, int depth) {
		if (depth > MAX_TYPE_REF_DEPTH) {
			throw new OpenOddException(
					"expanding type references exceeded depth %d; records must be acyclic".formatted(
							MAX_TYPE_REF_DEPTH));
		}
		switch (c.kind) {
			case RECORD -> {
				for (var child : sortedChildren(c)) {
					expandConcept(child, joinId(prefix, c.id), depth + 1);
				}
			}
			case TYPE_REFERENCE -> {
				Concept target;
				try {
					target = resolve(c.ref);
				} catch (OpenOddException e) {
					throw new OpenOddException(
							"concept \"%s\" references unknown type \"%s\"".formatted(c.id, c.ref));
				}
				if (target.kind != ConceptKind.RECORD && target.kind != ConceptKind.CATEGORICAL) {
					throw new OpenOddException(
							"concept \"%s\" references \"%s\" which is neither a record nor a categorical".formatted(
									c.id, c.ref));
				}
				// Expand the referenced record's fields under this
				// concept's ID so that conditions such as
				// "wind_trajectory.angle" resolve.
				if (target.kind == ConceptKind.RECORD) {
					for (var child : sortedChildren(target)) {
						expandSynthetic(child, c.id, 0);
					}
				}
			}
			default -> {
			}
		}
	}

	/** Creates field concepts under a type-referencing concept. */
	private void expandSynthetic(Concept c, String ownerId, int depth) {
		if (depth > MAX_TYPE_REF_DEPTH) {
			throw new OpenOddException(
					"expanding type references exceeded depth %d; records must be acyclic".formatted(
							MAX_TYPE_REF_DEPTH));
		}
		var fieldId = ownerId + "." + c.name;
		if (expanded.containsKey(fieldId) || taxonomy.concepts.containsKey(fieldId)) {
			return;
		}
		var clone = new Concept(fieldId, c.name, c.parameter, null);
		clone.kind = c.kind;
		clone.primType = c.primType;
		clone.unitType = c.unitType;
		clone.ref = c.ref;
		clone.literals.addAll(c.literals);
		clone.children.putAll(c.children);
		clone.childOrder.addAll(c.childOrder);
		expanded.put(fieldId, clone);
		all.put(fieldId, clone);
		if (c.kind == ConceptKind.TYPE_REFERENCE) {
			try {
				var target = resolve(c.ref);
				if (target.kind == ConceptKind.RECORD) {
					for (var child : sortedChildren(target)) {
						expandSynthetic(child, fieldId, depth + 1);
					}
				}
			} catch (OpenOddException ignored) {
				// Unresolvable references are reported by the validator.
			}
		}
		if (c.kind == ConceptKind.RECORD) {
			for (var child : sortedChildren(c)) {
				expandSynthetic(child, fieldId, depth + 1);
			}
		}
	}

	static List<Concept> sortedChildren(Concept c) {
		return new ArrayList<>(new TreeMap<>(c.children).values());
	}

	static String joinId(String prefix, String id) {
		return prefix.isEmpty() ? id : prefix + "." + id;
	}

	/** Returns the concept with the exact ID, including synthesized expansions. */
	public Concept concept(String id) {
		var c = expanded.get(id);
		if (c != null) {
			return c;
		}
		return taxonomy.concepts.get(id);
	}

	/**
	 * Removes the "$" prefix of a parameter marker; strips a leading "$"
	 * of the whole ID and of its last segment, so that "t.$expected_type"
	 * and "t.expected_type" compare equal.
	 */
	static String stripParameterMarker(String id) {
		var i = id.lastIndexOf('.');
		if (i >= 0) {
			var rest = id.substring(i + 1);
			if (rest.startsWith("$")) {
				return id.substring(0, i + 1) + rest.substring(1);
			}
			return id;
		}
		if (id.startsWith("$")) {
			return id.substring(1);
		}
		return id;
	}

	/**
	 * Resolves a reference as written in conditions, terms or table
	 * column headers. Resolution rules, in order:
	 *
	 * <ol>
	 *   <li>exact ID match (with and without a "$" parameter prefix),</li>
	 *   <li>unique concept whose ID ends in ".&lt;reference&gt;",</li>
	 *   <li>otherwise an error naming the candidates if ambiguous.</li>
	 * </ol>
	 */
	public Concept resolve(String ref) {
		ref = ref.trim();
		if (ref.isEmpty()) {
			throw new OpenOddException("empty concept reference");
		}
		var norm = ref.startsWith("$") ? ref.substring(1) : ref;
		var allConcepts = allConcepts();

		// Exact match, tolerating a "$" parameter marker on either side.
		for (var e : allConcepts.entrySet()) {
			if (e.getKey().equals(ref) || e.getKey().equals(norm)) {
				return e.getValue();
			}
		}
		for (var e : allConcepts.entrySet()) {
			if (stripParameterMarker(e.getKey()).equals(ref)
					|| stripParameterMarker(e.getKey()).equals(norm)) {
				return e.getValue();
			}
		}

		// Unique suffix match on the dotted ID.
		var candidates = new ArrayList<Concept>();
		var candidateIds = new ArrayList<String>();
		for (var e : allConcepts.entrySet()) {
			var cid = stripParameterMarker(e.getKey());
			if (cid.endsWith("." + norm)) {
				candidates.add(e.getValue());
				candidateIds.add(e.getKey());
			}
		}
		switch (candidates.size()) {
			case 1 -> {
				return candidates.get(0);
			}
			case 0 -> throw new OpenOddException("unknown concept \"%s\"".formatted(ref));
			default -> {
				java.util.Collections.sort(candidateIds);
				throw new OpenOddException("ambiguous concept reference \"%s\": matches %s".formatted(
						ref, String.join(", ", candidateIds)));
			}
		}
	}

	/**
	 * Resolves a table column name against the taxonomy. Column headers
	 * are frequently written in upper case while taxonomy IDs are lower
	 * case; the lookup therefore falls back to a case-insensitive match
	 * on the last ID segment.
	 */
	public Concept resolveColumn(String name) {
		name = name.trim();
		if (name.isEmpty()) {
			throw new OpenOddException("empty column name");
		}
		try {
			return resolve(name);
		} catch (OpenOddException ignored) {
			// fall through
		}
		var lower = name.toLowerCase();
		try {
			return resolve(lower);
		} catch (OpenOddException ignored) {
			// fall through
		}
		// Case-insensitive exact and suffix match.
		var allConcepts = allConcepts();
		var candidates = new ArrayList<Concept>();
		var ids = new ArrayList<String>();
		for (var e : allConcepts.entrySet()) {
			var cid = stripParameterMarker(e.getKey());
			if (e.getKey().equalsIgnoreCase(name) || cid.equalsIgnoreCase(name)) {
				return e.getValue();
			}
			if (cid.toLowerCase().endsWith("." + lower)) {
				candidates.add(e.getValue());
				ids.add(e.getKey());
			}
		}
		switch (candidates.size()) {
			case 1 -> {
				return candidates.get(0);
			}
			case 0 -> throw new OpenOddException("unknown concept \"%s\"".formatted(name));
			default -> throw new OpenOddException("ambiguous column \"%s\": matches %s".formatted(
					name, String.join(", ", ids)));
		}
	}

	private Map<String, Concept> allConcepts() {
		return all;
	}

	/**
	 * Canonicalizes the keys of a record map so that evaluation can look
	 * up values by canonical concept ID. Keys that cannot be resolved are
	 * kept verbatim.
	 */
	public Map<String, Value> normalizeRecordKeys(Map<String, Value> values) {
		var out = new LinkedHashMap<String, Value>(values.size());
		for (var e : values.entrySet()) {
			out.put(canonicalKey(e.getKey()), e.getValue());
		}
		return out;
	}

	private String canonicalKey(String key) {
		try {
			return resolve(key).id;
		} catch (OpenOddException e) {
			return key;
		}
	}

	/**
	 * Resolves the record value for a concept; handles canonical keys,
	 * parameters (with and without "$") and dotted paths into record
	 * values.
	 */
	public RecordHit valueFor(Concept c, Record rec) {
		// Direct canonical hit.
		for (var key : recordKeyVariants(c.id)) {
			var v = rec.values().get(key);
			if (v != null) {
				return new RecordHit(v, true);
			}
		}
		// Dotted reference into a record-valued concept.
		var i = c.id.lastIndexOf('.');
		if (i > 0) {
			var parent = c.id.substring(0, i);
			var field = c.id.substring(i + 1);
			for (var pk : recordKeyVariants(parent)) {
				var pv = rec.values().get(pk);
				if (pv != null && pv.kind() == ValueKind.RECORD) {
					var fv = pv.rec().get(field);
					if (fv != null) {
						return new RecordHit(fv, true);
					}
					// Fully flattened keys such as "wind_trajectory.angle".
					fv = pv.rec().get(c.id);
					if (fv != null) {
						return new RecordHit(fv, true);
					}
				}
			}
			var v = rec.values().get(c.id);
			if (v != null) {
				return new RecordHit(v, true);
			}
		}
		return new RecordHit(Value.missing(), false);
	}

	/** Result of a record lookup: the value and whether it was present. */
	public record RecordHit(Value value, boolean present) {
	}

	/**
	 * Returns the lookup keys for a concept ID: the ID itself plus the
	 * spelling with, respectively without, a "$" marker on the last
	 * segment.
	 */
	public static List<String> recordKeyVariants(String id) {
		var i = id.lastIndexOf('.');
		if (i >= 0) {
			var leaf = id.substring(i + 1);
			if (leaf.startsWith("$")) {
				return List.of(id, id.substring(0, i + 1) + leaf.substring(1));
			}
			return List.of(id, id.substring(0, i + 1) + "$" + leaf);
		}
		if (id.startsWith("$")) {
			return List.of(id, id.substring(1));
		}
		return List.of(id, "$" + id);
	}
}
