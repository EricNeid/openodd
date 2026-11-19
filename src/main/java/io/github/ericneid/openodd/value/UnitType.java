// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.value;

/**
 * Groups units that are convertible among each other. The ID corresponds
 * to the PhysicalDimension class of the ASAM Unit Handling Guide, for
 * example "velocity" or "precipitation_rate".
 */
public record UnitType(String id, String base) {
}
