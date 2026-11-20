// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An ODD module: a named set of inclusion and exclusion sections over
 * taxonomy concepts, module references and labels.
 */
public final class Module {

	/** The module ID. */
	public final String id;
	/** The module title. */
	public String title = "";
	/** The module description. */
	public String description = "";
	/** Labels carried by the module. */
	public final List<String> labels = new ArrayList<>();
	/** Tags of the module. */
	public final List<String> tags = new ArrayList<>();
	/** Optional key/value metadata. */
	public Map<String, String> metadata;
	/** The is_active flag; inactive modules are ignored during evaluation. */
	public boolean active = true;
	/** Whether the module is a root module used as an entry point for inference. */
	public boolean root;
	/** "ODD", "TOD" or "Standard". */
	public final String kind;
	/** The single optional INCLUDE section. */
	public Section include;
	/** The single optional EXCLUDE section. */
	public Section exclude;

	/**
	 * Creates a module with the given ID and kind.
	 *
	 * @param id   the module ID
	 * @param kind "ODD", "TOD" or "Standard"
	 */
	public Module(String id, String kind) {
		this.id = id;
		this.kind = kind;
	}

	@Override
	public String toString() {
		return "module " + id;
	}
}
