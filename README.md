# openodd

A Java implementation of the [ASAM OpenODD](https://www.asam.net/standards/detail/openodd/) 1.0.0 standard
("Operational Design Domain", published 2025-04-03).

ASAM OpenODD defines a technology-independent data model for the operational
design domains of automated driving systems, plus machine-readable exchange
formats. This package is a **library** (no CLI) providing:

- **Data model** — taxonomies (records, attributes, categoricals with
  constraint-defined literals, user-defined record types, parameters), ODD
  modules with INCLUDE/EXCLUDE sections, and boolean expressions.
- **Parsers & writers** — the YAML mapping for taxonomies, modules and
  COD/OD records, including `IMPORT` following; COD/OD tables in CSV form;
  optional manifest files.
- **Evaluation engine** — decides whether a COD/OD record lies inside or
  outside an ODD, including unit conversion and uncertain values.
- **Validator** — checks reference resolution, expression/concept type
  compatibility, unit compatibility, label disjointness and module cycles.

## Requirements

Java 21 or later.

## Quickstart

Get library from Maven Central:

```kotlin
dependencies {
    implementation("io.github.ericneid:openodd:0.2.0")
}
```

To build from source:

```sh
./gradlew build
# build/libs/openodd-0.2.0.jar
```

## Usage

### Loading a specification

```java
var m = OpenOdd.loadFile("odd.yaml");
OpenOdd.validate(m);
```

Both methods report problems through an unchecked `OpenOddException`.

`loadFile` follows `IMPORT` statements (resolved relative to the importing
file), merges all files into one model and computes the root modules. Use
`loadFiles` to load several entry points, or `newLoader` with a custom
resolver for embedded or in-memory content:

```java
var l = OpenOdd.newLoader(new MapResolver(Map.of(
        "odd.yaml", src,
        "taxonomy.yaml", taxonomy)));
var m = l.load("odd.yaml");
```

Root modules: modules under `ODD:`/`TOD:` are always roots; modules under
`MODULES:` are roots unless another module references them directly or
through a label.

### Evaluating records

```java
var ev = OpenOdd.newEvaluator(m, EvalOptions.DEFAULT);

// A single module:
var inside = ev.evaluate(rec, "example_module_1");

// All root modules:
var results = ev.evaluateRoots(rec); // Map<String, Boolean>

// The whole ODD: true when every root module matches.
var insideOdd = ev.insideOdd(rec);

// Three-valued classification: conditions over values the record does
// not carry evaluate to UNKNOWN instead of false (Kleene logic).
var verdict = ev.classify(rec, "example_module_1"); // OddVerdict
var verdicts = ev.classifyRoots(rec);               // Map<String, OddVerdict>
var overall = ev.classify(rec);                     // INSIDE, OUTSIDE or UNKNOWN
```

Records are maps of concept values. Keys may be full concept IDs
(`environmental_conditions.weather.wind_speed`) or unambiguous short
references (`wind_speed`):

```java
var rec = new Record(Map.of(
        "wind_speed", Value.number(72, "km/h", "")));
```

### COD/OD tables

```java
var idx = new ConceptIndex(m.taxonomy);

var cod = OpenOdd.parseCODCSV(new FileReader("cod.csv")); // CSV with "NAME;unit" headers
cod.resolve(idx);

var results = ev.evaluateCod(cod);  // one Map<String, Boolean> per row
var verdicts = ev.classifyCod(cod); // one Map<String, OddVerdict> per row
```

With a manifest (Section 8.3.4 of the standard), column names and units are
taken from the manifest and all referenced data files are merged:

```java
var manifest = OpenOdd.parseManifestCSV(new FileReader("manifest.csv"));
var cod = OpenOdd.loadCODWithManifest(manifest, new OsResolver(), dir);
cod.resolve(idx);
```

YAML-form tables work the same way through `parseCODYAML` and
`Cod.resolveRecords`.

### Writing YAML

```java
var data = m.writeYAML(); // or m.writeYAMLTo(writer)
```

The output parses back into an equivalent model. See *Limitations* below for
what is not reproduced.

## Semantics

The implementation follows the ASAM OpenODD model; where the standard leaves
room for interpretation, this package uses the following rules:

- **Modules** evaluate to `INCLUDE AND (NOT EXCLUDE)`. A missing INCLUDE
  section is `true`, a missing EXCLUDE section is `false`; an empty AND
  section is `true`, an empty OR section is `false`.
- **Inactive modules** (`ACTIVE: false`) are ignored: conditions referencing
  them — negated or not — are automatically satisfied. A label is the OR of
  its active modules; a label without active modules satisfies every
  condition referencing it.
- **Bounds** are strict: `<` is strictly less-than, `<=` is less-or-equal,
  and ranges `[a .. b]` are inclusive on both ends (the standard allows
  engines to pick a boundary convention; this one is chosen explicitly).
- **Uncertain values** — aleatoric ranges (`"[24 .. 26] m/s"`) and epistemic
  lists (`"[car, truck]"`) — are evaluated with **possibility** semantics by
  default: an expression is true if it holds for at least one possible
  point. `EvalOptions.of(UncertaintyMode.CERTAINTY)` requires it to hold for
  all possible points.
- **Missing values** — a condition over a concept for which the record
  carries no value evaluates to UNKNOWN, not false; verdicts combine under
  Kleene logic (`NOT unknown = unknown`), including the negated EXCLUDE
  conditions. A record is INSIDE only when every condition is provably
  true. The boolean API (`evaluate`, `evaluateRoots`, `insideOdd`,
  `evaluateCod`) collapses UNKNOWN to false; `classify`, `classifyRoots` and
  `classifyCod` return the three-valued `OddVerdict`. Uncertain *values*
  (see above) stay a separate concern: they are resolved by the uncertainty
  mode, not reported as UNKNOWN.
- **Units** follow the ASAM Unit Handling Guide: a value `v` in unit `u`
  converts to the base unit as `(v − offset) / factor`. The built-in
  registry covers the unit types of the standard (length, velocity,
  acceleration, time, angle, mass, temperature, precipitation_rate,
  data_rate, occurrence_rate, fraction, count, area, volume). Register more
  with `Units.registerUnitType`/`Units.registerUnit`; unit types unknown to
  the registry are *open* — their units compare symbolically.
- **Reference resolution** — conditions, terms and table columns resolve
  against the taxonomy: exact ID first, then a unique dotted suffix match;
  ambiguity is an error. The `$` parameter marker is optional. Type
  references (`wind_trajectory: radial_vector`) are expanded into synthetic
  field concepts so that `trajectory.angle` resolves.
- **Categoricals with constraint-defined literals** (`rain_level` with
  `rainfall_rate` ranges) induce a literal order from their numeric range
  constraints, enabling `rain_level: < heavy_rain`. A numeric record value
  for the underlying measure derives the literal
  (`rainfall_rate: 5 mm/h` ⇒ `moderate_rain`).
- **unknown** — the missing-value keywords (`unknown`, `none`, `null`)
  evaluate `x: unknown` to true exactly when the record has no value.
  Module IDs, literals and labels must never be named `unknown`.
- **ID spaces** — labels and module IDs must be disjoint from resolvable
  concept references; `validate` enforces this, as well as reference
  cycles.

## Limitations

- `writeYAML` writes a single merged file: `IMPORT` statements are resolved
  during loading and are not reproduced, and COD/OD records are not written
  at all.
- Concept, condition and section metadata survives a round trip only as
  `#/ key: value` line comments, per the standard's comment-based metadata
  notation.
- TITLE/DESCRIPTION use plain strings (or `{en: ...}` language maps); other
  LangString fields are not distinguished.

## Testing

```sh
./gradlew test
```

`src/test/resources/testdata/weather` contains a condensed version of the
standard's weather example, and
`src/test/resources/testdata/sample_odds` holds three small
self-contained example ODDs: a minimal daytime city ODD, one with EXCLUDE
sections and derived literals, and one composed from helper modules and a
parameter.

## New releases

A release is cut by pushing a `v*` tag that matches the `version` in
`build.gradle.kts` (`.github/workflows/release.yml`): the workflow
verifies the tag, builds and tests, publishes to Maven Central and
creates a GitHub release. It requires these repository secrets:

| Secret | Content |
|---|---|
| `CENTRAL_USERNAME` | Central Portal user token, username |
| `CENTRAL_PASSWORD` | Central Portal user token, password |
| `GPG_PRIVATE_KEY` | ASCII-armored signing key, `gpg --export-secret-keys --armor <key id>` |
| `GPG_KEY_ID` | The signing key id |
| `GPG_PASSPHRASE` | Passphrase of the signing key |

Manuell publish release candiates to Maven Central:

```sh
./gradlew publishToMavenLocal 
./gradlew publishAndReleaseToMavenCentral
```

## Acknowledgement

This library, including its source code, test suite, and documentation, was
generated with the help of artificial intelligence (GLM 5.3).

## License

Released under the [MIT License](LICENSE).
