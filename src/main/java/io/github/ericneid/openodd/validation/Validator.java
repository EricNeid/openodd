// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.validation;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.expr.BoundExpr;
import io.github.ericneid.openodd.expr.EqualExpr;
import io.github.ericneid.openodd.expr.Expression;
import io.github.ericneid.openodd.expr.Factor;
import io.github.ericneid.openodd.expr.ListExpr;
import io.github.ericneid.openodd.expr.RangeExpr;
import io.github.ericneid.openodd.expr.Term;
import io.github.ericneid.openodd.model.Concept;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.model.ConceptKind;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.model.Module;
import io.github.ericneid.openodd.model.Section;
import io.github.ericneid.openodd.model.Taxonomy;
import io.github.ericneid.openodd.value.Units;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks a model against the semantic constraints of the ASAM OpenODD
 * standard: reference resolution, expression and concept type
 * compatibility, unit compatibility, label disjointness and module
 * reference acyclicity. The first problem found is reported through an
 * {@link OpenOddException}.
 */
public final class Validator {

	private final Model model;
	private ConceptIndex idx;

	private Validator(Model model) {
		this.model = model;
	}

	/**
	 * Validates the model; see the class documentation.
	 *
	 * @param m the model to validate
	 */
	public static void validate(Model m) {
		var v = new Validator(m);
		v.taxonomy();
		v.labels();
		v.modules();
	}

	private void taxonomy() {
		idx = new ConceptIndex(model.taxonomy);
		for (var id : sortedConceptIds(model.taxonomy)) {
			var c = model.taxonomy.concepts.get(id);
			switch (c.kind) {
				case ATTRIBUTE -> checkUnitType(c);
				case TYPE_REFERENCE -> {
					try {
						idx.resolve(c.ref);
					} catch (OpenOddException e) {
						throw new OpenOddException(
								"concept \"%s\" references unknown type \"%s\"".formatted(c.id, c.ref), e);
					}
				}
				case CATEGORICAL -> {
					if (c.literals.isEmpty()) {
						throw new OpenOddException("categorical concept \"%s\" has no literals".formatted(c.id));
					}
					var seen = new HashSet<String>();
					for (var lit : c.literals) {
						if (!seen.add(lit.name)) {
							throw new OpenOddException(
									"duplicate literal \"%s\" in categorical \"%s\"".formatted(lit.name, c.id));
						}
						for (var cons : lit.constraints) {
							Concept cc;
							try {
								cc = idx.resolve(cons.ref());
							} catch (OpenOddException e) {
								throw new OpenOddException(
										"literal \"%s\" of \"%s\": %s".formatted(lit.name, c.id, e.getMessage()), e);
							}
							checkExpression(cc, cons.expr(), "literal \"%s\" of \"%s\"".formatted(lit.name, c.id));
						}
					}
				}
				default -> {
				}
			}
		}
	}

	private static List<String> sortedConceptIds(Taxonomy tax) {
		return new ArrayList<>(new TreeMap<>(tax.concepts).keySet());
	}

	/**
	 * Reports whether the unit type of an attribute is usable: unit types
	 * require a numeric primitive type. Unit types unknown to the
	 * registry are open; comparisons within them stay symbolic and
	 * require identical unit symbols.
	 */
	private void checkUnitType(Concept c) {
		if (c.unitType.isEmpty()) {
			return;
		}
		if (!Concept.isNumericPrimitive(c.primType)) {
			throw new OpenOddException(
					"concept \"%s\": unit type \"%s\" requires a numeric primitive type, got \"%s\"".formatted(c.id,
							c.unitType, c.primType));
		}
	}

