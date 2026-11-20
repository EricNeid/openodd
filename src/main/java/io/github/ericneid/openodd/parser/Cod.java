// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.model.Concept;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.value.Record;
import io.github.ericneid.openodd.value.Units;
import io.github.ericneid.openodd.value.Value;
import io.github.ericneid.openodd.value.ValueKind;
import io.github.ericneid.openodd.value.Values;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A table of current operational domain (COD) or operational domain (OD)
 * measurements.
 */
public final class Cod {

	/** The columns of the table in header order. */
	public final List<Column> columns = new ArrayList<>();
	/** The records of the table in row order. */
	public final List<Record> records = new ArrayList<>();

	/** Creates an empty table. */
	public Cod() {
	}

	/** The manifest-declared interpretation of one column. */
	record ColumnMapping(String taxonomyId, String unit) {
	}

	/**
	 * Reads COD/OD data in CSV form (Section 8.3.2 of the standard). The
	 * header row names each column as "NAME", "NAME;unit" or
	 * "NAME;type", including the TEMPORAL_EXTENT and SPATIAL_EXTENT
	 * columns. Concept names are matched case-insensitively when the
	 * table is resolved against a taxonomy.
	 *
	 * @param reader the source of the CSV text
	 * @return the parsed table
	 */
	public static Cod parseCsv(Reader reader) {
		return parseCsv(reader, Map.of());
	}

	/** Implements {@link #parseCsv(Reader)} with a manifest column mapping. */
	static Cod parseCsv(Reader reader, Map<String, ColumnMapping> mapping) {
		var rows = Csv.readAll(reader);
		if (rows.isEmpty()) {
			throw new OpenOddException("empty CSV: missing header row");
		}
		var header = new ArrayList<String>(rows.get(0));
		if (!header.isEmpty() && header.get(0).startsWith("\ufeff")) {
			header.set(0, header.get(0).substring(1));
		}
		var cod = new Cod();
		for (var h : header) {
			h = h.trim();
			if (h.isEmpty()) {
				throw new OpenOddException("empty column header");
			}
			var nm = splitColumnHeader(h);
			var name = nm[0];
			var marker = nm[1];
			var isTemporal = false;
			var isSpatial = false;
			switch (name.toUpperCase()) {
				case "TEMPORAL_EXTENT" -> isTemporal = true;
				case "SPATIAL_EXTENT" -> isSpatial = true;
				default -> {
					var m = mapping.get(h);
					if (m == null) {
						m = mapping.get(name);
					}
					if (m != null) {
						// The manifest may name the column with or
						// without its ";unit" marker.
						name = m.taxonomyId();
						marker = m.unit();
					}
				}
			}
			cod.columns.add(new Column(h, name, isTemporal || isSpatial ? "" : marker, isTemporal, isSpatial));
		}
		for (var i = 1; i < rows.size(); i++) {
			var row = rows.get(i);
			var rec = new Record();
			var empty = true;
			for (var j = 0; j < row.size() && j < cod.columns.size(); j++) {
				var col = cod.columns.get(j);
				var cell = row.get(j).trim();
				if (col.temporal) {
					if (!cell.isEmpty()) {
						empty = false;
						rec.temporal(cell);
					}
					continue;
				}
				if (col.spatial) {
					if (!cell.isEmpty()) {
						empty = false;
						rec.spatial(cell);
					}
					continue;
				}
				if (cell.isEmpty()) {
					continue;
				}
				empty = false;
				var uu = interpretMarker(col.unit);
				Value v;
				try {
					v = Values.parseCellValue(cell, uu[0], uu[1]);
				} catch (OpenOddException err) {
					throw new OpenOddException(
							"row %d, column \"%s\": %s".formatted(i + 1, col.header, err.getMessage()), err);
				}
				rec.values().put(col.name, v);
			}
			if (empty && rec.temporal().isEmpty() && rec.spatial().isEmpty()) {
				continue;
			}
			cod.records.add(rec);
		}
		return cod;
	}

	/**
	 * Splits "NAME;marker" into name and marker. The marker is a unit
	 * symbol or a type name; both are optional.
	 */
	static String[] splitColumnHeader(String header) {
		var i = header.indexOf(';');
		if (i < 0) {
			return new String[]{header.trim(), ""};
		}
		return new String[]{header.substring(0, i).trim(), header.substring(i + 1).trim()};
	}

