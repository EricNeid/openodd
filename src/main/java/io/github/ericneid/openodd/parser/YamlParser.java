// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.parser;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.expr.EqualExpr;
import io.github.ericneid.openodd.expr.Expression;
import io.github.ericneid.openodd.expr.Expressions;
import io.github.ericneid.openodd.expr.ListExpr;
import io.github.ericneid.openodd.internal.Texts;
import io.github.ericneid.openodd.model.Concept;
import io.github.ericneid.openodd.model.ConceptKind;
import io.github.ericneid.openodd.model.Condition;
import io.github.ericneid.openodd.model.Constraint;
import io.github.ericneid.openodd.model.Document;
import io.github.ericneid.openodd.model.Literal;
import io.github.ericneid.openodd.model.Module;
import io.github.ericneid.openodd.model.Section;
import io.github.ericneid.openodd.model.SectionOperator;
import io.github.ericneid.openodd.model.Taxonomy;
import io.github.ericneid.openodd.value.Record;
import io.github.ericneid.openodd.value.Value;
import io.github.ericneid.openodd.value.Values;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.AnchorNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a single ASAM OpenODD YAML document. IMPORT statements are
 * collected but not resolved.
 */
public final class YamlParser {

	private YamlParser() {
	}

	/** Parses a single ASAM OpenODD YAML document. */
	public static Document parse(String data) {
		var root = compose(data);
		if (root == null) {
			return new Document();
		}
		if (!(deref(root) instanceof MappingNode docNode)) {
			throw new OpenOddException(
					"expected a YAML mapping at the document root, got " + nodeKindName(root));
		}
		var doc = new Document();
		for (var entry : mappingKeys(docNode)) {
			switch (entry.key()) {
				case "IMPORT" -> doc.imports.addAll(parseStringList(entry.value()));
				case "TAXONOMY" -> parseTaxonomy(doc.taxonomy, entry.value());
				case "ODD", "TOD" -> parseModuleMap(doc, entry.value(), entry.key(), true);
				case "MODULES" -> parseModuleMap(doc, entry.value(), "Standard", false);
				case "COD", "OD" -> {
					var records = parseCodRecordsNode(entry.value());
					if (entry.key().equals("COD")) {
						doc.cod.addAll(records);
					} else {
						doc.od.addAll(records);
					}
				}
				default -> throw new OpenOddException(
						"unknown top-level element \"%s\" (expected IMPORT, TAXONOMY, ODD, TOD, MODULES, COD or OD)"
								.formatted(entry.key()));
			}
		}
		return doc;
	}

	private static Node compose(String data) {
		try {
			var options = new LoaderOptions();
			options.setProcessComments(true);
			return new Yaml(options).compose(new StringReader(data));
		} catch (Exception e) {
			throw new OpenOddException("invalid YAML: " + e.getMessage(), e);
		}
	}

	/** Resolves alias nodes to their anchors. */
	static Node deref(Node n) {
		while (n instanceof AnchorNode a) {
			n = a.getRealNode();
		}
		return n;
	}

	static int line(Node n) {
		return n.getStartMark() == null ? 0 : n.getStartMark().getLine() + 1;
	}

	static String nodeKindName(Node n) {
		n = deref(n);
		if (n instanceof SequenceNode) {
			return "a sequence";
		}
		if (n instanceof ScalarNode) {
			return "a scalar";
		}
		if (n instanceof MappingNode) {
			return "a mapping";
		}
		return "another node kind";
	}

	/** One key/value pair of a YAML mapping. */
	record Entry(String key, Node keyNode, Node value) {
	}

	/**
	 * Returns the entries of a mapping node and rejects duplicate keys,
	 * which the standard forbids for all of its ID spaces.
	 */
	static List<Entry> mappingKeys(Node node) {
		if (!(deref(node) instanceof MappingNode n)) {
			throw new OpenOddException("expected a YAML mapping, got " + nodeKindName(node));
		}
		var seen = new LinkedHashMap<String, Integer>();
		var entries = new ArrayList<Entry>(n.getValue().size());
		for (var tuple : n.getValue()) {
			var keyNode = deref(tuple.getKeyNode());
			var valNode = tuple.getValueNode();
			if (!(keyNode instanceof ScalarNode)) {
				throw new OpenOddException(
						"line %d: mapping keys must be scalars".formatted(line(tuple.getKeyNode())));
			}
			var key = ((ScalarNode) keyNode).getValue();
			var prev = seen.put(key, line(keyNode));
			if (prev != null) {
				throw new OpenOddException(
						"line %d: duplicate key \"%s\" (first defined on line %d)".formatted(line(keyNode), key, prev));
			}
			entries.add(new Entry(key, tuple.getKeyNode(), valNode));
		}
		return entries;
	}

