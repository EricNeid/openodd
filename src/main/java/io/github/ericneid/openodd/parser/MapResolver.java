// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;

import java.util.Map;

/**
 * Serves file content from an in-memory map. Useful for tests and for
 * programmatically assembled specifications.
 */
public final class MapResolver implements FileResolver {

	private final Map<String, String> files;

	public MapResolver(Map<String, String> files) {
		this.files = files;
	}

	@Override
	public String readFile(String name) {
		var data = files.get(name);
		if (data == null) {
			throw new OpenOddException("reading \"%s\": file does not exist".formatted(name));
		}
		return data;
	}
}