	/**
	 * Maps a column marker to a unit symbol and unit type. Registered
	 * unit symbols resolve to their unit type; type markers such as
	 * "categorical_literal" or "boolean" carry no unit.
	 */
	static String[] interpretMarker(String marker) {
		marker = marker == null ? "" : marker.trim();
		if (marker.isEmpty()) {
			return new String[]{"", ""};
		}
		var ut = Units.unitTypeOfUnit(marker);
		if (ut != null) {
			return new String[]{marker, ut};
		}
		switch (marker.toLowerCase()) {
			case "categorical_literal", "categorical", "boolean", "string", "float", "integer",
					"long", "double", "shapefile", "record" -> {
				// Type markers carry no unit; the unit type comes from
				// the taxonomy concept once resolved.
				return new String[]{"", ""};
			}
			default -> {
			}
		}
		// Unknown symbol: keep it as the unit; conversion is only
		// possible when the taxonomy declares a matching unit type
		// without registered units.
		return new String[]{marker, ""};
	}

	/**
	 * Canonicalizes the concept keys of all records against the
	 * taxonomy. Assigns unit types to unitless numeric values from the
	 * concepts and flattens record-structured cells under their concept
	 * paths. The first resolution error encountered is thrown.
	 *
	 * @param idx the concept index to resolve against
	 */
	public void resolve(ConceptIndex idx) {
		for (var i = 0; i < records.size(); i++) {
			var rec = records.get(i);
			var resolved = new LinkedHashMap<String, Value>();
			for (var e : rec.values().entrySet()) {
				Concept c;
				try {
					c = idx.resolveColumn(e.getKey());
				} catch (OpenOddException err) {
					throw new OpenOddException(
							"record %d, column \"%s\": %s".formatted(i + 1, e.getKey(), err.getMessage()), err);
				}
				var v = Values.withConcept(e.getValue(), c);
				resolved.put(c.id, v);
				if (v.kind() == ValueKind.RECORD) {
					Values.recordFieldValues(v, c.id, resolved);
				}
			}
			rec.values().clear();
			rec.values().putAll(resolved);
		}
	}

	/**
	 * Parses COD/OD data in the YAML form of Section 10.3: a list of
	 * records whose keys are "NAME;marker" column headers.
	 *
	 * @param data the YAML text
	 * @return the parsed records
	 */
	public static List<Record> parseYaml(String data) {
		return YamlParser.parse(data).codAndOd();
	}

	/**
	 * Canonicalizes concept keys of YAML-parsed records against the
	 * taxonomy, in the same way {@link #resolve(ConceptIndex)} does for
	 * CSV tables.
	 *
	 * @param records the records to canonicalize
	 * @param idx     the concept index to resolve against
	 */
	public static void resolveRecords(List<Record> records, ConceptIndex idx) {
		for (var i = 0; i < records.size(); i++) {
			var rec = records.get(i);
			var resolved = new LinkedHashMap<String, Value>();
			for (var e : rec.values().entrySet()) {
				Concept c;
				try {
					c = idx.resolveColumn(e.getKey());
				} catch (OpenOddException err) {
					throw new OpenOddException(
							"record %d, column \"%s\": %s".formatted(i + 1, e.getKey(), err.getMessage()), err);
				}
				var v = Values.withConcept(e.getValue(), c);
				resolved.put(c.id, v);
				if (v.kind() == ValueKind.RECORD) {
					Values.recordFieldValues(v, c.id, resolved);
				}
			}
			rec.values().clear();
			rec.values().putAll(resolved);
		}
	}

	/**
	 * Merges another COD into this one by taking the union of columns
	 * and appending the records (Section 8.3.2.2). Both tables must be
	 * resolved against the same taxonomy.
	 *
	 * @param other the table to merge into this one
	 */
	public void merge(Cod other) {
		var known = new java.util.HashSet<String>();
		for (var col : columns) {
			known.add(col.name);
		}
		for (var col : other.columns) {
			if (!known.contains(col.name)) {
				columns.add(col);
				known.add(col.name);
			}
		}
		records.addAll(other.records);
	}

	/**
	 * Reads COD/OD data in CSV form from a string.
	 *
	 * @param csv the CSV text
	 * @return the parsed table
	 */
	public static Cod parseCsvText(String csv) {
		return parseCsv(new StringReader(csv));
	}
}