	private void labels() {
		// Label IDs must be disjoint from module IDs and resolvable
		// taxonomy concept references (Section 6.4.3.5 of the standard);
		// conditions resolve module and label references before concept
		// references, so any overlap would shadow the concept.
		for (var e : model.labels.entrySet()) {
			var label = e.getKey();
			if (label.equalsIgnoreCase("unknown")) {
				throw new OpenOddException("label must not be \"%s\"".formatted(label));
			}
			if (model.modules.containsKey(label)) {
				throw new OpenOddException("label \"%s\" duplicates a module ID".formatted(label));
			}
			if (resolves(label)) {
				throw new OpenOddException("label \"%s\" collides with a taxonomy concept reference".formatted(label));
			}
			for (var id : e.getValue()) {
				if (!model.modules.containsKey(id)) {
					throw new OpenOddException("label \"%s\" refers to unknown module \"%s\"".formatted(label, id));
				}
			}
		}
	}

	private boolean resolves(String ref) {
		try {
			idx.resolve(ref);
			return true;
		} catch (OpenOddException e) {
			return false;
		}
	}

	private void modules() {
		var ids = new ArrayList<String>(new TreeMap<>(model.modules).keySet());
		for (var id : ids) {
			var mod = model.modules.get(id);
			if (id.equalsIgnoreCase("unknown")) {
				throw new OpenOddException("module ID must not be \"%s\"".formatted(id));
			}
			if (resolves(id)) {
				throw new OpenOddException(
						"module ID \"%s\" collides with a taxonomy concept reference".formatted(id));
			}
			if (mod.include == null && mod.exclude == null) {
				continue; // a module may consist of metadata only
			}
			for (var sec : new Section[]{mod.include, mod.exclude}) {
				if (sec == null) {
					continue;
				}
				section(mod, sec);
			}
		}
		// Module reference cycles.
		var seen = new HashMap<String, Integer>(); // 0 unseen, 1 in progress, 2 done
		for (var id : ids) {
			visit(id, seen);
		}
	}

	private void visit(String id, Map<String, Integer> seen) {
		switch (seen.getOrDefault(id, 0)) {
			case 1 -> throw new OpenOddException("cyclic module reference through \"%s\"".formatted(id));
			case 2 -> {
				return;
			}
			default -> {
			}
		}
		seen.put(id, 1);
		var mod = model.modules.get(id);
		for (var sec : new Section[]{mod.include, mod.exclude}) {
			if (sec == null) {
				continue;
			}
			for (var ref : sectionModuleRefs(sec)) {
				visit(ref, seen);
			}
		}
		seen.put(id, 2);
	}

	/** Lists module IDs referenced by a section, directly or through labels. */
	private List<String> sectionModuleRefs(Section sec) {
		var refs = new ArrayList<String>();
		for (var cond : sec.conditions) {
			if (model.modules.containsKey(cond.ref)) {
				refs.add(cond.ref);
				continue;
			}
			var mods = model.labels.get(cond.ref);
			if (mods != null) {
				refs.addAll(mods);
			}
		}
		for (var sub : sec.subsections) {
			refs.addAll(sectionModuleRefs(sub));
		}
		return refs;
	}

	private void section(Module mod, Section sec) {
		for (var cond : sec.conditions) {
			var where = "module \"%s\", condition \"%s\"".formatted(mod.id, cond.ref);
			if (model.modules.containsKey(cond.ref)) {
				if (!isBoolOperand(cond.expr)) {
					throw new OpenOddException("%s: module references take true or false".formatted(where));
				}
				continue;
			}
			if (model.labels.containsKey(cond.ref)) {
				if (!isBoolOperand(cond.expr)) {
					throw new OpenOddException("%s: label references take true or false".formatted(where));
				}
				continue;
			}
			Concept c;
			try {
				c = idx.resolve(cond.ref);
			} catch (OpenOddException e) {
				throw new OpenOddException("%s: %s".formatted(where, e.getMessage()), e);
			}
			checkExpression(c, cond.expr, where);
		}
		for (var sub : sec.subsections) {
			section(mod, sub);
		}
	}

	static boolean isBoolOperand(Expression expr) {
		if (!(expr instanceof EqualExpr eq)) {
			return false;
		}
		return switch (eq.kind()) {
			case BOOL -> true;
			case LITERAL -> switch (eq.str().toLowerCase()) {
				case "true", "false" -> true;
				default -> false;
			};
			default -> false;
		};
	}

