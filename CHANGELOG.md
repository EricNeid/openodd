<!-- markdownlint-disable MD022 MD032 MD024-->
# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

* Fixed the evaluation of epistemic list values (`[a, b]`): when no
  alternative satisfied a condition, the possibility mode wrongly
  reported true and the certainty mode wrongly reported false when all
  alternatives satisfied it. `Evaluator.anyOf` now falls through with
  the verdict implied by its uncertainty mode.

## 0.2.0 - 2026-11-20

* Added three-valued classification: `OddVerdict` with `Evaluator.classify`,
  `classifyRoots` and `classifyCod`. Conditions over values the record does
  not carry evaluate to UNKNOWN under Kleene logic instead of silently to
  false; the boolean API (`evaluate`, `evaluateRoots`, `insideOdd`,
  `evaluateCod`) collapses UNKNOWN to false as before.
* Behavior change: a missing value in an EXCLUDE section now yields UNKNOWN
  for the module (previously `true`), because `NOT unknown = unknown`.

## 0.1.0 - 2026-11-19

* Initial release