	static List<String> parseStringList(Node node) {
		var n = deref(node);
		if (n instanceof ScalarNode sc) {
			var s = scalarString(n);
			if (s.trim().isEmpty()) {
				return List.of();
			}
			return List.of(s);
		}
		if (n instanceof SequenceNode seq) {
			var out = new ArrayList<String>();
			for (var raw : seq.getValue()) {
				var item = deref(raw);
				out.add(scalarString(item));
			}
			return out;
		}
		throw new OpenOddException("expected a string or a list of strings, got " + nodeKindName(node));
	}

	/**
	 * Classifies a mapping-valued taxonomy entry: a record whose entries
	 * are child concepts, or a categorical whose entries are literals
	 * with defining constraint expressions. The YAML notation itself is
	 * ambiguous here; the classification follows these rules:
	 *
	 * <ul>
	 *   <li>a direct scalar or sequence entry denotes a record member;</li>
	 *   <li>a nested map that looks typed denotes a record member;</li>
	 *   <li>a nested map with only expression scalars denotes a literal
	 *       constraint map, making the parent a categorical; null entries
	 *       are constraint-less literals of such a categorical.</li>
	 * </ul>
	 */
	static boolean mappingIsRecord(List<Entry> entries) {
		var constraintMap = false;
		for (var e : entries) {
			var v = deref(e.value());
			if (v instanceof ScalarNode sc) {
				if (Tag.NULL.equals(sc.getTag())) {
					continue; // ambiguous: literal without constraints
				}
				return true;
			}
			if (v instanceof SequenceNode) {
				return true;
			}
			if (v instanceof MappingNode) {
				if (!nestedLooksTyped(v)) {
					constraintMap = true;
				}
			}
		}
		return !constraintMap;
	}

