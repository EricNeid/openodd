// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A taxonomy concept node.
 *
 * <p>Concepts are built by the YAML parser; the fields are public for
 * convenient construction but are not intended to be modified after
 * loading.</p>
 */
public final class Concept {

	/** Primitive type names accepted for attributes (Section 6.2.4.4). */
	public static final String PRIM_BOOL = "boolean";
	static final String PRIM_INT = "integer";
	static final String PRIM_LONG = "long";
	static final String PRIM_FLOAT = "float";
	static final String PRIM_DOUBLE = "double";
	/** String-like record reference used for geo-fenced areas. */
	public static final String PRIM_SHAPEFILE = "shapefile";

	/** The full dotted path of the concept. */
	public final String id;
	/** The leaf segment of the ID (without "$" for parameters). */
	public final String name;
	/** Whether the concept is a parameter, written with a leading "$". */
	public final boolean parameter;
	/** The concept kind. */
	public ConceptKind kind;
	/** The primitive type of an attribute, for example "float". */
	public String primType = "";
	/** The unit type of a numeric attribute, for example "velocity". */
	public String unitType = "";
	/** The referenced concept ID for {@link ConceptKind#TYPE_REFERENCE}. */
	public String ref = "";
	/** The categorical literals of a categorical concept. */
	public final List<Literal> literals = new ArrayList<>();
	/** Child concepts of a record, keyed by leaf name. */
	public final Map<String, Concept> children = new LinkedHashMap<>();
	/** Declaration order of the children. */
	public final List<String> childOrder = new ArrayList<>();
	/** The enclosing record, or {@code null} at the top level. */
	public final Concept parent;
	/** Optional key/value pairs attached to the concept. */
	public Map<String, String> metadata;
	/** Optional free-form description. */
	public String description = "";

	/**
	 * Creates a concept with the given ID, name, parameter flag and parent.
	 *
	 * @param id        the full dotted path of the concept
	 * @param name      the leaf segment of the ID
	 * @param parameter whether the concept is a parameter
	 * @param parent    the enclosing record, null at the top level
	 */
	public Concept(String id, String name, boolean parameter, Concept parent) {
		this.id = id;
		this.name = name;
		this.parameter = parameter;
		this.parent = parent;
	}

	/**
	 * Reports whether the type name is a primitive type of the standard.
	 *
	 * @param s the type name
	 * @return whether the name is a primitive type
	 */
	public static boolean isPrimitiveType(String s) {
		return switch (s) {
			case PRIM_BOOL, PRIM_INT, PRIM_LONG, PRIM_FLOAT, PRIM_DOUBLE, PRIM_SHAPEFILE -> true;
			default -> false;
		};
	}

	/**
	 * Reports whether the type name is numeric.
	 *
	 * @param s the type name
	 * @return whether the name is numeric
	 */
	public static boolean isNumericPrimitive(String s) {
		return switch (s) {
			case PRIM_INT, PRIM_LONG, PRIM_FLOAT, PRIM_DOUBLE -> true;
			default -> false;
		};
	}

	/**
	 * Returns the literal names in declaration order.
	 *
	 * @return the literal names
	 */
	public List<String> leafNames() {
		var names = new ArrayList<String>(literals.size());
		for (var l : literals) {
			names.add(l.name);
		}
		return names;
	}

	/**
	 * Returns the literal with the given name, or {@code null}.
	 *
	 * @param name the literal name
	 * @return the literal, or {@code null} when absent
	 */
	public Literal literal(String name) {
		for (var l : literals) {
			if (l.name.equals(name)) {
				return l;
			}
		}
		return null;
	}

	/**
	 * Returns a human readable description for error messages.
	 *
	 * @return the description of the concept
	 */
	public String describe() {
		return switch (kind) {
			case ATTRIBUTE -> unitType.isEmpty()
					? "%s (%s)".formatted(id, primType)
					: "%s (%s %s)".formatted(id, primType, unitType);
			case CATEGORICAL -> "%s (categorical: %s)".formatted(id, String.join(", ", leafNames()));
			case TYPE_REFERENCE -> "%s (%s)".formatted(id, ref);
			case RECORD -> id + " (record)";
		};
	}

	@Override
	public String toString() {
		return describe();
	}
}
