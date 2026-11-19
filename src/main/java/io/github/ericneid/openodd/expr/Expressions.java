// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.internal.Texts;

import java.util.ArrayList;

/**
 * Parses condition values from their string form into {@link Expression}
 * instances. The input is the raw scalar text after YAML decoding; quoted
 * and unquoted spellings arrive here identically.
 */
public final class Expressions {

	private Expressions() {
	}

	/** Recognizes the YAML operator notation of Code 199; "" denotes equality. */
	static CmpOp parseCmpOp(String s) {
		for (var i = 0; i < s.length(); i++) {
			var c = s.charAt(i);
			if (c == '<' || c == '>') {
				var op = String.valueOf(c);
				if (i + 1 < s.length() && s.charAt(i + 1) == '=') {
					op += "=";
				}
				return switch (op) {
					case "<=" -> CmpOp.LESS_EQUAL;
					case ">=" -> CmpOp.GREATER_EQUAL;
					case "<" -> CmpOp.LESS;
					case ">" -> CmpOp.GREATER;
					default -> CmpOp.NONE;
				};
			}
			break;
		}
		return CmpOp.NONE;
	}

	/** Parses a condition value from its string form. */
	public static Expression parse(String input) {
		var s = input.trim();
		if (s.isEmpty()) {
			throw new OpenOddException("empty expression");
		}
		var op = parseCmpOp(s);
		if (op != CmpOp.NONE) {
			var rest = s.substring(op.symbol().length()).trim();
			var tu = parseTermWithUnit(rest);
			return new BoundExpr(op, tu.term(), tu.unit());
		}
		if (s.startsWith("[")) {
			// A range or list expression, for example "[2 .. 10] mm/h" or
			// "[fog_mist, fog_medium]". Whatever follows the closing
			// bracket is the unit.
			var end = s.indexOf(']');
			if (end < 0) {
				throw new OpenOddException("malformed range or list \"%s\"".formatted(s));
			}
			var inner = s.substring(1, end).trim();
			var unit = s.substring(end + 1).trim();
			if (inner.contains("..")) {
				return parseRangeExpression(inner, unit);
			}
			if (!unit.isEmpty()) {
				throw new OpenOddException("list expression \"%s\" must not carry a unit".formatted(s));
			}
			return parseListExpression(inner);
		}
		// A bare range without brackets, for example "2 .. 10". Unquoted
		// YAML values such as [2 .. 10] arrive as a sequence whose single
		// item is the range text without brackets. The surrounding spaces
		// are part of the standard's notation; they also keep dotted
		// references such as "a..b" from being mistaken for ranges.
		if (s.contains(" .. ")) {
			try {
				return parseRangeExpression(s, "");
			} catch (OpenOddException ignored) {
				// A malformed range falls through to the equal expression
				// spelling, mirroring the reference implementation.
			}
		}
		// Equal expression.
		if (Texts.isUnknownKeyword(s)) {
			return EqualExpr.unknown();
		}
		switch (s.toLowerCase()) {
			case "true" -> {
				return EqualExpr.boolValue(true);
			}
			case "false" -> {
				return EqualExpr.boolValue(false);
			}
			default -> {
			}
		}
		var nu = Texts.splitNumberUnit(s);
		if (nu != null) {
			return EqualExpr.number(nu.num(), nu.unit());
		}
		return EqualExpr.literal(s);
	}

	/** Parses the inner "a .. b" of a range with the trailing unit split off. */
	private static Expression parseRangeExpression(String inner, String unit) {
		var parts = splitOnDoubleDot(inner);
		var lo = parseTerm(parts[0].trim());
		var hi = parseTerm(parts[1].trim());
		return new RangeExpr(lo, hi, unit);
	}

	private static Expression parseListExpression(String inner) {
		return new ListExpr(Texts.splitListItems(inner));
	}

	/** Splits on the first ".."; throws when absent. */
	public static String[] splitOnDoubleDot(String inner) {
		var idx = inner.indexOf("..");
		if (idx < 0) {
			throw new OpenOddException("invalid range \"%s\"".formatted(inner));
		}
		return new String[]{inner.substring(0, idx), inner.substring(idx + 2)};
	}

	/** Splits a bound body into a term and an optional unit. */
	static TermUnit parseTermWithUnit(String s) {
		var tokens = tokenizeTerm(s);
		if (tokens.length == 0) {
			throw new OpenOddException("missing term in expression \"%s\"".formatted(s));
		}
		var pt = parseTermTokens(tokens);
		var unit = String.join(" ", pt.rest());
		return new TermUnit(pt.term(), unit);
	}

