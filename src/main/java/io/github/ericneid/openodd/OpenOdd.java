// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd;

import io.github.ericneid.openodd.eval.EvalOptions;
import io.github.ericneid.openodd.eval.Evaluator;
import io.github.ericneid.openodd.model.Document;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.parser.Cod;
import io.github.ericneid.openodd.parser.FileResolver;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.parser.Manifest;
import io.github.ericneid.openodd.parser.YamlParser;
import io.github.ericneid.openodd.validation.Validator;
import io.github.ericneid.openodd.value.Record;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Entry points of the library, mirroring the function names of the
 * reference implementation: loading, parsing, validation and evaluation
 * of ASAM OpenODD specifications.
 */
public final class OpenOdd {

	private OpenOdd() {
	}

	/**
	 * Loads a YAML specification through the operating system filesystem
	 * into a merged model, following all IMPORT statements.
	 *
	 * @param name the file name of the specification
	 * @return the merged model
	 */
	public static Model loadFile(String name) {
		return Loader.loadFile(name);
	}

	/**
	 * Loads multiple files through the operating system filesystem into a
	 * single model, following all IMPORT statements.
	 *
	 * @param paths the file names of the specifications
	 * @return the merged model
	 */
	public static Model loadFiles(String... paths) {
		return Loader.loadFiles(paths);
	}

	/**
	 * Creates a loader reading through the given resolver.
	 *
	 * @param resolver the resolver reading the file contents
	 * @return the loader
	 */
	public static Loader newLoader(FileResolver resolver) {
		return new Loader(resolver);
	}

	/**
	 * Parses a single YAML document without following imports.
	 *
	 * @param src the YAML text
	 * @return the parsed document
	 */
	public static Document parseYAML(String src) {
		return YamlParser.parse(src);
	}

	/**
	 * Parses a single YAML document without following imports.
	 *
	 * @param src the UTF-8 encoded YAML text
	 * @return the parsed document
	 */
	public static Document parseYAML(byte[] src) {
		return YamlParser.parse(new String(src, StandardCharsets.UTF_8));
	}

	/**
	 * Parses a COD/OD table in CSV form.
	 *
	 * @param r the source of the CSV text
	 * @return the parsed table
	 */
	public static Cod parseCODCSV(Reader r) {
		return Cod.parseCsv(r);
	}

	/**
	 * Parses a COD/OD table in CSV form.
	 *
	 * @param csv the CSV text
	 * @return the parsed table
	 */
	public static Cod parseCODCSV(String csv) {
		return Cod.parseCsvText(csv);
	}

	/**
	 * Parses COD/OD records from a YAML table.
	 *
	 * @param src the YAML text
	 * @return the parsed records
	 */
	public static List<Record> parseCODYAML(String src) {
		return Cod.parseYaml(src);
	}

	/**
	 * Parses a COD/OD manifest file in CSV form.
	 *
	 * @param r the source of the CSV text
	 * @return the parsed manifest
	 */
	public static Manifest parseManifestCSV(Reader r) {
		return Manifest.parseCsv(r);
	}

	/**
	 * Loads the COD/OD data files referenced by a manifest.
	 *
	 * @param m        the manifest describing the data files
	 * @param resolver the resolver reading the file contents
	 * @param dir      the base directory of the referenced files
	 * @return the loaded table
	 */
	public static Cod loadCODWithManifest(Manifest m, FileResolver resolver, String dir) {
		return Manifest.loadCodWithManifest(m, resolver, dir);
	}

	/**
	 * Checks a model against the semantic constraints of the standard;
	 * the first problem is reported through an OpenOddException.
	 *
	 * @param m the model to check
	 */
	public static void validate(Model m) {
		Validator.validate(m);
	}

	/**
	 * Creates an evaluator over the model.
	 *
	 * @param m       the model with the ODD to evaluate against
	 * @param options the evaluation options, null for the defaults
	 * @return the evaluator
	 */
	public static Evaluator newEvaluator(Model m, EvalOptions options) {
		return new Evaluator(m, options);
	}
}
