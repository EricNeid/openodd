// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

/**
 * Describes a measurement unit of a unit type. A value v expressed in
 * this unit converts to the base unit of the unit type as
 * {@code v_base = (v - offset) / factor}, the inverse of
 * {@code v = v_base * factor + offset}, matching Section 6.2.7.
 *
 * @param id     the unit symbol, for example "km/h"
 * @param name   the display name of the unit
 * @param factor the factor to the base unit
 * @param offset the offset to the base unit
 */
public record Unit(String id, String name, double factor, double offset) {
}