	record TermUnit(Term term, String unit) {
	}

	private static String[] tokenizeTerm(String s) {
		var trimmed = s.strip();
		if (trimmed.isEmpty()) {
			return new String[0];
		}
		return trimmed.split("\\s+");
	}

	record ParsedTerm(Term term, String[] rest) {
	}

	/**
	 * Consumes tokens forming a term and returns the remaining tokens,
	 * which are interpreted as the unit. Accepts both spaced
	 * ("2 * ego_width") and glued ("1.75*ego_width") operator spellings.
	 */
	private static ParsedTerm parseTermTokens(String[] tokens) {
		if (tokens.length == 0) {
			throw new OpenOddException("missing factor");
		}
		var tok = tokens[0];
		var rest = new ArrayList<String>();
		for (var i = 1; i < tokens.length; i++) {
			rest.add(tokens[i]);
		}

		// Glued binary operator within the first token, for example
		// "1.75*ego_width".
		var op = findGluedOp(tok);
		if (op > 0) {
			var lf = parseSingleFactor(tok.substring(0, op));
			var rf = parseSingleFactor(tok.substring(op + 1));
			return new ParsedTerm(new Term(lf, tok.charAt(op), rf), rest.toArray(new String[0]));
		}

		var left = parseSingleFactor(tok);
		// Spaced binary operator: "1.75 * ego_width".
		if (rest.size() > 1 && (rest.get(0).equals("*") || rest.get(0).equals("/"))) {
			var opChar = rest.get(0).charAt(0);
			var rhs = rest.get(1);
			if (findGluedOp(rhs) > 0) {
				// Mixed spelling such as "2 * 1.75*ego_width" is not part
				// of the grammar; reject it.
				throw new OpenOddException("invalid term: unexpected operator in \"%s\"".formatted(rhs));
			}
			var right = parseSingleFactor(rhs);
			return new ParsedTerm(new Term(left, opChar, right),
					rest.subList(2, rest.size()).toArray(new String[0]));
		}
		return new ParsedTerm(Term.of(left), rest.toArray(new String[0]));
	}

	/**
	 * Finds a top-level '*' or '/' operator inside a single token when
	 * both sides parse as factors. Returns the operator index or 0.
	 */
	static int findGluedOp(String tok) {
		for (var i = 1; i < tok.length() - 1; i++) {
			var c = tok.charAt(i);
			if (c == '*' || c == '/') {
				if (isFactorLike(tok.substring(0, i)) && isFactorLike(tok.substring(i + 1))) {
					return i;
				}
			}
		}
		return 0;
	}

	/** Reports whether s plausibly parses as a factor (number or reference). */
	static boolean isFactorLike(String s) {
		if (s.isEmpty()) {
			return false;
		}
		var nu = Texts.splitNumberUnit(s);
		if (nu != null && nu.unit().isEmpty()) {
			return true;
		}
		return isReferenceLike(s);
	}

	/**
	 * Reports whether s looks like a concept reference: an identifier
	 * possibly dotted, possibly parameter-prefixed.
	 */
	static boolean isReferenceLike(String s) {
		if (s.isEmpty()) {
			return false;
		}
		if (s.charAt(0) == '$') {
			s = s.substring(1);
		}
		if (s.isEmpty()) {
			return false;
		}
		for (var i = 0; i < s.length(); i++) {
			var c = s.charAt(i);
			if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
				continue;
			}
			if (c == '_' || c == '-' || c == '.') {
				continue;
			}
			return false;
		}
		return true;
	}

	/** Parses one number or reference. */
	static Factor parseSingleFactor(String tok) {
		var nu = Texts.splitNumberUnit(tok);
		if (nu != null && nu.unit().isEmpty()) {
			return Factor.number(nu.num());
		}
		if (isReferenceLike(tok)) {
			return Factor.ref(tok);
		}
		throw new OpenOddException("invalid factor \"%s\"".formatted(tok));
	}

	/** Parses a standalone term such as "2", "1.75*ego_width" or "$min_speed". */
	static Term parseTerm(String s) {
		var pt = parseTermTokens(tokenizeTerm(s));
		if (pt.rest().length != 0) {
			throw new OpenOddException(
					"unexpected trailing tokens [%s] in term \"%s\"".formatted(String.join(" ", pt.rest()), s));
		}
		return pt.term();
	}
}
