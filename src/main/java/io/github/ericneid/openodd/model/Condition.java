// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import io.github.ericneid.openodd.expr.Expression;

import java.util.Map;
import java.util.Objects;

/**
 * A single proposition of a section: a reference to a taxonomy concept,
 * module or label together with a boolean expression.
 */
public final class Condition {

	/** The reference as written, for example "wind_speed" or "bad_weather". */
	public final String ref;
	/** The boolean expression of the condition. */
	public final Expression expr;
	/** Optional key/value pairs. */
	public final Map<String, String> metadata;

	public Condition(String ref, Expression expr, Map<String, String> metadata) {
		this.ref = ref;
		this.expr = expr;
		this.metadata = metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Condition other)) {
			return false;
		}
		return ref.equals(other.ref) && expr.equals(other.expr);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ref, expr);
	}

	@Override
	public String toString() {
		return ref + ": " + expr;
	}
}
