// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.model.ConceptIndex;
import io.github.ericneid.openodd.model.Document;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.model.Section;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads ASAM OpenODD YAML specifications into a merged {@link Model},
 * following IMPORT statements. Imports are resolved relative to the
 * directory of the importing file. Each file is loaded at most once;
 * cyclic imports are reported as errors.
 */
public final class Loader {

	private final FileResolver resolver;
	private final Model model = new Model();
	private ConceptIndex index;
	private final Map<String, Document> docs = new HashMap<>();
	private final Set<String> loading = new HashSet<>();

	/**
	 * Creates a loader reading through the given resolver. A
	 * {@code null} resolver defaults to the operating system filesystem.
	 *
	 * @param resolver the resolver reading the file contents
	 */
	public Loader(FileResolver resolver) {
		this.resolver = resolver == null ? new OsResolver() : resolver;
	}

	/**
	 * Loads the named file through the operating system filesystem,
	 * following all IMPORT statements.
	 *
	 * @param name the file name of the specification
	 * @return the merged model
	 */
	public static Model loadFile(String name) {
		return new Loader(null).load(name);
	}

	/**
	 * Loads multiple files through the operating system filesystem into
	 * a single model, following all IMPORT statements.
	 *
	 * @param paths the file names of the specifications
	 * @return the merged model
	 */
	public static Model loadFiles(String... paths) {
		var l = new Loader(null);
		for (var p : paths) {
			l.load(p);
		}
		return l.model();
	}

	/**
	 * Parses and merges the named file and, recursively, its imports.
	 * Relative import paths are resolved against the directory of the
	 * importing file. The returned model is complete once every
	 * top-level load call has returned; use {@link #model()} afterwards.
	 *
	 * @param name the file name to load
	 * @return the merged model
	 */
	public Model load(String name) {
		loadFile(name, "");
		return model();
	}

	/**
	 * Finalizes and returns the merged model. It computes label
	 * memberships and marks modules as roots: modules declared under ODD
	 * or TOD elements are always roots, and modules under MODULES are
	 * roots unless another module references them, directly or through a
	 * label (Section 6.4.4.1 of the standard). Recomputes these flags from
	 * scratch and may be called at any time, also between load calls.
	 *
	 * @return the merged model
	 */
	public Model model() {
		// Modules referenced by other modules, directly or through
		// labels, cannot be roots.
		var referenced = new HashSet<String>();
		for (var mod : model.modules.values()) {
			if (mod.include != null) {
				collectReferences(mod.include, referenced);
			}
			if (mod.exclude != null) {
				collectReferences(mod.exclude, referenced);
			}
		}
		model.rootIds.clear();
		for (var e : model.modules.entrySet()) {
			e.getValue().root = !"Standard".equals(e.getValue().kind) || !referenced.contains(e.getKey());
			if (e.getValue().root) {
				model.rootIds.add(e.getKey());
			}
		}
		Collections.sort(model.rootIds);
		this.index = new ConceptIndex(model.taxonomy);
		return model;
	}

	/**
	 * The concept index of the merged model; available after loading.
	 *
	 * @return the concept index
	 */
	public ConceptIndex index() {
		return index;
	}

	private void collectReferences(Section sec, Set<String> referenced) {
		for (var cond : sec.conditions) {
			if (model.modules.containsKey(cond.ref)) {
				referenced.add(cond.ref);
			}
			var mods = model.labels.get(cond.ref);
			if (mods != null) {
				referenced.addAll(mods);
			}
		}
		for (var sub : sec.subsections) {
			collectReferences(sub, referenced);
		}
	}

	private void loadFile(String name, String dir) {
		var full = name;
		if (!dir.isEmpty() && !Path.of(name).isAbsolute()) {
			full = Path.of(dir, name).toString();
		}
		full = clean(full);
		if (loading.contains(full)) {
			throw new OpenOddException("cyclic import of \"%s\"".formatted(full));
		}
		if (docs.containsKey(full)) {
			return;
		}
		String data;
		try {
			data = resolver.readFile(full);
		} catch (RuntimeException e) {
			throw new OpenOddException("reading \"%s\": %s".formatted(full, e.getMessage()), e);
		}
		Document doc;
		try {
			doc = YamlParser.parse(data);
		} catch (OpenOddException e) {
			throw new OpenOddException("%s: %s".formatted(full, e.getMessage()), e);
		}
		docs.put(full, doc);
		loading.add(full);
		try {
			model.imports.add(full);
			var importDir = parentDir(full);
			for (var imp : doc.imports) {
				loadFile(imp, importDir);
			}
			merge(doc);
		} finally {
			loading.remove(full);
		}
	}

	/** Mirrors Go's filepath.Clean on POSIX paths. */
	static String clean(String path) {
		var cleaned = Path.of(path).normalize().toString();
		return cleaned.isEmpty() ? "." : cleaned;
	}

	/** Mirrors Go's filepath.Dir on POSIX paths. */
	static String parentDir(String path) {
		var cleaned = clean(path);
		var i = cleaned.lastIndexOf('/');
		if (i < 0) {
			return ".";
		}
		if (i == 0) {
			return "/";
		}
		return cleaned.substring(0, i);
	}

	/** Merges a parsed document into the model. */
	private void merge(Document doc) {
		for (var e : doc.taxonomy.concepts.entrySet()) {
			if (model.taxonomy.concepts.containsKey(e.getKey())) {
				throw new OpenOddException("duplicate concept \"%s\"".formatted(e.getKey()));
			}
			model.taxonomy.concepts.put(e.getKey(), e.getValue());
		}
		model.taxonomy.roots.putAll(doc.taxonomy.roots);
		for (var e : doc.modules.entrySet()) {
			if (model.modules.containsKey(e.getKey())) {
				throw new OpenOddException("duplicate module \"%s\"".formatted(e.getKey()));
			}
			model.modules.put(e.getKey(), e.getValue());
		}
		for (var e : doc.labels.entrySet()) {
			model.labels.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
		}
	}
}
