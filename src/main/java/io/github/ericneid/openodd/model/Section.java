// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An INCLUDE_* or EXCLUDE_* section of a module, or a nested AND/OR
 * sub-section.
 */
public final class Section {

	/** The combining operator. */
	public final SectionOperator operator;
	/** The conditions of the section. */
	public final List<Condition> conditions = new ArrayList<>();
	/** Nested AND/OR subsections. */
	public final List<Section> subsections = new ArrayList<>();
	/** Optional key/value metadata. */
	public Map<String, String> metadata;

	public Section(SectionOperator operator) {
		this.operator = Objects.requireNonNull(operator);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Section other)) {
			return false;
		}
		return operator == other.operator
				&& conditions.equals(other.conditions)
				&& subsections.equals(other.subsections);
	}

	@Override
	public int hashCode() {
		return Objects.hash(operator, conditions, subsections);
	}

	@Override
	public String toString() {
		return operator + " " + conditions + " " + subsections;
	}
}