	/** Validates an expression against the concept it constrains. */
	private void checkExpression(Concept c, Expression expr, String where) {
		// Resolve type references to their targets.
		while (c.kind == ConceptKind.TYPE_REFERENCE) {
			Concept target;
			try {
				target = idx.resolve(c.ref);
			} catch (OpenOddException e) {
				throw new OpenOddException(
						"%s: concept \"%s\" references unknown type \"%s\"".formatted(where, c.id, c.ref), e);
			}
			c = target;
		}
		if (expr instanceof EqualExpr ex) {
			checkEqual(c, ex, where);
		} else if (expr instanceof BoundExpr ex) {
			checkBound(c, ex, where);
		} else if (expr instanceof RangeExpr ex) {
			checkRange(c, ex, where);
		} else if (expr instanceof ListExpr ex) {
			checkList(c, ex, where);
		} else {
			throw new OpenOddException("%s: unsupported expression type".formatted(where));
		}
	}

	private void checkEqual(Concept c, EqualExpr ex, String where) {
		switch (ex.kind()) {
			case UNKNOWN -> {
			}
			case BOOL -> {
				if (c.kind == ConceptKind.CATEGORICAL) {
					if (c.literal("true") == null || c.literal("false") == null) {
						throw new OpenOddException(
								"%s: concept \"%s\" has no true/false literals".formatted(where, c.id));
					}
				} else if (c.kind == ConceptKind.ATTRIBUTE && !Concept.PRIM_BOOL.equals(c.primType)) {
					throw new OpenOddException("%s: concept %s is not boolean".formatted(where, c.describe()));
				}
			}
			case NUMBER -> {
				if (c.kind != ConceptKind.ATTRIBUTE || !Concept.isNumericPrimitive(c.primType)) {
					throw new OpenOddException(
							"%s: numeric equality needs a numeric concept, got %s".formatted(where, c.describe()));
				}
				checkUnit(c, ex.unit(), where);
			}
			default -> {
				if (ex.str().startsWith("$")) {
					return; // parameter reference, resolved per record
				}
				if (ex.str().equals("unknown")) {
					return;
				}
				if (c.kind == ConceptKind.ATTRIBUTE) {
					if (Concept.PRIM_SHAPEFILE.equals(c.primType)) {
						return; // shapefile references compare as strings
					}
					if (Concept.PRIM_BOOL.equals(c.primType)) {
						switch (ex.str().toLowerCase()) {
							case "true", "false" -> {
								return;
							}
							default -> {
							}
						}
						throw new OpenOddException("%s: concept %s is boolean".formatted(where, c.describe()));
					}
					throw new OpenOddException(
							"%s: concept %s is numeric; use a bound, range or numeric equality".formatted(where,
									c.describe()));
				}
				if (c.kind == ConceptKind.CATEGORICAL) {
					// Literals defined by constraints are also acceptable.
					return;
				}
				throw new OpenOddException(
						"%s: concept %s cannot be compared to a literal".formatted(where, c.describe()));
			}
		}
	}

	private void checkBound(Concept c, BoundExpr ex, String where) {
		if (c.kind == ConceptKind.CATEGORICAL) {
			if (c.literal(ex.term().toString()) == null) {
				throw new OpenOddException(
						"%s: \"%s\" is not a literal of \"%s\"".formatted(where, ex.term().toString(), c.id));
			}
			if (!ex.unit().isEmpty()) {
				throw new OpenOddException(
						"%s: categorical bounds take no units (found \"%s\")".formatted(where, ex.unit()));
			}
			return;
		}
		checkTerm(c, ex.term(), ex.unit(), where);
		if (c.kind != ConceptKind.ATTRIBUTE || !Concept.isNumericPrimitive(c.primType)) {
			throw new OpenOddException(
					"%s: bound comparison needs a numeric or categorical concept, got %s".formatted(where,
							c.describe()));
		}
		checkUnit(c, ex.unit(), where);
	}

