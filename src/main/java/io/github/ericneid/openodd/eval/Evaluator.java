// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.eval;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.expr.BoundExpr;
import io.github.ericneid.openodd.expr.CmpOp;
import io.github.ericneid.openodd.expr.EqualExpr;
import io.github.ericneid.openodd.expr.Expression;
import io.github.ericneid.openodd.expr.Factor;
import io.github.ericneid.openodd.expr.ListExpr;
import io.github.ericneid.openodd.expr.RangeExpr;
import io.github.ericneid.openodd.expr.ScalarKind;
import io.github.ericneid.openodd.expr.Term;
import io.github.ericneid.openodd.model.Concept;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.model.ConceptKind;
import io.github.ericneid.openodd.model.Condition;
import io.github.ericneid.openodd.model.Constraint;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.model.Module;
import io.github.ericneid.openodd.model.Section;
import io.github.ericneid.openodd.model.SectionOperator;
import io.github.ericneid.openodd.parser.Cod;
import io.github.ericneid.openodd.value.Record;
import io.github.ericneid.openodd.value.Units;
import io.github.ericneid.openodd.value.Value;
import io.github.ericneid.openodd.value.ValueKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Decides whether COD/OD records lie inside or outside the ODD modules of
 * a model. Evaluators are safe for concurrent use.
 *
 * <p>Conditions over concepts for which the record carries no value are
 * evaluated with Kleene three-valued logic: {@link #classify(Record,
 * String)} reports them as {@link OddVerdict#UNKNOWN} while the boolean
 * evaluation API collapses them to false.
 */
public final class Evaluator {

	/**
	 * Reports that a term references a concept for which the record
	 * carries no value; the enclosing condition evaluates to UNKNOWN.
	 */
	static final class MissingFactorException extends OpenOddException {
		MissingFactorException() {
			super("record has no value for a term factor");
		}
	}

	/**
	 * Kleene three-valued logic: AND, OR and NOT propagate UNKNOWN
	 * instead of collapsing it, so that missing record values stay
	 * distinguishable from violated conditions.
	 */
	private enum Kleene {
		TRUE, FALSE, UNKNOWN;

		/** Wraps a plain boolean. */
		static Kleene of(boolean b) {
			return b ? TRUE : FALSE;
		}

		/** Truth-table AND: false dominates, unknown survives. */
		Kleene and(Kleene other) {
			if (this == FALSE || other == FALSE) {
				return FALSE;
			}
			if (this == UNKNOWN || other == UNKNOWN) {
				return UNKNOWN;
			}
			return TRUE;
		}

		/** Truth-table OR: true dominates, unknown survives. */
		Kleene or(Kleene other) {
			if (this == TRUE || other == TRUE) {
				return TRUE;
			}
			if (this == UNKNOWN || other == UNKNOWN) {
				return UNKNOWN;
			}
			return FALSE;
		}

		/** Truth-table NOT. */
		Kleene not() {
			return switch (this) {
				case TRUE -> FALSE;
				case FALSE -> TRUE;
				case UNKNOWN -> UNKNOWN;
			};
		}
	}

	private final Model model;
	private final ConceptIndex idx;
	private final EvalOptions opts;
	/** Caches the literal order of orderable categoricals. */
	private final Map<String, Map<String, Integer>> orders = new ConcurrentHashMap<>();

	/** Builds an evaluator over the model. */
	public Evaluator(Model model, EvalOptions options) {
		this.model = Objects.requireNonNull(model);
		this.idx = new ConceptIndex(model.taxonomy);
		this.opts = options == null ? EvalOptions.DEFAULT : options;
	}

	/**
	 * Evaluates one module for a record. Record keys are canonicalized
	 * against the taxonomy first, so both full concept IDs and
	 * unambiguous short references work as keys. An UNKNOWN verdict
	 * collapses to false; use {@link #classify(Record, String)} to keep
	 * the three-valued verdict.
	 */
	public boolean evaluate(Record rec, String moduleId) {
		return classify(rec, moduleId).toBoolean();
	}

	/**
	 * Classifies one module for a record. Record keys are canonicalized
	 * against the taxonomy first, so both full concept IDs and
	 * unambiguous short references work as keys.
	 */
	public OddVerdict classify(Record rec, String moduleId) {
		var mod = model.modules.get(moduleId);
		if (mod == null) {
			throw new OpenOddException("unknown module \"%s\"".formatted(moduleId));
		}
		return verdict(module(newContext(rec), mod));
	}

	/**
	 * Evaluates every root module for a record. The result maps root
	 * module IDs to their truth values, with UNKNOWN collapsed to false;
	 * use {@link #classifyRoots(Record)} to keep the three-valued
	 * verdicts.
	 */
	public Map<String, Boolean> evaluateRoots(Record rec) {
		var out = new LinkedHashMap<String, Boolean>();
		for (var e : classifyRoots(rec).entrySet()) {
			out.put(e.getKey(), e.getValue().toBoolean());
		}
		return out;
	}

	/**
	 * Classifies every root module for a record. The result maps root
	 * module IDs to their three-valued verdicts.
	 */
	public Map<String, OddVerdict> classifyRoots(Record rec) {
		var ctx = newContext(rec);
		var out = new LinkedHashMap<String, OddVerdict>();
		for (var id : model.rootIds) {
			var mod = model.modules.get(id);
			if (mod == null) {
				continue;
			}
			out.put(id, verdict(module(ctx, mod)));
		}
		return out;
	}

	/** Prepares an evaluation context for one record. */
	private EvalCtx newContext(Record rec) {
		var canonical = new Record(idx.normalizeRecordKeys(rec.values()));
		canonical.temporal(rec.temporal());
		canonical.spatial(rec.spatial());
		return new EvalCtx(canonical);
	}

	/**
	 * Reports whether a record lies inside the ODD: all root modules
	 * evaluate to true. A record with an UNKNOWN verdict is not inside;
	 * use {@link #classify(Record)} to keep the three-valued verdict.
	 */
	public boolean insideOdd(Record rec) {
		return classify(rec).toBoolean();
	}

	/**
	 * Classifies a record against the whole ODD: INSIDE when every root
	 * module is true, OUTSIDE when any root module is false, UNKNOWN
	 * when no root module is false but at least one cannot be judged.
	 */
	public OddVerdict classify(Record rec) {
		var result = Kleene.TRUE;
		for (var v : classifyRoots(rec).values()) {
			result = result.and(kleene(v));
		}
		return verdict(result);
	}

	/**
	 * Evaluates all root modules for every record of a table. The result
	 * is aligned with {@code cod.records}, with UNKNOWN collapsed to
	 * false; use {@link #classifyCod(Cod)} to keep the three-valued
	 * verdicts.
	 */
	public List<Map<String, Boolean>> evaluateCod(Cod cod) {
		var results = new ArrayList<Map<String, Boolean>>(cod.records.size());
		for (var row : classifyCod(cod)) {
			var collapsed = new LinkedHashMap<String, Boolean>();
			for (var e : row.entrySet()) {
				collapsed.put(e.getKey(), e.getValue().toBoolean());
			}
			results.add(collapsed);
		}
		return results;
	}

	/**
	 * Classifies all root modules for every record of a table. The
	 * result is aligned with {@code cod.records}.
	 */
	public List<Map<String, OddVerdict>> classifyCod(Cod cod) {
		var results = new ArrayList<Map<String, OddVerdict>>(cod.records.size());
		for (var i = 0; i < cod.records.size(); i++) {
			try {
				results.add(classifyRoots(cod.records.get(i)));
			} catch (OpenOddException e) {
				throw new OpenOddException("record %d: %s".formatted(i + 1, e.getMessage()), e);
			}
		}
		return results;
	}

	/** Maps a truth value to its verdict. */
	private static OddVerdict verdict(Kleene k) {
		return switch (k) {
			case TRUE -> OddVerdict.INSIDE;
			case FALSE -> OddVerdict.OUTSIDE;
			case UNKNOWN -> OddVerdict.UNKNOWN;
		};
	}

	/** Maps a verdict back to its truth value. */
	private static Kleene kleene(OddVerdict v) {
		return switch (v) {
			case INSIDE -> Kleene.TRUE;
			case OUTSIDE -> Kleene.FALSE;
			case UNKNOWN -> Kleene.UNKNOWN;
		};
	}

	/** Carries per-record evaluation state. */
	private static final class EvalCtx {
		final Record rec;
		final Map<String, Kleene> cache = new HashMap<>();
		final Map<String, Boolean> busy = new HashMap<>();

		EvalCtx(Record rec) {
			this.rec = rec;
		}
	}

	/**
	 * Evaluates a module: INCLUDE AND (NOT EXCLUDE), with the missing
	 * section rules of Section 6.4.3.4 of the standard.
	 */
	private Kleene module(EvalCtx ctx, Module mod) {
		var cached = ctx.cache.get(mod.id);
		if (cached != null) {
			return cached;
		}
		if (!mod.active) {
			// Inactive modules are ignored; conditions referring to them
			// or their negation are automatically satisfied.
			ctx.cache.put(mod.id, Kleene.TRUE);
			return Kleene.TRUE;
		}
		if (ctx.busy.get(mod.id) != null) {
			throw new OpenOddException("cyclic module reference through \"%s\"".formatted(mod.id));
		}
		ctx.busy.put(mod.id, true);
		try {
			var include = Kleene.TRUE;
			if (mod.include != null) {
				try {
					include = section(ctx, mod.include);
				} catch (OpenOddException e) {
					throw new OpenOddException("module \"%s\" INCLUDE: %s".formatted(mod.id, e.getMessage()), e);
				}
			}
			var result = include;
			// A false INCLUDE decides the module on its own, mirroring
			// the short-circuit of the boolean pipeline.
			if (mod.exclude != null && result != Kleene.FALSE) {
				try {
					result = result.and(section(ctx, mod.exclude).not());
				} catch (OpenOddException e) {
					throw new OpenOddException("module \"%s\" EXCLUDE: %s".formatted(mod.id, e.getMessage()), e);
				}
			}
			ctx.cache.put(mod.id, result);
			return result;
		} finally {
			ctx.busy.remove(mod.id);
		}
	}

	/**
	 * Evaluates a section: the AND or OR over its conditions and
	 * subsections.
	 */
	private Kleene section(EvalCtx ctx, Section sec) {
		var result = sec.operator == SectionOperator.AND ? Kleene.TRUE : Kleene.FALSE;
		for (var cond : sec.conditions) {
			result = combine(sec.operator, result, condition(ctx, cond));
		}
		for (var sub : sec.subsections) {
			result = combine(sec.operator, result, section(ctx, sub));
		}
		return result;
	}

	/** Folds one operand into a section result. */
	private static Kleene combine(SectionOperator op, Kleene result, Kleene v) {
		return op == SectionOperator.AND ? result.and(v) : result.or(v);
	}

	/**
	 * Evaluates a single condition by resolving its reference to a
	 * module, label or taxonomy concept.
	 */
	private Kleene condition(EvalCtx ctx, Condition cond) {
		// Modules take precedence over concepts; the standard requires the
		// ID spaces to be disjoint, so a clash is a validation error.
		var mod = model.modules.get(cond.ref);
		if (mod != null) {
			var want = boolOperand(cond);
			if (want == null) {
				throw new OpenOddException(
						"condition \"%s\": module references take true or false".formatted(cond.ref));
			}
			if (!mod.active) {
				return Kleene.TRUE;
			}
			return compareOperand(module(ctx, mod), want);
		}
		if (model.labels.containsKey(cond.ref)) {
			var want = boolOperand(cond);
			if (want == null) {
				throw new OpenOddException(
						"condition \"%s\": label references take true or false".formatted(cond.ref));
			}
			return compareOperand(label(ctx, cond.ref), want);
		}
		Concept c;
		try {
			c = idx.resolve(cond.ref);
		} catch (OpenOddException e) {
			throw new OpenOddException("condition \"%s\": %s".formatted(cond.ref, e.getMessage()), e);
		}
		return expression(ctx, c, cond.expr);
	}

	/** Compares an operand's truth value against the wanted polarity. */
	private static Kleene compareOperand(Kleene got, boolean want) {
		if (got == Kleene.UNKNOWN) {
			return Kleene.UNKNOWN;
		}
		return Kleene.of((got == Kleene.TRUE) == want);
	}

	/**
	 * Extracts the expected truth value of a module or label reference
	 * condition; {@code null} when the expression is not a boolean
	 * operand.
	 */
	static Boolean boolOperand(Condition cond) {
		if (cond.expr instanceof EqualExpr eq && eq.kind() == ScalarKind.BOOL) {
			return eq.bool();
		}
		if (cond.expr instanceof EqualExpr eq && eq.kind() == ScalarKind.LITERAL) {
			return switch (eq.str().toLowerCase()) {
				case "true" -> true;
				case "false" -> false;
				default -> null;
			};
		}
		return null;
	}

	/**
	 * Evaluates a label as the disjunction of its active modules. A
	 * label without any active module satisfies every condition
	 * referencing it, mirroring the inactive-module rule.
	 */
	private Kleene label(EvalCtx ctx, String label) {
		var anyActive = false;
		var result = Kleene.FALSE;
		for (var id : model.labels.get(label)) {
			var mod = model.modules.get(id);
			if (mod == null || !mod.active) {
				continue;
			}
			anyActive = true;
			result = result.or(module(ctx, mod));
			if (result == Kleene.TRUE) {
				return Kleene.TRUE;
			}
		}
		return anyActive ? result : Kleene.TRUE;
	}

	/**
	 * Evaluates a boolean expression over a concept against the record of
	 * the current evaluation context.
	 */
	private Kleene expression(EvalCtx ctx, Concept c, Expression expr) {
		if (expr instanceof EqualExpr ex) {
			return equal(ctx, c, ex);
		}
		if (expr instanceof BoundExpr ex) {
			return bound(ctx, c, ex);
		}
		if (expr instanceof RangeExpr ex) {
			return rangeExpr(ctx, c, ex);
		}
		if (expr instanceof ListExpr ex) {
			return list(ctx, c, ex);
		}
		throw new OpenOddException("unsupported expression");
	}

	/** Fetches the record value of a concept. */
	private Value recordValue(EvalCtx ctx, Concept c) {
		var hit = idx.valueFor(c, ctx.rec);
		return hit.value();
	}

	/** Evaluates Equal expressions. */
	private Kleene equal(EvalCtx ctx, Concept c, EqualExpr ex) {
		switch (ex.kind()) {
			case UNKNOWN:
				return Kleene.of(recordValue(ctx, c).isUnknown());
			case BOOL:
				return equalBool(ctx, c, ex.bool());
			case NUMBER:
				return equalNumber(ctx, c, ex.num(), ex.unit());
			default:
				// A "$" prefix denotes a parameter reference whose record
				// value participates in the comparison.
				if (ex.str().startsWith("$")) {
					try {
						var pc = idx.resolve(ex.str());
						if (pc.parameter) {
							var pv = recordValue(ctx, pc);
							if (pc.kind == ConceptKind.CATEGORICAL && pv.kind() == ValueKind.CATEGORICAL) {
								return equalCategorical(ctx, c, pv.str());
							}
							if (pc.kind == ConceptKind.ATTRIBUTE && Concept.isNumericPrimitive(pc.primType)
									&& pv.kind() == ValueKind.NUMBER) {
								return equalNumber(ctx, c, pv.num(), pv.unit());
							}
							if (pv.kind() == ValueKind.MISSING) {
								// The record carries no parameter value.
								return Kleene.UNKNOWN;
							}
							// Incompatible parameter value.
							return Kleene.FALSE;
						}
					} catch (OpenOddException ignored) {
						// Unresolvable references are reported by the validator.
					}
				}
				if (ex.str().equals("unknown")) {
					return Kleene.of(recordValue(ctx, c).isUnknown());
				}
				return equalCategorical(ctx, c, ex.str());
		}
	}

	private Kleene equalBool(EvalCtx ctx, Concept c, boolean want) {
		var v = recordValue(ctx, c);
		var wantStr = formatBool(want);
		return switch (v.kind()) {
			case BOOL -> Kleene.of(v.bool() == want);
			case CATEGORICAL -> Kleene.of(v.str().equals(wantStr));
			case LIST -> Kleene.of(anyOf(v, item -> switch (item.kind()) {
				case BOOL -> item.bool() == want;
				case CATEGORICAL -> item.str().equals(wantStr);
				default -> false;
			}));
			case MISSING -> Kleene.UNKNOWN;
			default -> throw new OpenOddException(
					"concept \"%s\": boolean comparison needs a boolean value, got %s".formatted(c.id, v.kind()));
		};
	}

	static String formatBool(boolean b) {
		return Boolean.toString(b);
	}

	private Kleene equalNumber(EvalCtx ctx, Concept c, double want, String unit) {
		var v = recordValue(ctx, c);
		return switch (v.kind()) {
			case NUMBER -> {
				var w = convertToConceptBase(c, want, unit);
				var x = valueInConceptBase(c, v);
				yield Kleene.of(numEqual(x, w));
			}
			case RANGE -> {
				var w = convertToConceptBase(c, want, unit);
				var conv = convertedRange(c, v);
				yield Kleene.of(compareInterval(CmpOp.NONE, conv.min(), conv.max(), w));
			}
			case LIST -> Kleene.of(anyOf(v, item -> {
				if (item.kind() == ValueKind.NUMBER) {
					var w = convertToConceptBase(c, want, unit);
					var x = valueInConceptBase(c, item);
					return numEqual(x, w);
				}
				return false;
			}));
			case MISSING -> Kleene.UNKNOWN;
			default -> throw new OpenOddException(
					"concept \"%s\": numeric equality needs a numeric value, got %s".formatted(c.id, v.kind()));
		};
	}

	private Kleene equalCategorical(EvalCtx ctx, Concept c, String want) {
		var v = recordValue(ctx, c);
		return switch (v.kind()) {
			case CATEGORICAL -> {
				if (v.str().equals(want)) {
					yield Kleene.TRUE;
				}
				// The record may carry a numeric value for the concept
				// that defines the literal; derive the literal from the
				// record.
				yield derivedLiteralEquals(ctx, c, want);
			}
			case BOOL -> Kleene.of(formatBool(v.bool()).equals(want));
			case NUMBER -> derivedLiteralMatches(ctx, c, want);
			case LIST -> Kleene.of(anyOf(v, item -> switch (item.kind()) {
				case CATEGORICAL -> item.str().equals(want);
				case BOOL -> formatBool(item.bool()).equals(want);
				case NUMBER -> {
					var lit = deriveLiteral(ctx, c);
					yield lit.found() && lit.name().equals(want);
				}
				default -> false;
			}));
			case MISSING -> {
				// The record may carry values for the concepts defining
				// the literals; derive the literal from those constraints.
				yield derivedLiteralMatches(ctx, c, want);
			}
			default -> throw new OpenOddException(
					"concept \"%s\": literal comparison needs a categorical value, got %s".formatted(c.id, v.kind()));
		};
	}

	/**
	 * Derives the record's literal of a concept and compares it against
	 * the wanted name; UNKNOWN when no literal can be derived.
	 */
	private Kleene derivedLiteralMatches(EvalCtx ctx, Concept c, String want) {
		var lit = deriveLiteral(ctx, c);
		if (!lit.found()) {
			return Kleene.UNKNOWN;
		}
		return Kleene.of(lit.name().equals(want));
	}

	/** Checks the defining constraints of a literal against the record. */
	private Kleene derivedLiteralEquals(EvalCtx ctx, Concept c, String want) {
		var lit = c.literal(want);
		if (lit == null || lit.constraints.isEmpty()) {
			return Kleene.FALSE;
		}
		var result = Kleene.TRUE;
		for (var cons : lit.constraints) {
			Concept cc;
			try {
				cc = idx.resolve(cons.ref());
			} catch (OpenOddException e) {
				throw new OpenOddException(
						"literal \"%s\" of \"%s\": %s".formatted(want, c.id, e.getMessage()), e);
			}
			var v = expression(ctx, cc, cons.expr());
			if (v == Kleene.FALSE) {
				return Kleene.FALSE;
			}
			if (v == Kleene.UNKNOWN) {
				result = Kleene.UNKNOWN;
			}
		}
		return result;
	}

	/** The result of {@link #deriveLiteral}. */
	record DerivedLiteral(String name, boolean found) {
		static final DerivedLiteral NONE = new DerivedLiteral("", false);
	}

	/**
	 * Maps a numeric record value to the literal whose defining
	 * constraints it satisfies.
	 */
	private DerivedLiteral deriveLiteral(EvalCtx ctx, Concept c) {
		for (var lit : c.literals) {
			if (lit.constraints.isEmpty()) {
				continue;
			}
			var match = Kleene.TRUE;
			for (var cons : lit.constraints) {
				Concept cc;
				try {
					cc = idx.resolve(cons.ref());
				} catch (OpenOddException e) {
					return DerivedLiteral.NONE;
				}
				var v = expression(ctx, cc, cons.expr());
				if (v == Kleene.FALSE) {
					match = Kleene.FALSE;
					break;
				}
				if (v == Kleene.UNKNOWN) {
					match = Kleene.UNKNOWN;
				}
			}
			if (match == Kleene.TRUE) {
				return new DerivedLiteral(lit.name, true);
			}
		}
		return DerivedLiteral.NONE;
	}

	/** Evaluates UpperBound and LowerBound expressions. */
	private Kleene bound(EvalCtx ctx, Concept c, BoundExpr ex) {
		if (c.kind == ConceptKind.CATEGORICAL) {
			return boundCategorical(ctx, c, ex);
		}
		TermValue tv;
		try {
			tv = term(ctx, ex.term());
		} catch (MissingFactorException e) {
			return Kleene.UNKNOWN;
		}
		var threshold = tv.value();
		var termType = tv.unitType();
		if (!ex.unit().isEmpty()) {
			checkUnit(c, ex.unit());
			threshold = convertUnitToBase(c, threshold, ex.unit());
		} else if (!termType.isEmpty() && !c.unitType.isEmpty() && !termType.equals(c.unitType)) {
			throw new OpenOddException(
					"concept \"%s\" has unit type \"%s\" but the term is of unit type \"%s\"".formatted(c.id,
							c.unitType, termType));
		}
		var v = recordValue(ctx, c);
		return compareNumber(c, v, ex.op(), threshold);
	}

	/** Evaluates Range expressions "[a .. b]". */
	private Kleene rangeExpr(EvalCtx ctx, Concept c, RangeExpr ex) {
		if (c.kind == ConceptKind.CATEGORICAL) {
			return rangeCategorical(ctx, c, ex);
		}
		TermValue loTv;
		try {
			loTv = term(ctx, ex.min());
		} catch (MissingFactorException e) {
			return Kleene.UNKNOWN;
		}
		TermValue hiTv;
		try {
			hiTv = term(ctx, ex.max());
		} catch (MissingFactorException e) {
			return Kleene.UNKNOWN;
		}
		var lo = loTv.value();
		var hi = hiTv.value();
		var lot = loTv.unitType();
		var hit = hiTv.unitType();
		if (!lot.isEmpty() && !hit.isEmpty() && !lot.equals(hit)) {
			throw new OpenOddException(
					"concept \"%s\": range bounds use different unit types \"%s\" and \"%s\"".formatted(c.id, lot,
							hit));
		}
		if (!ex.unit().isEmpty()) {
			checkUnit(c, ex.unit());
			lo = convertUnitToBase(c, lo, ex.unit());
			hi = convertUnitToBase(c, hi, ex.unit());
		}
		var v = recordValue(ctx, c);
		return compareRange(c, v, lo, hi);
	}

	/**
	 * Converts an uncertain numeric value into the base unit of the
	 * concept's unit type.
	 */
	private Value convertedRange(Concept c, Value v) {
		if (v.kind() != ValueKind.RANGE) {
			return v;
		}
		var lo = valueInConceptBase(c, Value.number(v.min(), v.unit(), v.unitType()));
		var hi = valueInConceptBase(c, Value.number(v.max(), v.unit(), v.unitType()));
		return Value.rangeOf(lo, hi);
	}

	/**
	 * Compares an uncertain value interval [lo, hi] against a threshold
	 * under the configured uncertainty mode. Possibility asks whether the
	 * predicate holds for at least one point of the interval, certainty
	 * whether it holds for all of them; for the monotone comparison
	 * operators and equality this is decided exactly by the interval
	 * bounds.
	 */
	private boolean compareInterval(CmpOp op, double lo, double hi, double t) {
		if (opts.uncertainty() == UncertaintyMode.CERTAINTY) {
			return switch (op) {
				case LESS -> hi < t;
				case LESS_EQUAL -> hi <= t;
				case GREATER -> lo > t;
				case GREATER_EQUAL -> lo >= t;
				default -> numEqual(lo, t) && numEqual(hi, t);
			};
		}
		return switch (op) {
			case LESS -> lo < t;
			case LESS_EQUAL -> lo <= t;
			case GREATER -> hi > t;
			case GREATER_EQUAL -> hi >= t;
			default ->
				// Equality holds when the threshold lies inside the
				// interval, with a small tolerance for unit-conversion
				// rounding.
				lo <= t && t <= hi || numEqual(lo, t) || numEqual(hi, t);
		};
	}

	/** Compares a record value against a threshold. */
	private Kleene compareNumber(Concept c, Value v, CmpOp op, double threshold) {
		return switch (v.kind()) {
			case NUMBER -> Kleene.of(compare(valueInConceptBase(c, v), op, threshold));
			case RANGE -> {
				var conv = convertedRange(c, v);
				yield Kleene.of(compareInterval(op, conv.min(), conv.max(), threshold));
			}
			case LIST -> Kleene.of(anyOf(v, item -> {
				if (item.kind() == ValueKind.NUMBER) {
					return compare(valueInConceptBase(c, item), op, threshold);
				}
				return false;
			}));
			case MISSING -> Kleene.UNKNOWN;
			default -> throw new OpenOddException(
					"concept \"%s\": bound comparison needs a numeric value, got %s".formatted(c.id, v.kind()));
		};
	}

	/**
	 * Checks membership in the condition interval [lo, hi]. For an
	 * uncertain record interval, possibility asks for a non-empty
	 * intersection with the condition interval and certainty for
	 * containment.
	 */
	private Kleene compareRange(Concept c, Value v, double lo, double hi) {
		return switch (v.kind()) {
			case NUMBER -> Kleene.of(inInterval(valueInConceptBase(c, v), lo, hi));
			case RANGE -> {
				var conv = convertedRange(c, v);
				if (opts.uncertainty() == UncertaintyMode.CERTAINTY) {
					yield Kleene.of(conv.min() >= lo && conv.max() <= hi);
				}
				yield Kleene.of(conv.min() <= hi && conv.max() >= lo);
			}
			case LIST -> Kleene.of(anyOf(v, item -> {
				if (item.kind() == ValueKind.NUMBER) {
					var x = valueInConceptBase(c, item);
					return x >= lo && x <= hi;
				}
				return false;
			}));
			case MISSING -> Kleene.UNKNOWN;
			default -> throw new OpenOddException(
					"concept \"%s\": range comparison needs a numeric value, got %s".formatted(c.id, v.kind()));
		};
	}

	static boolean compare(double x, CmpOp op, double t) {
		return switch (op) {
			case LESS -> x < t;
			case LESS_EQUAL -> x <= t;
			case GREATER -> x > t;
			case GREATER_EQUAL -> x >= t;
			default -> numEqual(x, t);
		};
	}

	/**
	 * Compares two numbers with a small relative tolerance. Exact
	 * equality is preserved where possible; the tolerance absorbs
	 * rounding introduced by unit conversion, for example 180/1.8 when
	 * comparing 212 F with 100 C.
	 */
	static boolean numEqual(double a, double b) {
		if (a == b) {
			return true;
		}
		var diff = Math.abs(a - b);
		var scale = Math.max(Math.abs(a), Math.abs(b));
		if (scale < 1) {
			scale = 1;
		}
		return diff <= 1e-12 * scale;
	}

	/**
	 * Reports whether x lies in [lo, hi], with a small tolerance for
	 * unit-conversion rounding at the bounds.
	 */
	static boolean inInterval(double x, double lo, double hi) {
		return (x > lo || numEqual(x, lo)) && (x < hi || numEqual(x, hi));
	}

	/** Applies f to the alternatives of an epistemic list value. */
	private boolean anyOf(Value v, Predicate<Value> f) {
		for (var item : v.items()) {
			var ok = f.test(item);
			if (opts.uncertainty() == UncertaintyMode.CERTAINTY) {
				if (!ok) {
					return false;
				}
				continue;
			}
			if (ok) {
				return true;
			}
		}
		return opts.uncertainty() != UncertaintyMode.CERTAINTY;
	}

	/** A term's numeric value together with the unit type it carries. */
	record TermValue(double value, String unitType) {
	}

	/**
	 * Evaluates a numeric term: numbers and attribute or parameter
	 * references combined by one multiplication or division. The result
	 * is expressed in the base unit of the referenced unit type; numbers
	 * are dimensionless.
	 */
	private TermValue term(EvalCtx ctx, Term t) {
		var left = factor(ctx, t.left());
		if (t.op() == 0) {
			return left;
		}
		var right = factor(ctx, t.right());
		var lv = left.value();
		var rv = right.value();
		var lt = left.unitType();
		var rt = right.unitType();
		switch (t.op()) {
			case '*':
				if (lt.isEmpty()) {
					return new TermValue(lv * rv, rt);
				}
				if (rt.isEmpty()) {
					return new TermValue(lv * rv, lt);
				}
				throw new OpenOddException(
						"cannot multiply \"%s\" and \"%s\"; only dimensionless factors may be combined".formatted(lt,
								rt));
			case '/':
				if (lt.isEmpty() && rt.isEmpty()) {
					if (rv == 0) {
						throw new OpenOddException("division by zero in term");
					}
					return new TermValue(lv / rv, "");
				}
				if (rt.isEmpty()) {
					if (rv == 0) {
						throw new OpenOddException("division by zero in term");
					}
					return new TermValue(lv / rv, lt);
				}
				if (lt.equals(rt)) {
					if (rv == 0) {
						throw new OpenOddException("division by zero in term");
					}
					return new TermValue(lv / rv, "");
				}
				throw new OpenOddException("cannot divide \"%s\" by \"%s\"".formatted(lt, rt));
			default:
				throw new OpenOddException("invalid term operator \"%s\"".formatted(String.valueOf(t.op())));
		}
	}

	private TermValue factor(EvalCtx ctx, Factor f) {
		if (!f.isRef()) {
			return new TermValue(f.number(), "");
		}
		Concept c;
		try {
			c = idx.resolve(f.ref());
		} catch (OpenOddException e) {
			throw new OpenOddException("term factor \"%s\": %s".formatted(f.ref(), e.getMessage()), e);
		}
		if (c.kind != ConceptKind.ATTRIBUTE || !Concept.isNumericPrimitive(c.primType)) {
			throw new OpenOddException(
					"term factor \"%s\": %s is not a numeric attribute".formatted(f.ref(), c.describe()));
		}
		var v = recordValue(ctx, c);
		if (v.kind() == ValueKind.MISSING) {
			throw new MissingFactorException();
		}
		if (v.kind() != ValueKind.NUMBER) {
			throw new OpenOddException(
					"term factor \"%s\": record value is not numeric (got %s)".formatted(f.ref(), v.kind()));
		}
		var x = valueInConceptBase(c, v);
		return new TermValue(x, c.unitType);
	}

	/**
	 * Converts a numeric record value into the base unit of the concept's
	 * unit type.
	 */
	private double valueInConceptBase(Concept c, Value v) {
		if (v.unit().isEmpty() || c.unitType.isEmpty()) {
			return v.num();
		}
		if (!v.unitType().isEmpty() && !v.unitType().equals(c.unitType)) {
			throw new OpenOddException(
					"concept \"%s\" has unit type \"%s\" but the value carries unit type \"%s\"".formatted(c.id,
							c.unitType, v.unitType()));
		}
		if (!Units.hasUnitType(c.unitType)) {
			// Unknown unit type: compare symbolically.
			return v.num();
		}
		var base = Units.baseUnitOf(c.unitType);
		return Units.convertUnit(c.unitType, v.unit(), base, v.num());
	}

	/**
	 * Converts a numeric expression operand with an explicit unit into
	 * the base unit of the concept's unit type.
	 */
	private double convertToConceptBase(Concept c, double v, String unit) {
		if (unit.isEmpty() || c.unitType.isEmpty()) {
			return v;
		}
		checkUnit(c, unit);
		return convertUnitToBase(c, v, unit);
	}

	/**
	 * Converts a value in a known unit to the base unit of the concept's
	 * unit type.
	 */
	private double convertUnitToBase(Concept c, double v, String unit) {
		if (!Units.hasUnitType(c.unitType)) {
			return v;
		}
		return Units.convertUnit(c.unitType, unit, Units.baseUnitOf(c.unitType), v);
	}

	/**
	 * Validates that a unit symbol belongs to the concept's unit type.
	 */
	private void checkUnit(Concept c, String unit) {
		if (unit.isEmpty() || c.unitType.isEmpty()) {
			return;
		}
		var ut = Units.unitTypeOfUnit(unit);
		if (ut != null && !ut.equals(c.unitType)) {
			throw new OpenOddException(
					"unit \"%s\" belongs to unit type \"%s\" but concept \"%s\" has unit type \"%s\"".formatted(unit,
							ut, c.id, c.unitType));
		}
		if (!Units.unitTypeHasUnits(c.unitType)) {
			// Open unit type: no registry to check against.
			return;
		}
		if (!Units.isKnownUnitSymbol(c.unitType, unit)) {
			throw new OpenOddException(
					"unit \"%s\" is not registered for unit type \"%s\" of concept \"%s\"".formatted(unit, c.unitType,
							c.id));
		}
	}

	/**
	 * Evaluates a bound over orderable categorical literals, for example
	 * "rain_level: &lt; heavy_rain".
	 */
	private Kleene boundCategorical(EvalCtx ctx, Concept c, BoundExpr ex) {
		var order = literalOrder(c);
		var threshold = order.get(ex.term().toString());
		if (threshold == null) {
			throw new OpenOddException(
					"concept \"%s\": \"%s\" is not a categorical literal".formatted(c.id, ex.term().toString()));
		}
		var idx = categoricalIndex(ctx, c);
		if (idx == null) {
			// The record's literal is missing or not orderable.
			return Kleene.UNKNOWN;
		}
		return Kleene.of(compare(idx, ex.op(), threshold));
	}

	/** Evaluates a range over orderable categorical literals. */
	private Kleene rangeCategorical(EvalCtx ctx, Concept c, RangeExpr ex) {
		var order = literalOrder(c);
		var lo = order.get(ex.min().toString());
		if (lo == null) {
			throw new OpenOddException(
					"concept \"%s\": \"%s\" is not a categorical literal".formatted(c.id, ex.min().toString()));
		}
		var hi = order.get(ex.max().toString());
		if (hi == null) {
			throw new OpenOddException(
					"concept \"%s\": \"%s\" is not a categorical literal".formatted(c.id, ex.max().toString()));
		}
		var idx = categoricalIndex(ctx, c);
		if (idx == null) {
			// The record's literal is missing or not orderable.
			return Kleene.UNKNOWN;
		}
		return Kleene.of(idx >= lo && idx <= hi);
	}

	/**
	 * Determines the order index of the record's literal for a
	 * categorical concept, deriving it from numeric values when needed;
	 * {@code null} when unknown.
	 */
	private Integer categoricalIndex(EvalCtx ctx, Concept c) {
		var v = recordValue(ctx, c);
		String lit;
		switch (v.kind()) {
			case CATEGORICAL:
				lit = v.str();
				break;
			case BOOL:
				lit = formatBool(v.bool());
				break;
			case NUMBER, RANGE, LIST, MISSING:
				var derived = deriveLiteral(ctx, c);
				if (!derived.found()) {
					return null;
				}
				lit = derived.name();
				break;
			default:
				return null;
		}
		var order = literalOrder(c);
		return order.get(lit);
	}

	/**
	 * Computes the order of literals induced by their numeric range
	 * constraints (Section 6.2.4.7 of the standard).
	 */
	private Map<String, Integer> literalOrder(Concept c) {
		var cached = orders.get(c.id);
		if (cached != null) {
			return cached;
		}
		record Bound(String name, double lo, double hi) {
		}
		var bounds = new ArrayList<Bound>(c.literals.size());
		for (var lit : c.literals) {
			Bound litBound = null;
			for (var cons : lit.constraints) {
				Concept cc;
				try {
					cc = idx.resolve(cons.ref());
				} catch (OpenOddException e) {
					throw new OpenOddException(
							"literal \"%s\" of \"%s\": %s".formatted(lit.name, c.id, e.getMessage()), e);
				}
				var iv = constraintInterval(cons, cc);
				if (iv == null) {
					continue;
				}
				litBound = new Bound(lit.name, iv.lo(), iv.hi());
				break;
			}
			if (litBound == null) {
				throw new OpenOddException(
						"concept \"%s\" is not orderable: literal \"%s\" has no numeric range constraint".formatted(
								c.id, lit.name));
			}
			bounds.add(litBound);
		}
		bounds.sort((a, b) -> {
			if (a.lo() != b.lo()) {
				return Double.compare(a.lo(), b.lo());
			}
			return Double.compare(a.hi(), b.hi());
		});
		var order = new HashMap<String, Integer>(bounds.size());
		for (var i = 0; i < bounds.size(); i++) {
			order.put(bounds.get(i).name(), i);
		}
		orders.put(c.id, order);
		return order;
	}

	/** The numeric interval of a literal constraint. */
	record Interval(double lo, double hi) {
	}

	/**
	 * Extracts the numeric interval of a literal constraint, expressed
	 * in the base unit of the referenced concept; {@code null} when the
	 * constraint is not numeric.
	 */
	private Interval constraintInterval(Constraint cons, Concept cc) {
		if (cons.expr() instanceof BoundExpr expr) {
			var empty = new EvalCtx(new Record());
			var t = term(empty, expr.term()).value();
			if (!expr.unit().isEmpty()) {
				t = convertUnitToBase(cc, t, expr.unit());
			}
			return switch (expr.op()) {
				case GREATER, GREATER_EQUAL -> new Interval(t, Double.POSITIVE_INFINITY);
				case LESS, LESS_EQUAL -> new Interval(Double.NEGATIVE_INFINITY, t);
				default -> new Interval(t, t);
			};
		}
		if (cons.expr() instanceof RangeExpr expr) {
			var empty = new EvalCtx(new Record());
			var lo = term(empty, expr.min()).value();
			var hi = term(empty, expr.max()).value();
			if (!expr.unit().isEmpty()) {
				lo = convertUnitToBase(cc, lo, expr.unit());
				hi = convertUnitToBase(cc, hi, expr.unit());
			}
			return new Interval(lo, hi);
		}
		return null;
	}

	/** Evaluates CategoricalList expressions. */
	private Kleene list(EvalCtx ctx, Concept c, ListExpr ex) {
		var items = expandListItems(ctx, ex.items());
		var v = recordValue(ctx, c);
		return switch (v.kind()) {
			case CATEGORICAL -> Kleene.of(contains(items, v.str()));
			case BOOL -> Kleene.of(contains(items, formatBool(v.bool())));
			case NUMBER -> {
				var lit = deriveLiteral(ctx, c);
				if (!lit.found()) {
					yield Kleene.UNKNOWN;
				}
				yield Kleene.of(contains(items, lit.name()));
			}
			case LIST -> Kleene.of(anyOf(v, item -> switch (item.kind()) {
				case CATEGORICAL -> contains(items, item.str());
				case BOOL -> contains(items, formatBool(item.bool()));
				case NUMBER -> {
					var lit = deriveLiteral(ctx, c);
					yield lit.found() && contains(items, lit.name());
				}
				default -> false;
			}));
			case MISSING -> {
				// The record may carry values for the concepts defining
				// the literals; derive the literal from those constraints.
				var lit = deriveLiteral(ctx, c);
				if (lit.found() && contains(items, lit.name())) {
					yield Kleene.TRUE;
				}
				// A list accepts missing values through an "unknown" item.
				yield contains(items, "unknown") ? Kleene.TRUE : Kleene.UNKNOWN;
			}
			default -> throw new OpenOddException(
					"concept \"%s\": list comparison needs a categorical value, got %s".formatted(c.id, v.kind()));
		};
	}

	/** Resolves "$parameter" list items against the record. */
	private List<String> expandListItems(EvalCtx ctx, List<String> items) {
		var out = new ArrayList<String>(items.size());
		for (var item : items) {
			if (!item.startsWith("$")) {
				out.add(item);
				continue;
			}
			Concept pc;
			try {
				pc = idx.resolve(item);
			} catch (OpenOddException e) {
				throw new OpenOddException("list item \"%s\": %s".formatted(item, e.getMessage()), e);
			}
			var pv = recordValue(ctx, pc);
			switch (pv.kind()) {
				case CATEGORICAL -> out.add(pv.str());
				case BOOL -> out.add(formatBool(pv.bool()));
				case LIST -> {
					for (var alt : pv.items()) {
						switch (alt.kind()) {
							case CATEGORICAL -> out.add(alt.str());
							case BOOL -> out.add(formatBool(alt.bool()));
							default -> {
							}
						}
					}
				}
				default -> throw new OpenOddException(
						"list item \"%s\": record has no categorical value for the parameter".formatted(item));
			}
		}
		return out;
	}

	static boolean contains(List<String> items, String s) {
		return items.contains(s);
	}
}
