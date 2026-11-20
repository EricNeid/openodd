// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.value.Record;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single parsed ASAM OpenODD YAML file. Imports are recorded but not
 * followed; use a {@link Loader} to build a merged {@link Model}.
 */
public final class Document {

	/** File names referenced by the IMPORT element. */
	public final List<String> imports = new ArrayList<>();
	/** Concepts defined by the TAXONOMY element. */
	public final Taxonomy taxonomy = new Taxonomy();
	/** All modules, including root modules. */
	public final Map<String, Module> modules = new LinkedHashMap<>();
	/** Modules declared under the ODD or TOD elements, in order. */
	public final List<String> rootIds = new ArrayList<>();
	/** Labels mapped to the module IDs carrying them. */
	public final Map<String, List<String>> labels = new LinkedHashMap<>();
	/** Tabular data declared under the COD element. */
	public final List<Record> cod = new ArrayList<>();
	/** Tabular data declared under the OD element. */
	public final List<Record> od = new ArrayList<>();

	/** Creates an empty document. */
	public Document() {
	}

	/**
	 * Concatenation of the COD and OD records.
	 *
	 * @return the concatenated records
	 */
	public List<Record> codAndOd() {
		var out = new ArrayList<Record>(cod.size() + od.size());
		out.addAll(cod);
		out.addAll(od);
		return out;
	}
}
