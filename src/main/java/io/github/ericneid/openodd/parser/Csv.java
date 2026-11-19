// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal CSV reader with the semantics of Go's encoding/csv: double
 * quoted fields with embedded delimiters, escaped quotes (""), embedded
 * newlines and commas, and leading space trimming. Blank lines are
 * ignored.
 */
final class Csv {

	private Csv() {
	}

	/** Reads all rows; returns an empty list for empty input. */
	static List<List<String>> readAll(Reader reader) {
		var rows = new ArrayList<List<String>>();
		try (var br = reader instanceof BufferedReader b ? b : new BufferedReader(reader)) {
			var row = new ArrayList<String>();
			var cell = new StringBuilder();
			var inQuotes = false;
			var justClosedQuote = false;
			var cellStarted = false;
			var rowStarted = false;
			int c;
			while ((c = br.read()) != -1) {
				if (inQuotes) {
					if (c == '"') {
						inQuotes = false;
						justClosedQuote = true;
					} else {
						cell.append((char) c);
					}
					continue;
				}
				if (justClosedQuote) {
					justClosedQuote = false;
					if (c == '"') {
						// Escaped quote: stay inside the quoted field.
						cell.append('"');
						inQuotes = true;
						continue;
					}
					if (c == ',') {
						row.add(cell.toString());
						cell.setLength(0);
						cellStarted = false;
						rowStarted = true;
						continue;
					}
					if (c == '\n') {
						row.add(cell.toString());
						rows.add(row);
						row = new ArrayList<>();
						cell.setLength(0);
						cellStarted = false;
						rowStarted = false;
						continue;
					}
					if (c == '\r') {
						continue;
					}
					// Stray characters after the closing quote are data.
					cell.append((char) c);
					cellStarted = true;
					continue;
				}
				switch (c) {
					case '"' -> {
						inQuotes = true;
						cellStarted = true;
					}
					case ',' -> {
						row.add(cell.toString());
						cell.setLength(0);
						cellStarted = false;
						rowStarted = true;
					}
					case '\r' -> {
						// Swallow the carriage return of CRLF line endings.
					}
					case '\n' -> {
						if (!rowStarted && !cellStarted) {
							// Blank lines are ignored.
							cell.setLength(0);
							continue;
						}
						row.add(cell.toString());
						rows.add(row);
						row = new ArrayList<>();
						cell.setLength(0);
						cellStarted = false;
						rowStarted = false;
					}
					default -> {
						if (!cellStarted && (c == ' ' || c == '\t')) {
							// TrimLeadingSpace: skip leading whitespace.
							continue;
						}
						cell.append((char) c);
						cellStarted = true;
					}
				}
			}
			if (inQuotes) {
				throw new OpenOddException("invalid CSV: unterminated quoted field");
			}
			if (cell.length() > 0 || cellStarted || rowStarted) {
				row.add(cell.toString());
				rows.add(row);
			}
			return rows;
		} catch (IOException e) {
			throw new OpenOddException("invalid CSV: " + e.getMessage(), e);
		}
	}
}
