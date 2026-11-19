// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import io.github.ericneid.openodd.expr.Expression;

/**
 * Associates a taxonomy concept reference with an expression, as used in
 * categorical literal definitions.
 */
public record Constraint(String ref, Expression expr) {
}
