<!-- markdownlint-disable MD022 MD032 MD024-->
# Changelog

All notable changes to this project will be documented in this file.

## Unreleased
* Initial release — complete 1:1 port of
  [go-openodd](https://github.com/EricNeid/go-openodd) to Java 21
* Sources restructured into Java-idiomatic role packages (`model`,
  `expr`, `value`, `parser`, `eval`, `validation`, `writer`,
  `internal`); the root package keeps the `OpenOdd` facade and
  `OpenOddException`. Implementation classes that cross package
  boundaries are public now.
* Data model for ASAM OpenODD 1.0.0 taxonomies (records, attributes,
  categoricals with constraint-defined literals, user-defined types,
  parameters), ODD modules with INCLUDE/EXCLUDE sections and expressions
* YAML parser and writer, COD/OD readers in CSV and YAML form, optional
  manifest files
* Evaluation engine with unit conversion and uncertain values
  (possibility and certainty semantics)
* Validator for references, unit compatibility, labels and module cycles
* Writer and evaluator outputs verified byte-identical with the Go
  reference implementation
