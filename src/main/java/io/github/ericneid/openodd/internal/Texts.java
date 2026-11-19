// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * String helpers shared by the expression, value and table parsers.
 */
public final class Texts {

	private Texts() {
	}

	/**
	 * Renders a number the way the standard writes it: integral values
	 * without a decimal point, everything else with the shortest exact
	 * representation, mirroring Go's {@code strconv.FormatFloat(f, 'g', -1, 64)}.
	 */
	public static String formatNumber(double f) {
		if (f == (long) f && f > -1e15 && f < 1e15) {
			return Long.toString((long) f);
		}
		if (Double.isNaN(f)) {
			return "NaN";
		}
		if (Double.isInfinite(f)) {
			return f > 0 ? "+Inf" : "-Inf";
		}
		// Shortest round-tripping digits, reformatted with Go's %g rules:
		// scientific notation when the decimal exponent is below -4 or
		// at least 21, fixed notation otherwise.
		var s = Double.toString(f);
		var sign = "";
		if (s.startsWith("-")) {
			sign = "-";
			s = s.substring(1);
		}
		var exp10 = 0L;
		var e = s.indexOf('E');
		if (e >= 0) {
			exp10 = Long.parseLong(s.substring(e + 1));
			s = s.substring(0, e);
		}
		var dot = s.indexOf('.');
		var intPart = dot < 0 ? s : s.substring(0, dot);
		var fracPart = dot < 0 ? "" : s.substring(dot + 1);
		var digits = (intPart + fracPart);
		var dp = intPart.length() + exp10; // value = 0.<digits> * 10^dp
		// Strip leading zeros (keep dp aligned) and trailing zeros.
		var first = 0;
		while (first < digits.length() - 1 && digits.charAt(first) == '0') {
			first++;
			dp--;
		}
		var last = digits.length();
		while (last > first + 1 && digits.charAt(last - 1) == '0') {
			last--;
		}
		digits = digits.substring(first, last);
		if (digits.equals("0")) {
			return sign + "0";
		}
		var exp = dp - 1;
		if (exp < -4 || exp >= 6) {
			// Scientific: d[.ddd]e±XX with at least two exponent digits.
			var sb = new StringBuilder(sign);
			sb.append(digits.charAt(0));
			if (digits.length() > 1) {
				sb.append('.').append(digits.substring(1));
			}
			sb.append('e');
			if (exp < 0) {
				sb.append('-');
				exp = -exp;
			} else {
				sb.append('+');
			}
			if (exp < 10) {
				sb.append('0');
			}
			return sb.append(exp).toString();
		}
		var sb = new StringBuilder(sign);
		if (dp <= 0) {
			sb.append("0.");
			for (var i = 0; i < -dp; i++) {
				sb.append('0');
			}
			sb.append(digits);
		} else if (dp >= digits.length()) {
			sb.append(digits);
			for (var i = 0; i < dp - digits.length(); i++) {
				sb.append('0');
			}
		} else {
			sb.append(digits, 0, (int) dp).append('.').append(digits.substring((int) dp));
		}
		return sb.toString();
	}

	/** Prefixes a unit symbol with a separating space. */
	public static String suffixUnit(String unit) {
		if (unit == null || unit.isEmpty()) {
			return "";
		}
		return " " + unit;
	}

	/** Reports whether s is one of the accepted spellings of the missing-value keyword. */
	public static boolean isUnknownKeyword(String s) {
		if (s == null) {
			return true;
		}
		return switch (s.trim().toLowerCase()) {
			case "unknown", "none", "null", "undefined", "" -> true;
			default -> false;
		};
	}

	/**
	 * Splits a string into a leading number and a trailing unit. Returns
	 * {@code null} when the string does not start with a number.
	 */
	public static NumberUnit splitNumberUnit(String s) {
		s = s.trim();
		var i = 0;
		if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
			i++;
		}
		var digits = 0;
		var dot = false;
		var exp = false;
		loop:
		while (i < s.length()) {
			var c = s.charAt(i);
			if (c >= '0' && c <= '9') {
				digits++;
			} else if (c == '.' && !dot && !exp) {
				dot = true;
			} else if ((c == 'e' || c == 'E') && digits > 0 && !exp) {
				var rest = s.substring(i + 1);
				if (rest.isEmpty()) {
					return null;
				}
				if (rest.charAt(0) == '+' || rest.charAt(0) == '-') {
					rest = rest.substring(1);
				}
				if (rest.isEmpty() || rest.charAt(0) < '0' || rest.charAt(0) > '9') {
					return null;
				}
				exp = true;
			} else {
				break loop;
			}
			i++;
		}
		if (digits == 0) {
			return null;
		}
		var numStr = s.substring(0, i);
		var unit = s.substring(i).trim();
		double f;
		try {
			f = Double.parseDouble(numStr);
		} catch (NumberFormatException e) {
			return null;
		}
		return new NumberUnit(f, unit);
	}

	/** Result of {@link #splitNumberUnit}. */
	public record NumberUnit(double num, String unit) {
	}

	/**
	 * Splits a comma separated list, tolerating quoted items; empty items
	 * are dropped.
	 */
	public static List<String> splitListItems(String s) {
		if (s == null || s.isEmpty()) {
			return List.of();
		}
		var items = new ArrayList<String>();
		var cur = new StringBuilder();
		var inQuote = '\0';
		for (var i = 0; i < s.length(); i++) {
			var c = s.charAt(i);
			if (inQuote != 0) {
				if (c == inQuote) {
					inQuote = 0;
				} else {
					cur.append(c);
				}
			} else if (c == '"' || c == '\'') {
				inQuote = c;
			} else if (c == ',') {
				items.add(cur.toString().trim());
				cur.setLength(0);
			} else {
				cur.append(c);
			}
		}
		items.add(cur.toString().trim());
		var out = new ArrayList<String>(items.size());
		for (var it : items) {
			if (!it.isEmpty()) {
				out.add(it);
			}
		}
		return out;
	}
}
