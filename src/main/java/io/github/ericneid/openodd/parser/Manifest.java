// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Binds COD/OD data files and their columns to taxonomy concepts
 * (Section 8.3.4.2 of the standard).
 */
public final class Manifest {

	/**
	 * One row of a manifest file.
	 *
	 * @param fieldId    uniquely identifies the field within the manifest
	 * @param fileName   the data file, relative to the manifest location
	 * @param type       "taxonomy", "cod" or "od"
	 * @param column     the column name in the data file
	 * @param taxonomyId the taxonomy concept represented by the column
	 * @param unit       the unit of numeric values
	 * @param comment    a free-form description
	 */
	public record Entry(
			String fieldId,
			String fileName,
			String type,
			String column,
			String taxonomyId,
			String unit,
			String comment) {
	}

	/** Creates an empty manifest. */
	public Manifest() {
	}

	/** The manifest entries in file order. */
	public final List<Entry> entries = new ArrayList<>();

	/**
	 * Parses a manifest file in CSV form. Requires the columns FIELD_ID,
	 * FILE_NAME, TYPE, COLUMN, TAXONOMY_ID, UNIT and COMMENT in that
	 * order.
	 *
	 * @param reader the source of the CSV text
	 * @return the parsed manifest
	 */
	public static Manifest parseCsv(Reader reader) {
		var rows = Csv.readAll(reader);
		if (rows.isEmpty()) {
			throw new OpenOddException("empty manifest: missing header row");
		}
		var header = new ArrayList<String>();
		for (var h : rows.get(0)) {
			var cell = h;
			if (cell.startsWith("\ufeff")) {
				cell = cell.substring(1);
			}
			header.add(cell.trim().toUpperCase());
		}
		var required = new String[]{"FIELD_ID", "FILE_NAME", "TYPE", "COLUMN", "TAXONOMY_ID", "UNIT", "COMMENT"};
		for (var i = 0; i < required.length; i++) {
			var got = i < header.size() ? header.get(i) : "";
			if (!got.equals(required[i])) {
				throw new OpenOddException(
						"manifest header column %d is \"%s\", want \"%s\"".formatted(i + 1, got, required[i]));
			}
		}
		var m = new Manifest();
		for (var i = 1; i < rows.size(); i++) {
			var row = rows.get(i);
			if (row.isEmpty() || allEmpty(row)) {
				continue;
			}
			if (row.size() < required.length) {
				throw new OpenOddException(
						"manifest row %d has %d columns, want %d".formatted(i + 1, row.size(), required.length));
			}
			m.entries.add(new Entry(
					row.get(0).trim(),
					row.get(1).trim(),
					row.get(2).trim().toLowerCase(),
					row.get(3).trim(),
					row.get(4).trim(),
					row.get(5).trim(),
					row.get(6).trim()));
		}
		return m;
	}

	private static boolean allEmpty(List<String> row) {
		for (var c : row) {
			if (!c.trim().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Lists the taxonomy files referenced by the manifest.
	 *
	 * @return the referenced file names
	 */
	public List<String> taxonomyFiles() {
		var files = new ArrayList<String>();
		for (var e : entries) {
			if (e.type().equals("taxonomy") && !e.fileName().isEmpty()) {
				files.add(e.fileName());
			}
		}
		return files;
	}

	/**
	 * Lists the COD/OD data files referenced by the manifest.
	 *
	 * @return the referenced file names, without duplicates
	 */
	public List<String> dataFiles() {
		var files = new ArrayList<String>();
		var seen = new LinkedHashSet<String>();
		for (var e : entries) {
			if ((e.type().equals("cod") || e.type().equals("od")) && !e.fileName().isEmpty()
					&& seen.add(e.fileName())) {
				files.add(e.fileName());
			}
		}
		return files;
	}

	/** Extracts the column interpretation of one data file. */
	Map<String, Cod.ColumnMapping> columnMappings(String fileName) {
		var mapping = new LinkedHashMap<String, Cod.ColumnMapping>();
		for (var e : entries) {
			if (!e.fileName().equals(fileName) || e.taxonomyId().isEmpty()) {
				continue;
			}
			mapping.put(e.column(), new Cod.ColumnMapping(e.taxonomyId(), e.unit()));
		}
		return mapping;
	}

	/**
	 * Loads all COD/OD data files referenced by a manifest through the
	 * resolver and merges them into a single table. Relative file names
	 * are resolved against dir, the directory of the manifest file. The
	 * returned table is not yet resolved against a taxonomy; call
	 * {@link Cod#resolve(ConceptIndex)} afterwards.
	 *
	 * @param m        the manifest describing the data files
	 * @param resolver the resolver reading the file contents, null for the filesystem
	 * @param dir      the base directory of the referenced files
	 * @return the merged table
	 */
	public static Cod loadCodWithManifest(Manifest m, FileResolver resolver, String dir) {
		if (resolver == null) {
			resolver = new OsResolver();
		}
		Cod combined = null;
		for (var file : m.dataFiles()) {
			var name = dir != null && !dir.isEmpty() ? dir + "/" + file : file;
			String data;
			try {
				data = resolver.readFile(name);
			} catch (OpenOddException e) {
				throw new OpenOddException("reading \"%s\": %s".formatted(name, e.getMessage()), e);
			}
			Cod cod;
			try {
				cod = Cod.parseCsv(new java.io.StringReader(data), m.columnMappings(file));
			} catch (OpenOddException e) {
				throw new OpenOddException("%s: %s".formatted(name, e.getMessage()), e);
			}
			if (combined == null) {
				combined = cod;
			} else {
				combined.merge(cod);
			}
		}
		return combined != null ? combined : new Cod();
	}
}
