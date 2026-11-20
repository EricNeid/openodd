// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.eval;

import java.util.Objects;

/**
 * Configuration of an {@link Evaluator}.
 *
 * @param uncertainty the uncertainty mode; {@code null} means
 *                    {@link UncertaintyMode#POSSIBILITY}
 */
public record EvalOptions(UncertaintyMode uncertainty) {

	/** Default options: possibility semantics. */
	public static final EvalOptions DEFAULT = new EvalOptions(UncertaintyMode.POSSIBILITY);

	/** Normalizes a null uncertainty mode to possibility. */
	public EvalOptions {
		uncertainty = Objects.requireNonNullElse(uncertainty, UncertaintyMode.POSSIBILITY);
	}

	/**
	 * Creates options with the given uncertainty mode.
	 *
	 * @param uncertainty the uncertainty mode
	 * @return the options
	 */
	public static EvalOptions of(UncertaintyMode uncertainty) {
		return new EvalOptions(uncertainty);
	}
}
