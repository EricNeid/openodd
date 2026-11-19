// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import java.util.List;

/**
 * A CategoricalList expression such as "[fog_mist, fog_medium]". Items
 * starting with "$" are parameter references resolved against the
 * evaluated record.
 */
public record ListExpr(List<String> items) implements Expression {

	public ListExpr {
		items = items == null ? List.of() : List.copyOf(items);
	}

	@Override
	public String toString() {
		return "[" + String.join(", ", items) + "]";
	}
}
