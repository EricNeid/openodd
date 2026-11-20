// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import io.github.ericneid.openodd.internal.Texts;

import java.util.Objects;

/**
 * A factor of a numeric term: a number or a reference to a numeric
 * taxonomy attribute or a parameter.
 *
 * @param number the numeric value
 * @param ref    the attribute or parameter reference, "" for a numeric factor
 */
public record Factor(double number, String ref) {

	/**
	 * A numeric factor.
	 *
	 * @param number the numeric value
	 * @return the factor
	 */
	public static Factor number(double number) {
		return new Factor(number, "");
	}

	/**
	 * A reference factor; the reference may carry a leading "$".
	 *
	 * @param ref the attribute or parameter reference
	 * @return the factor
	 */
	public static Factor ref(String ref) {
		return new Factor(0, Objects.requireNonNull(ref));
	}

	/** Normalizes a null reference to "". */
	public Factor {
		ref = ref == null ? "" : ref;
	}

	/**
	 * Reports whether the factor references an attribute or parameter.
	 *
	 * @return whether the factor is a reference
	 */
	public boolean isRef() {
		return !ref.isEmpty();
	}

	@Override
	public String toString() {
		if (!ref.isEmpty()) {
			return ref;
		}
		return Texts.formatNumber(number);
	}
}
