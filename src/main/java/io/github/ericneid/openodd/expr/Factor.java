// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import io.github.ericneid.openodd.internal.Texts;

import java.util.Objects;

/**
 * A factor of a numeric term: a number or a reference to a numeric
 * taxonomy attribute or a parameter.
 */
public record Factor(double number, String ref) {

	/** A numeric factor. */
	public static Factor number(double number) {
		return new Factor(number, "");
	}

	/** A reference factor; the reference may carry a leading "$". */
	public static Factor ref(String ref) {
		return new Factor(0, Objects.requireNonNull(ref));
	}

	public Factor {
		ref = ref == null ? "" : ref;
	}

	/** Reports whether the factor references an attribute or parameter. */
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
