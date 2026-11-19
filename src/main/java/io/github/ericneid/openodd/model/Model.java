// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import io.github.ericneid.openodd.writer.YamlWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A complete loaded ASAM OpenODD specification: a taxonomy plus all
 * modules.
 */
public final class Model {

	/** The taxonomy of the specification. */
	public final Taxonomy taxonomy = new Taxonomy();
	/** All modules, including root modules, keyed by ID. */
	public final Map<String, Module> modules = new LinkedHashMap<>();
	/** The root module IDs. */
	public final List<String> rootIds = new ArrayList<>();
	/** Labels mapped to the IDs of the modules carrying them. */
	public final Map<String, List<String>> labels = new LinkedHashMap<>();
	/** The files that contributed to the model, in load order. */
	public final List<String> imports = new ArrayList<>();

	/** Returns the module with the given ID, or {@code null}. */
	public Module module(String id) {
		return modules.get(id);
	}

	/**
	 * Renders the model in the YAML form of ASAM OpenODD (Section 10 of
	 * the standard). The output contains the TAXONOMY element and the
	 * ODD, TOD and MODULES elements; imports have already been merged
	 * into the model and are therefore not reproduced. The output parses
	 * back into an equivalent model.
	 */
	public byte[] writeYAML() {
		return YamlWriter.writeYAML(this);
	}

	/** Renders the model in YAML form into w. */
	public void writeYAMLTo(Appendable w) {
		YamlWriter.writeYAMLTo(this, w);
	}

	/** Returns the root modules in declaration order. */
	public List<Module> rootModules() {
		var mods = new ArrayList<Module>(rootIds.size());
		for (var id : rootIds) {
			var mod = modules.get(id);
			if (mod != null) {
				mods.add(mod);
			}
		}
		return mods;
	}
}