	/** Reports whether a nested mapping looks like a record body. */
	static boolean nestedLooksTyped(Node mapping) {
		for (var tuple : ((MappingNode) mapping).getValue()) {
			var v = deref(tuple.getValueNode());
			if (v instanceof MappingNode) {
				return true;
			}
			if (v instanceof ScalarNode) {
				if (isTypeSpecScalar(v)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Reports whether a scalar value looks like a type specification such
	 * as "float velocity", "boolean" or "shapefile". Single-token
	 * identifiers are deliberately not recognized: inside constraint maps
	 * they are equal-literal expressions.
	 */
	static boolean isTypeSpecScalar(Node v) {
		if (!(v instanceof ScalarNode sc)) {
			return false;
		}
		var fields = sc.getValue().trim().split("\\s+");
		if (fields.length == 0 || fields.length > 2 || fields[0].isEmpty()) {
			return false;
		}
		return Concept.isPrimitiveType(fields[0]);
	}

	/** Reads the TAXONOMY element into the taxonomy. */
	static void parseTaxonomy(Taxonomy tax, Node node) {
		for (var e : mappingKeys(node)) {
			parseConceptNode(tax, null, e.keyNode(), e.key(), deref(e.value()));
		}
	}

	/**
	 * Parses one taxonomy key/value pair. Keys may contain dots (measures,
	 * aggregations); such keys define concepts whose ID is the dotted
	 * path without introducing intermediate records.
	 */
	static void parseConceptNode(Taxonomy tax, Concept parent, Node rawKeyNode, String key, Node val) {
		var name = key;
		var parameter = false;
		if (name.startsWith("$")) {
			parameter = true;
			name = name.substring(1);
		}
		if (name.isEmpty()) {
			throw new OpenOddException("line %d: empty concept name".formatted(line(rawKeyNode)));
		}
		if (name.contains(".")) {
			for (var seg : name.split("\\.", -1)) {
				if (seg.isEmpty()) {
					throw new OpenOddException(
							"line %d: invalid concept name \"%s\"".formatted(line(rawKeyNode), key));
				}
			}
		}

		var id = parent != null ? parent.id + "." + key : key;
		if (tax.concepts.containsKey(id)) {
			throw new OpenOddException("line %d: duplicate concept \"%s\"".formatted(line(rawKeyNode), id));
		}

		var c = new Concept(id, name, parameter, parent);

		if (val instanceof ScalarNode sc) {
			var spec = scalarString(val);
			var fields = spec.trim().split("\\s+");
			if (fields.length == 0 || fields[0].isEmpty()) {
				throw new OpenOddException(
						"line %d: concept \"%s\" has an empty type specification".formatted(line(val), key));
			}
			if (Concept.isPrimitiveType(fields[0])) {
				if (fields.length > 2) {
					throw new OpenOddException(
							"line %d: concept \"%s\": invalid type specification \"%s\" (expected \"<type> [<unit type>]\")"
									.formatted(line(val), key, spec));
				}
				c.kind = ConceptKind.ATTRIBUTE;
				c.primType = fields[0];
				if (fields.length == 2) {
					if (Concept.PRIM_BOOL.equals(c.primType) || Concept.PRIM_SHAPEFILE.equals(c.primType)) {
						throw new OpenOddException(
								"line %d: concept \"%s\": type \"%s\" takes no unit type".formatted(line(val), key,
										c.primType));
					}
					c.unitType = fields[1];
				}
			} else if (fields.length == 1) {
				c.kind = ConceptKind.TYPE_REFERENCE;
				c.ref = fields[0];
			} else {
				throw new OpenOddException(
						"line %d: concept \"%s\": invalid type specification \"%s\"".formatted(line(val), key, spec));
			}
		} else if (val instanceof SequenceNode seq) {
			c.kind = ConceptKind.CATEGORICAL;
			for (var raw : seq.getValue()) {
				var item = deref(raw);
				var lit = literalFromScalar(item);
				if (c.literal(lit.name) != null) {
					throw new OpenOddException(
							"line %d: duplicate categorical literal \"%s\" in concept \"%s\"".formatted(line(raw),
									lit.name, id));
				}
				c.literals.add(lit);
			}
		} else if (val instanceof MappingNode) {
			var entries = mappingKeys(val);
			if (mappingIsRecord(entries)) {
				// A record: each entry is a child concept.
				c.kind = ConceptKind.RECORD;
				for (var e : entries) {
					parseConceptNode(tax, c, e.keyNode(), e.key(), deref(e.value()));
				}
			} else {
				// A categorical: each entry is a literal with defining
				// constraint expressions.
				c.kind = ConceptKind.CATEGORICAL;
				for (var e : entries) {
					var litName = e.key();
					if (litName.equalsIgnoreCase("unknown")) {
						throw new OpenOddException(
								"line %d: literal must not be \"%s\"".formatted(line(e.keyNode()), litName));
					}
					var lit = new Literal(litName);
					lit.constraints.addAll(parseLiteralConstraints(deref(e.value())));
					if (c.literal(litName) != null) {
						throw new OpenOddException(
								"line %d: duplicate categorical literal \"%s\" in concept \"%s\"".formatted(
										line(e.keyNode()), litName, id));
					}
					c.literals.add(lit);
				}
			}
		} else {
			throw new OpenOddException("line %d: concept \"%s\": unsupported value of type %s".formatted(line(val),
					key, nodeKindName(val)));
		}

		c.metadata = structuredComment(effectiveLineComment(rawKeyNode));
		tax.concepts.put(id, c);
		if (parent == null) {
			tax.roots.put(id, c);
		} else {
			parent.children.put(key, c);
			parent.childOrder.add(key);
		}
	}

	static String scalarString(Node n) {
		if (!(deref(n) instanceof ScalarNode sc)) {
			throw new OpenOddException("expected a scalar, got " + nodeKindName(n));
		}
		return sc.getValue();
	}

	static Literal literalFromScalar(Node n) {
		if (!(n instanceof ScalarNode sc)) {
			throw new OpenOddException("categorical literals must be scalars, got " + nodeKindName(n));
		}
		var name = sc.getValue();
		if (Tag.BOOL.equals(sc.getTag()) && isGoBoolSpelling(name)) {
			// Normalize YAML boolean spellings such as "True". SnakeYAML
			// additionally resolves YAML 1.1 spellings such as "yes"; go-yaml
			// keeps those as strings, so they pass through unchanged.
			name = name.toLowerCase();
		}
		if (name.trim().isEmpty()) {
			throw new OpenOddException("empty categorical literal");
		}
		return new Literal(name);
	}

	/**
	 * Reports whether s is one of the boolean spellings go-yaml resolves
	 * (true/false in any capitalization).
	 */
	static boolean isGoBoolSpelling(String s) {
		return switch (s) {
			case "true", "True", "TRUE", "false", "False", "FALSE" -> true;
			default -> false;
		};
	}

	/**
	 * Parses the constraint map of a categorical literal, for example
	 * "light_rain: {rainfall_rate: \"[0.1 .. 2.5] mm/h\"}". A null or
	 * empty map declares the literal without constraints.
	 */
	static List<Constraint> parseLiteralConstraints(Node n) {
		if (n instanceof ScalarNode sc && Tag.NULL.equals(sc.getTag())) {
			return List.of();
		}
		var constraints = new ArrayList<Constraint>();
		for (var e : mappingKeys(n)) {
			constraints.add(new Constraint(e.key(), parseExpressionNode(e.value())));
		}
		return constraints;
	}

	/** Parses the ODD, TOD or MODULES elements. */
	static void parseModuleMap(Document doc, Node node, String kind, boolean root) {
		for (var e : mappingKeys(node)) {
			if (e.key().equalsIgnoreCase("unknown")) {
				throw new OpenOddException(
						"line %d: module ID must not be \"%s\"".formatted(line(e.keyNode()), e.key()));
			}
			if (doc.modules.containsKey(e.key())) {
				throw new OpenOddException(
						"line %d: duplicate module \"%s\"".formatted(line(e.keyNode()), e.key()));
			}
			var mod = parseModule(e.key(), kind, root, deref(e.value()));
			doc.modules.put(e.key(), mod);
			if (root) {
				doc.rootIds.add(e.key());
			}
			for (var label : mod.labels) {
				doc.labels.computeIfAbsent(label, k -> new ArrayList<>()).add(e.key());
			}
		}
	}

	static Module parseModule(String id, String kind, boolean root, Node n) {
		var mod = new Module(id, kind);
		mod.root = root;
		if (!(n instanceof MappingNode)) {
			throw new OpenOddException("expected a module mapping, got " + nodeKindName(n));
		}
		for (var e : mappingKeys(n)) {
			switch (e.key()) {
				case "TITLE" -> mod.title = parseLangString(e.value());
				case "DESCRIPTION" -> mod.description = parseLangString(e.value());
				case "LABEL", "LABELS" -> mod.labels.addAll(parseStringList(e.value()));
				case "TAGS" -> mod.tags.addAll(parseStringList(e.value()));
				case "METADATA" -> mod.metadata = mergeMetadata(mod.metadata, parseMetadata(deref(e.value())));
				case "ACTIVE" -> mod.active = parseBoolNode(e.value());
				case "INCLUDE_AND", "INCLUDE_OR" -> {
					if (mod.include != null) {
						throw new OpenOddException("duplicate INCLUDE section (%s)".formatted(e.key()));
					}
					mod.include = parseSection(SectionOperator.AND, "INCLUDE_OR".equals(e.key()), deref(e.value()));
				}
				case "EXCLUDE_AND", "EXCLUDE_OR" -> {
					if (mod.exclude != null) {
						throw new OpenOddException("duplicate EXCLUDE section (%s)".formatted(e.key()));
					}
					mod.exclude = parseSection(SectionOperator.AND, "EXCLUDE_OR".equals(e.key()), deref(e.value()));
				}
				default -> throw new OpenOddException(
						"unknown module element \"%s\" (expected TITLE, DESCRIPTION, LABELS, TAGS, METADATA, ACTIVE, INCLUDE_AND, INCLUDE_OR, EXCLUDE_AND or EXCLUDE_OR)"
								.formatted(e.key()));
			}
		}
		return mod;
	}

	static boolean parseBoolNode(Node node) {
		var n = deref(node);
		if (n instanceof ScalarNode sc && Tag.BOOL.equals(sc.getTag()) && isGoBoolSpelling(sc.getValue())) {
			return sc.getValue().toLowerCase().equals("true");
		}
		throw new OpenOddException("expected a boolean, got \"%s\"".formatted(scalarValue(node)));
	}

	private static String scalarValue(Node n) {
		return n instanceof ScalarNode sc ? sc.getValue() : nodeKindName(n);
	}

	static String parseLangString(Node node) {
		var n = deref(node);
		if (n instanceof ScalarNode) {
			return scalarString(n);
		}
		if (n instanceof MappingNode) {
			// A LangString map such as {en: ..., de: ...}; prefer English.
			var values = new LinkedHashMap<String, String>();
			for (var e : mappingKeys(n)) {
				values.put(e.key().toLowerCase(), scalarString(deref(e.value())));
			}
			var en = values.get("en");
			if (en != null) {
				return en;
			}
			for (var k : new java.util.TreeSet<>(values.keySet())) {
				return values.get(k);
			}
			return "";
		}
		throw new OpenOddException("expected a string or language map, got " + nodeKindName(node));
	}

	static Map<String, String> parseMetadata(Node n) {
		var out = new LinkedHashMap<String, String>();
		for (var e : mappingKeys(n)) {
			out.put(e.key(), parseLangString(e.value()));
		}
		return out;
	}

	static Map<String, String> mergeMetadata(Map<String, String> dst, Map<String, String> src) {
		if (src == null || src.isEmpty()) {
			return dst;
		}
		if (dst == null) {
			dst = new LinkedHashMap<>();
		}
		dst.putAll(src);
		return dst;
	}

	/** Parses an INCLUDE or EXCLUDE section body or a nested AND/OR subsection body. */
	static Section parseSection(SectionOperator op, boolean isOr, Node n) {
		if (isOr) {
			op = SectionOperator.OR;
		}
		var sec = new Section(op);
		for (var e : mappingKeys(n)) {
			switch (e.key()) {
				case "AND" -> sec.subsections.add(parseSection(SectionOperator.AND, false, deref(e.value())));
				case "OR" -> sec.subsections.add(parseSection(SectionOperator.OR, true, deref(e.value())));
				case "METADATA" -> sec.metadata = mergeMetadata(sec.metadata, parseMetadata(deref(e.value())));
				default -> {
					var expr = parseExpressionNode(e.value());
					sec.conditions.add(new Condition(e.key(), expr, structuredComment(effectiveLineComment(e.value()))));
				}
			}
		}
		return sec;
	}

	/**
	 * Parses the value of a condition. Unquoted YAML booleans and numbers
	 * arrive with their respective tags; their raw text is parsed like
	 * the equivalent quoted string, so that "true" and true lead to the
	 * same expression.
	 */
	static Expression parseExpressionNode(Node node) {
		var n = deref(node);
		if (n instanceof ScalarNode sc) {
			if (Tag.NULL.equals(sc.getTag())) {
				return EqualExpr.unknown();
			}
			return Expressions.parse(sc.getValue());
		}
		if (n instanceof SequenceNode seq) {
			var items = seq.getValue();
			// An unquoted range such as [1.25*a .. 2.25*a] arrives as a
			// single-element sequence containing the range text.
			if (items.size() == 1) {
				var item = deref(items.get(0));
				if (item instanceof ScalarNode sc && sc.getValue().contains("..")) {
					return Expressions.parse(sc.getValue());
				}
			}
			var list = new ArrayList<String>();
			for (var raw : items) {
				var item = deref(raw);
				if (!(item instanceof ScalarNode sc)) {
					throw new OpenOddException("list items must be scalars, got " + nodeKindName(item));
				}
				var name = sc.getValue();
				if (Tag.BOOL.equals(sc.getTag()) && isGoBoolSpelling(name)) {
					name = name.toLowerCase();
				}
				list.add(name);
			}
			return new ListExpr(list);
		}
		throw new OpenOddException("expected a scalar or a list expression, got " + nodeKindName(n));
	}

	/** Parses the COD/OD element of a YAML file (Section 10.3). */
	static List<Record> parseCodRecordsNode(Node node) {
		var n = deref(node);
		if (!(n instanceof SequenceNode seq)) {
			throw new OpenOddException("expected a list of records, got " + nodeKindName(n));
		}
		var records = new ArrayList<Record>();
		for (var raw : seq.getValue()) {
			var item = deref(raw);
			var rec = new Record();
			for (var e : mappingKeys(item)) {
				var header = e.key(); // "NAME;marker" or an extent column
				var nm = Cod.splitColumnHeader(header);
				var name = nm[0];
				switch (name.toUpperCase()) {
					case "TEMPORAL_EXTENT" -> rec.temporal(scalarString(deref(e.value())));
					case "SPATIAL_EXTENT" -> rec.spatial(scalarString(deref(e.value())));
					default -> rec.values().put(name, parseRecordValueNode(e.value(), nm[1]));
				}
			}
			records.add(rec);
		}
		return records;
	}

	/** Parses one cell of a YAML COD/OD record. */
	static Value parseRecordValueNode(Node node, String marker) {
		var n = deref(node);
		var uu = Cod.interpretMarker(marker);
		var unit = uu[0];
		var unitType = uu[1];
		if (n instanceof ScalarNode sc) {
			var tag = sc.getTag();
			if (Tag.BOOL.equals(tag)) {
				if (isGoBoolSpelling(sc.getValue())) {
					return Value.bool(sc.getValue().toLowerCase().equals("true"));
				}
				// SnakeYAML resolves YAML 1.1 spellings such as "yes" as
				// booleans; go-yaml keeps them as strings, so the cell is
				// parsed as text.
			} else if (Tag.INT.equals(tag) || Tag.FLOAT.equals(tag)) {
				return Value.number(parseYamlNumber(sc.getValue()), unit, unitType);
			} else if (Tag.NULL.equals(tag)) {
				return Value.missing();
			}
			return Values.parseCellValue(sc.getValue(), unit, unitType);
		}
		if (n instanceof SequenceNode seq) {
			var items = new ArrayList<String>();
			for (var raw : seq.getValue()) {
				items.add(scalarString(deref(raw)));
			}
			// A single-item sequence may be a range "[a .. b]" written
			// unquoted; multi-item sequences are categorical lists. The
			// range text arrives without its brackets, so they are
			// restored before running the regular cell parser.
			if (items.size() == 1 && items.get(0).contains("..")) {
				return Values.parseCellValue("[" + items.get(0) + "]", unit, unitType);
			}
			var list = new ArrayList<Value>();
			for (var s : items) {
				list.add(Values.parseScalarValue(s, unit, unitType));
			}
			return Value.list(list);
		}
		if (n instanceof MappingNode) {
			// A JSON-like record structure.
			return recordFromMapping(n);
		}
		throw new OpenOddException("unexpected record value " + nodeKindName(n));
	}

	static Value recordFromMapping(Node n) {
		var rec = new LinkedHashMap<String, Value>();
		for (var e : mappingKeys(n)) {
			rec.put(e.key(), parseRecordValueNode(e.value(), ""));
		}
		return Value.record(rec);
	}

	/**
	 * Parses a YAML number as resolved by SnakeYAML (decimal and hex
	 * integers, floats with leading dots and exponents).
	 */
	static double parseYamlNumber(String s) {
		var t = s.trim().replace("_", "");
		try {
			if (t.startsWith("0x") || t.startsWith("0X") || t.startsWith("-0x") || t.startsWith("+0x")
					|| t.startsWith("0o") || t.startsWith("0O") || t.startsWith("-0o") || t.startsWith("+0o")) {
				var negative = t.startsWith("-");
				var body = negative || t.startsWith("+") ? t.substring(1) : t;
				var radix = body.startsWith("0x") || body.startsWith("0X") ? 16 : 8;
				var v = Long.parseLong(body.substring(2), radix);
				return negative ? -v : v;
			}
			if (t.startsWith("+")) {
				t = t.substring(1);
			}
			if (t.startsWith(".")) {
				t = "0" + t;
			} else if (t.startsWith("-.")) {
				t = "-0" + t.substring(1);
			}
			return Double.parseDouble(t);
		} catch (NumberFormatException e) {
			throw new OpenOddException("invalid YAML number \"%s\"".formatted(s));
		}
	}

	/**
	 * Returns the inline comment text of a node, including the leading
	 * "#", mirroring the comment model of the reference implementation.
	 */
	static String effectiveLineComment(Node node) {
		var n = deref(node);
		var comments = n.getInLineComments();
		if (comments == null || comments.isEmpty()) {
			return "";
		}
		var value = comments.get(0).getValue();
		if (value == null) {
			return "";
		}
		return value.startsWith("#") ? value : "#" + value;
	}

	/** Extracts "#/ key: value" metadata from a line comment. */
	static Map<String, String> structuredComment(String comment) {
		if (comment == null || comment.isEmpty()) {
			return null;
		}
		comment = comment.trim();
		if (!comment.startsWith("#/")) {
			return null;
		}
		var body = comment.substring(2).trim();
		Map<String, String> meta = null;
		for (var part : Texts.splitListItems(body)) {
			var i = part.indexOf(':');
			if (i < 0) {
				continue;
			}
			var k = part.substring(0, i).trim();
			var v = part.substring(i + 1).trim();
			if (k.isEmpty()) {
				continue;
			}
			if (meta == null) {
				meta = new LinkedHashMap<>();
			}
			meta.put(k, v);
		}
		return meta;
	}
}
