// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

/**
 * Describes one column of a COD/OD table.
 */
public final class Column {

	/** The column title as written, including any ";marker" suffix. */
	public final String header;
	/** The concept name part of the header. */
	public final String name;
	/** The unit symbol declared by the header or manifest. */
	public final String unit;
	/** Marks the TEMPORAL_EXTENT column. */
	public final boolean temporal;
	/** Marks the SPATIAL_EXTENT column. */
	public final boolean spatial;

	/**
	 * Creates a column from a parsed header.
	 *
	 * @param header   the column title as written, including any ";marker" suffix
	 * @param name     the concept name part of the header
	 * @param unit     the unit symbol, null for none
	 * @param temporal whether the column marks the TEMPORAL_EXTENT
	 * @param spatial  whether the column marks the SPATIAL_EXTENT
	 */
	public Column(String header, String name, String unit, boolean temporal, boolean spatial) {
		this.header = header;
		this.name = name;
		this.unit = unit == null ? "" : unit;
		this.temporal = temporal;
		this.spatial = spatial;
	}
}
