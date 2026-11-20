<!-- markdownlint-disable MD022 MD032 MD024-->
# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

* Added three-valued classification: `OddVerdict` with `Evaluator.classify`,
  `classifyRoots` and `classifyCod`. Conditions over values the record does
  not carry evaluate to UNKNOWN under Kleene logic instead of silently to
  false; the boolean API (`evaluate`, `evaluateRoots`, `insideOdd`,
  `evaluateCod`) collapses UNKNOWN to false as before.
* Behavior change: a missing value in an EXCLUDE section now yields UNKNOWN
  for the module (previously `true`), because `NOT unknown = unknown`.

## 0.1.0 - 2026-11-19

* Initial release
