// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads files from the operating system filesystem.
 */
public final class OsResolver implements FileResolver {

	/** Creates a filesystem resolver. */
	public OsResolver() {
	}

	@Override
	public String readFile(String name) {
		try {
			return Files.readString(Path.of(name), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new OpenOddException("reading \"%s\": %s".formatted(name, e.getMessage()), e);
		}
	}
}