	private void checkRange(Concept c, RangeExpr ex, String where) {
		if (c.kind == ConceptKind.CATEGORICAL) {
			for (var t : new Term[]{ex.min(), ex.max()}) {
				if (c.literal(t.toString()) == null) {
					throw new OpenOddException(
							"%s: \"%s\" is not a literal of \"%s\"".formatted(where, t.toString(), c.id));
				}
			}
			if (!ex.unit().isEmpty()) {
				throw new OpenOddException(
						"%s: categorical ranges take no units (found \"%s\")".formatted(where, ex.unit()));
			}
			return;
		}
		checkTerm(c, ex.min(), ex.unit(), where);
		checkTerm(c, ex.max(), ex.unit(), where);
		if (c.kind != ConceptKind.ATTRIBUTE || !Concept.isNumericPrimitive(c.primType)) {
			throw new OpenOddException(
					"%s: range comparison needs a numeric or categorical concept, got %s".formatted(where,
							c.describe()));
		}
		checkUnit(c, ex.unit(), where);
	}

	private void checkList(Concept c, ListExpr ex, String where) {
		if (c.kind == ConceptKind.CATEGORICAL) {
			for (var item : ex.items()) {
				if (item.startsWith("$")) {
					continue; // parameter reference, resolved per record
				}
				if (item.equals("unknown")) {
					continue;
				}
				if (c.literal(item) == null) {
					throw new OpenOddException(
							"%s: \"%s\" is not a literal of \"%s\"".formatted(where, item, c.id));
				}
			}
			return;
		}
		throw new OpenOddException(
				"%s: list expressions need a categorical concept, got %s".formatted(where, c.describe()));
	}

	/** Validates the factors of a numeric term and its unit. */
	private void checkTerm(Concept c, Term t, String unit, String where) {
		var factors = new ArrayList<Factor>();
		factors.add(t.left());
		if (t.op() != 0) {
			factors.add(t.right());
		}
		var hasRef = false;
		for (var f : factors) {
			if (!f.isRef()) {
				continue;
			}
			hasRef = true;
			Concept fc;
			try {
				fc = idx.resolve(f.ref());
			} catch (OpenOddException e) {
				throw new OpenOddException(
						"%s: term factor \"%s\": %s".formatted(where, f.ref(), e.getMessage()), e);
			}
			if (fc.kind == ConceptKind.TYPE_REFERENCE) {
				try {
					fc = idx.resolve(fc.ref);
				} catch (OpenOddException ignored) {
					// Unresolvable targets are reported elsewhere.
				}
			}
			if (fc.kind != ConceptKind.ATTRIBUTE || !Concept.isNumericPrimitive(fc.primType)) {
				throw new OpenOddException(
						"%s: term factor \"%s\" is not a numeric attribute (%s)".formatted(where, f.ref(),
								fc.describe()));
			}
		}
		if (hasRef && !unit.isEmpty()) {
			throw new OpenOddException(
					"%s: parameterized expressions take no units (found \"%s\")".formatted(where, unit));
		}
		if (!hasRef) {
			checkUnit(c, unit, where);
		}
	}

	/** Validates that an expression unit belongs to the concept's unit type. */
	private void checkUnit(Concept c, String unit, String where) {
		if (unit.isEmpty() || c.unitType.isEmpty()) {
			return;
		}
		var ut = Units.unitTypeOfUnit(unit);
		if (ut != null && !ut.equals(c.unitType)) {
			throw new OpenOddException(
					"%s: unit \"%s\" belongs to unit type \"%s\" but concept \"%s\" has unit type \"%s\"".formatted(
							where, unit, ut, c.id, c.unitType));
		}
		if (!Units.unitTypeHasUnits(c.unitType)) {
			return; // open unit type
		}
		if (!Units.isKnownUnitSymbol(c.unitType, unit)) {
			throw new OpenOddException(
					"%s: unit \"%s\" is not registered for unit type \"%s\" of concept \"%s\"".formatted(where, unit,
							c.unitType, c.id));
		}
	}
}
