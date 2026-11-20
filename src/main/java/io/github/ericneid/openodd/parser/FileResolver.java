// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;


/**
 * Reads OpenODD files by name. Loaders use it to follow IMPORT
 * statements; implementations range from the operating system filesystem
 * ({@link OsResolver}) to in-memory maps for tests and embedded content
 * ({@link MapResolver}).
 */
public interface FileResolver {

	/**
	 * Reads the file content as UTF-8 text.
	 *
	 * @param name the file name to resolve
	 * @return the content of the file
	 */
	String readFile(String name);
}
