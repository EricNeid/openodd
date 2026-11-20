// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.writer;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.expr.EqualExpr;
import io.github.ericneid.openodd.expr.Expression;
import io.github.ericneid.openodd.internal.Texts;
import io.github.ericneid.openodd.model.Concept;
import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.model.Module;
import io.github.ericneid.openodd.model.Section;
import io.github.ericneid.openodd.model.SectionOperator;
import io.github.ericneid.openodd.parser.YamlParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders a model in the YAML form of ASAM OpenODD (Section 10 of the
 * standard). The output contains the TAXONOMY element and the ODD, TOD
 * and MODULES elements; imports have already been merged into the model
 * and are therefore not reproduced. The output parses back into an
 * equivalent model with {@link YamlParser}.
 *
 * <p>The emitter mirrors the byte output of the reference implementation:
 * two-space indentation, double quotes for ambiguous scalars, flow style
 * for empty collections and {@code "#/ key: value"} line comments after
 * condition keys.</p>
 */
public final class YamlWriter {

	private YamlWriter() {
	}

	/**
	 * Renders the model into UTF-8 bytes.
	 *
	 * @param m the model to render
	 * @return the rendered YAML document
	 */
	public static byte[] writeYAML(Model m) {
		var e = new Emitter();
		e.emitDocument(yamlNode(m));
		return e.out.toString().getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Renders the model into an appendable output.
	 *
	 * @param m the model to render
	 * @param w the target of the rendered document
	 */
	public static void writeYAMLTo(Model m, Appendable w) {
		var e = new Emitter();
		e.emitDocument(yamlNode(m));
		try {
			w.append(e.out);
		} catch (IOException io) {
			throw new OpenOddException("writing YAML: " + io.getMessage(), io);
		}
	}

	// ------------------------------------------------------------------
	// Node tree

	/** A writer node: a scalar, a sequence or a mapping. */
	private static final class Node {
		/** The scalar text; {@code null} for collections. */
		final String scalar;
		/** Whether the scalar is written in double quotes. */
		final boolean quoted;
		/** Sequence items; {@code null} for scalars and mappings. */
		final List<Node> items;
		/** Mapping entries; {@code null} for scalars and sequences. */
		final List<Pair> pairs;
		/** A "#/ key: value" comment attached to a mapping key. */
		String lineComment;

		private Node(String scalar, boolean quoted, List<Node> items, List<Pair> pairs) {
			this.scalar = scalar;
			this.quoted = quoted;
			this.items = items;
			this.pairs = pairs;
		}

		/** A string scalar, quoted when it would be misread otherwise. */
		static Node str(String s) {
			return new Node(s, needsQuoting(s), null, null);
		}

		/** A boolean scalar. */
		static Node bool(boolean b) {
			return new Node(Boolean.toString(b), false, null, null);
		}

		/** A number scalar. */
		static Node number(double f) {
			return new Node(Texts.formatNumber(f), false, null, null);
		}

		/** An empty sequence. */
		static Node seq() {
			return new Node(null, false, new ArrayList<>(), null);
		}

		/** An empty mapping. */
		static Node map() {
			return new Node(null, false, null, new ArrayList<>());
		}

		void add(Node key, Node value) {
			pairs.add(new Pair(key, value));
		}
	}

	private record Pair(Node key, Node value) {
	}

	/** Builds the document root of the YAML form. */
	private static Node yamlNode(Model m) {
		var root = Node.map();
		if (!m.taxonomy.roots.isEmpty()) {
			var tax = Node.map();
			for (var c : m.taxonomy.roots.values()) {
				tax.add(conceptKeyNode(c), conceptValueNode(c));
			}
			root.add(Node.str("TAXONOMY"), tax);
		}
		var odd = new ArrayList<Module>();
		var tod = new ArrayList<Module>();
		var standard = new ArrayList<Module>();
		for (var id : new TreeMap<>(m.modules).keySet()) {
			var mod = m.modules.get(id);
			switch (mod.kind) {
				case "ODD" -> odd.add(mod);
				case "TOD" -> tod.add(mod);
				default -> standard.add(mod);
			}
		}
		appendModuleGroup(root, "ODD", odd);
		appendModuleGroup(root, "TOD", tod);
		appendModuleGroup(root, "MODULES", standard);
		return root;
	}

	private static void appendModuleGroup(Node root, String key, List<Module> mods) {
		if (mods.isEmpty()) {
			return;
		}
		var group = Node.map();
		for (var mod : mods) {
			group.add(Node.str(mod.id), moduleValueNode(mod));
		}
		root.add(Node.str(key), group);
	}

	/**
	 * Renders the taxonomy key of a concept, including the "$" marker of
	 * parameters.
	 */
	private static Node conceptKeyNode(Concept c) {
		var name = c.name;
		if (c.parameter) {
			name = "$" + name;
		}
		return Node.str(name);
	}

	/**
	 * Renders the taxonomy value of a concept: a type specification for
	 * attributes and type references, a sequence or literal constraint
	 * map for categoricals, or a nested mapping for records.
	 */
	private static Node conceptValueNode(Concept c) {
		switch (c.kind) {
			case ATTRIBUTE -> {
				var spec = c.primType;
				if (!c.unitType.isEmpty()) {
					spec += " " + c.unitType;
				}
				return Node.str(spec);
			}
			case TYPE_REFERENCE -> {
				return Node.str(c.ref);
			}
			case RECORD -> {
				var body = Node.map();
				for (var name : c.childOrder) {
					var child = c.children.get(name);
					if (child == null) {
						continue;
					}
					body.add(conceptKeyNode(child), conceptValueNode(child));
				}
				return body;
			}
			case CATEGORICAL -> {
				var hasConstraints = false;
				for (var lit : c.literals) {
					if (!lit.constraints.isEmpty()) {
						hasConstraints = true;
						break;
					}
				}
				if (!hasConstraints) {
					var seq = Node.seq();
					for (var lit : c.literals) {
						seq.items.add(Node.str(lit.name));
					}
					return seq;
				}
				var body = Node.map();
				for (var lit : c.literals) {
					var cons = Node.map();
					for (var constraint : lit.constraints) {
						cons.add(Node.str(constraint.ref()), exprNode(constraint.expr()));
					}
					body.add(Node.str(lit.name), cons);
				}
				return body;
			}
			default -> {
				return Node.str("");
			}
		}
	}

	/** Renders the body of a module. */
	private static Node moduleValueNode(Module mod) {
		var body = Node.map();
		if (!mod.title.isEmpty()) {
			body.add(Node.str("TITLE"), Node.str(mod.title));
		}
		if (!mod.description.isEmpty()) {
			body.add(Node.str("DESCRIPTION"), Node.str(mod.description));
		}
		if (!mod.labels.isEmpty()) {
			body.add(Node.str("LABELS"), strListNode(mod.labels));
		}
		if (!mod.tags.isEmpty()) {
			body.add(Node.str("TAGS"), strListNode(mod.tags));
		}
		if (mod.metadata != null && !mod.metadata.isEmpty()) {
			body.add(Node.str("METADATA"), strMapNode(mod.metadata));
		}
		if (!mod.active) {
			body.add(Node.str("ACTIVE"), Node.bool(false));
		}
		if (mod.include != null) {
			body.add(Node.str(sectionKey("INCLUDE", mod.include.operator)), sectionValueNode(mod.include));
		}
		if (mod.exclude != null) {
			body.add(Node.str(sectionKey("EXCLUDE", mod.exclude.operator)), sectionValueNode(mod.exclude));
		}
		return body;
	}

	private static String sectionKey(String prefix, SectionOperator op) {
		return op == SectionOperator.OR ? prefix + "_OR" : prefix + "_AND";
	}

	/**
	 * Renders a section body: its conditions, optional METADATA and
	 * nested AND/OR subsections.
	 */
	private static Node sectionValueNode(Section sec) {
		var body = Node.map();
		for (var cond : sec.conditions) {
			var key = Node.str(cond.ref);
			key.lineComment = setLineComment(cond.metadata);
			body.add(key, exprNode(cond.expr));
		}
		if (sec.metadata != null && !sec.metadata.isEmpty()) {
			body.add(Node.str("METADATA"), strMapNode(sec.metadata));
		}
		for (var sub : sec.subsections) {
			var key = sub.operator == SectionOperator.OR ? "OR" : "AND";
			body.add(Node.str(key), sectionValueNode(sub));
		}
		return body;
	}

	/**
	 * Renders a condition or literal constraint expression in the scalar
	 * notation of the standard.
	 */
	private static Node exprNode(Expression expr) {
		if (expr instanceof EqualExpr eq) {
			switch (eq.kind()) {
				case BOOL -> {
					return Node.bool(eq.bool());
				}
				case NUMBER -> {
					if (eq.unit().isEmpty()) {
						return Node.number(eq.num());
					}
				}
				case UNKNOWN -> {
					return Node.str("unknown");
				}
				default -> {
				}
			}
		}
		return Node.str(expr.toString());
	}

	private static Node strListNode(List<String> items) {
		var seq = Node.seq();
		for (var item : items) {
			seq.items.add(Node.str(item));
		}
		return seq;
	}

	private static Node strMapNode(Map<String, String> m) {
		var body = Node.map();
		for (var e : new TreeMap<>(m).entrySet()) {
			body.add(Node.str(e.getKey()), Node.str(e.getValue()));
		}
		return body;
	}

	/** Attaches "#/ key: value" structured metadata to a node. */
	private static String setLineComment(Map<String, String> meta) {
		if (meta == null || meta.isEmpty()) {
			return null;
		}
		var parts = new ArrayList<String>(meta.size());
		for (var e : new TreeMap<>(meta).entrySet()) {
			parts.add(e.getKey() + ": " + e.getValue());
		}
		return "#/ " + String.join(", ", parts);
	}

	// ------------------------------------------------------------------
	// Emitter

	/**
	 * A minimal YAML emitter for the node shapes of this writer,
	 * reproducing the reference output: two-space block indentation,
	 * flow style for empty collections and line comments after mapping
	 * values. go-yaml's default line width is unlimited, so scalars are
	 * never wrapped.
	 */
	private static final class Emitter {
		final StringBuilder out = new StringBuilder();
		private int column;
		private boolean whitespace = true;
		private boolean indention = true;

		void put(char c) {
			out.append(c);
			column++;
		}

		void putCodePoint(int cp) {
			out.appendCodePoint(cp);
			column++;
		}

		void putBreak() {
			out.append('\n');
			column = 0;
			indention = true;
		}

		void writeIndent(int indent) {
			if (!indention || column > indent || (column == indent && !whitespace)) {
				putBreak();
			}
			while (column < indent) {
				put(' ');
			}
			whitespace = true;
			indention = true;
		}

		void writeIndicator(String indicator, boolean needWhitespace, boolean isWhitespace, boolean isIndention) {
			if (needWhitespace && !whitespace) {
				put(' ');
			}
			for (var i = 0; i < indicator.length(); i++) {
				put(indicator.charAt(i));
			}
			whitespace = isWhitespace;
			indention = indention && isIndention;
		}

		void writePlain(String value) {
			if (!value.isEmpty() && !whitespace) {
				put(' ');
			}
			for (var i = 0; i < value.length(); ) {
				var cp = value.codePointAt(i);
				i += Character.charCount(cp);
				if (cp == '\n') {
					// Unreachable in practice: line breaks force quoting.
					putBreak();
				} else {
					putCodePoint(cp);
				}
			}
			if (!value.isEmpty()) {
				whitespace = false;
				indention = false;
			}
		}

		void writeDoubleQuoted(String value) {
			writeIndicator("\"", true, false, false);
			for (var i = 0; i < value.length(); ) {
				var cp = value.codePointAt(i);
				i += Character.charCount(cp);
				var esc = escape(cp);
				if (esc != null) {
					for (var k = 0; k < esc.length(); k++) {
						put(esc.charAt(k));
					}
				} else {
					putCodePoint(cp);
				}
			}
			writeIndicator("\"", false, false, false);
			whitespace = false;
			indention = false;
		}

		/** Returns the escape spelling of a character, or null to emit it raw. */
		static String escape(int cp) {
			var mustEscape = !isPrintable(cp) || cp == 0xFEFF || isBreakCp(cp) || cp == '"' || cp == '\\';
			if (!mustEscape) {
				return null;
			}
			return switch (cp) {
				case 0x00 -> "\\0";
				case 0x07 -> "\\a";
				case 0x08 -> "\\b";
				case 0x09 -> "\\t";
				case 0x0A -> "\\n";
				case 0x0B -> "\\v";
				case 0x0C -> "\\f";
				case 0x0D -> "\\r";
				case 0x1B -> "\\e";
				case '"' -> "\\\"";
				case '\\' -> "\\\\";
				case 0x85 -> "\\N";
				case 0xA0 -> "\\_";
				case 0x2028 -> "\\L";
				case 0x2029 -> "\\P";
				default -> cp <= 0xFF ? "\\x%02X".formatted(cp)
						: cp <= 0xFFFF ? "\\u%04X".formatted(cp)
						: "\\U%08X".formatted(cp);
			};
		}

		/** Mirrors go-yaml's is_printable for code points. */
		static boolean isPrintable(int cp) {
			if (cp == 0x0A) {
				return true;
			}
			if (cp >= 0x20 && cp <= 0x7E) {
				return true;
			}
			if (cp >= 0xA0 && cp <= 0xD7FF) {
				return true;
			}
			return cp >= 0xE000 && cp <= 0xFFFD && cp != 0xFEFF;
		}

		static boolean isBreakCp(int cp) {
			return cp == '\n' || cp == '\r' || cp == 0x85 || cp == 0x2028 || cp == 0x2029;
		}

		/** Writes a line comment after a mapping value, then breaks the line. */
		void writeLineComment(String comment) {
			if (!whitespace) {
				put(' ');
			}
			for (var i = 0; i < comment.length(); i++) {
				put(comment.charAt(i));
			}
			putBreak();
			whitespace = true;
		}

		void emitDocument(Node root) {
			if (root.pairs.isEmpty()) {
				// An empty block mapping is not representable; use flow style.
				writeIndicator("{", true, true, false);
				writeIndicator("}", false, false, false);
				putBreak();
				return;
			}
			emitMapping(root, 0);
			writeIndent(0);
		}

		void emitMapping(Node map, int indent) {
			for (var p : map.pairs) {
				writeIndent(indent);
				emitScalarNode(p.key());
				writeIndicator(":", false, false, false);
				emitValue(p.value(), indent, p.key().lineComment);
			}
		}

		void emitScalarNode(Node n) {
			if (n.quoted) {
				writeDoubleQuoted(n.scalar);
			} else {
				writePlain(n.scalar);
			}
		}

		void emitValue(Node value, int indent, String lineComment) {
			if (value.scalar != null) {
				emitScalarNode(value);
				if (lineComment != null) {
					writeLineComment(lineComment);
				}
				return;
			}
			if (value.pairs != null) {
				if (!value.pairs.isEmpty()) {
					emitMapping(value, indent + 2);
					return;
				}
				writeIndicator("{", true, true, false);
				writeIndicator("}", false, false, false);
				return;
			}
			if (!value.items.isEmpty()) {
				emitSequence(value, indent + 2);
				return;
			}
			writeIndicator("[", true, true, false);
			writeIndicator("]", false, false, false);
		}

		void emitSequence(Node seq, int indent) {
			for (var item : seq.items) {
				writeIndent(indent);
				writeIndicator("-", true, false, true);
				if (item.scalar != null) {
					emitScalarNode(item);
				} else if (item.pairs != null) {
					if (!item.pairs.isEmpty()) {
						// The first key continues inline after "- ".
						emitMapping(item, indent + 2);
					} else {
						writeIndicator("{", true, true, false);
						writeIndicator("}", false, false, false);
					}
				} else if (!item.items.isEmpty()) {
					emitSequence(item, indent + 2);
				} else {
					writeIndicator("[", true, true, false);
					writeIndicator("]", false, false, false);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Scalar quoting

	/**
	 * Reports whether a string scalar would be misread as a number,
	 * boolean, null or flow indicator when written plain.
	 */
	static boolean needsQuoting(String s) {
		if (s.isEmpty()) {
			return true;
		}
		switch (s.toLowerCase(Locale.ROOT)) {
			case "true", "false", "yes", "no", "on", "off", "null", "~", "nan", "inf", "+inf", "-inf", "infinity":
				return true;
			default:
				break;
		}
		if (goParseFloatOk(s)) {
			return true;
		}
		switch (s.charAt(0)) {
			case '-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%', '@', '`',
				 ' ', '\t':
				return true;
			default:
				break;
		}
		var last = s.charAt(s.length() - 1);
		if (last == ' ' || last == ':') {
			return true;
		}
		if (s.contains(": ") || s.contains(" #")) {
			return true;
		}
		return s.contains("\n") || s.contains("\t");
	}

	/**
	 * Reports whether the string is syntactically accepted by Go's
	 * strconv.ParseFloat: decimal and hexadecimal floating point with
	 * digit-separating underscores and the inf/infinity/nan spellings.
	 */
	static boolean goParseFloatOk(String s) {
		if (s.isEmpty()) {
			return false;
		}
		var i = 0;
		var n = s.length();
		if (s.charAt(i) == '+' || s.charAt(i) == '-') {
			i++;
		}
		var rest = s.substring(i).toLowerCase(Locale.ROOT);
		if (rest.equals("inf") || rest.equals("infinity") || rest.equals("nan")) {
			return true;
		}
		var hex = false;
		var expChar = 'e';
		if (i + 2 < n && s.charAt(i) == '0' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
			hex = true;
			expChar = 'p';
			i += 2;
		}
		var sawdot = false;
		var sawdigits = false;
		for (; i < n; i++) {
			var c = s.charAt(i);
			if (c == '_') {
				continue;
			}
			if (c == '.') {
				if (sawdot) {
					break;
				}
				sawdot = true;
				continue;
			}
			if (c >= '0' && c <= '9') {
				sawdigits = true;
				continue;
			}
			if (hex && Character.toLowerCase(c) >= 'a' && Character.toLowerCase(c) <= 'f') {
				sawdigits = true;
				continue;
			}
			break;
		}
		if (!sawdigits) {
			return false;
		}
		if (i < n && Character.toLowerCase(s.charAt(i)) == expChar) {
			i++;
			if (i >= n) {
				return false;
			}
			if (s.charAt(i) == '+' || s.charAt(i) == '-') {
				i++;
			}
			if (i >= n || s.charAt(i) < '0' || s.charAt(i) > '9') {
				return false;
			}
			while (i < n && (isDigit(s.charAt(i)) || s.charAt(i) == '_')) {
				i++;
			}
		} else if (hex) {
			// Hexadecimal floats require a binary exponent.
			return false;
		}
		if (i != n) {
			return false;
		}
		if (s.indexOf('_') >= 0 && !underscoreOK(s)) {
			return false;
		}
		return true;
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	/**
	 * Validates digit-separating underscores the way strconv does: an
	 * underscore must sit between digits (a base prefix counts as a
	 * digit).
	 */
	private static boolean underscoreOK(String s) {
		var saw = '^';
		var i = 0;
		var n = s.length();
		if (n >= 1 && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
			s = s.substring(1);
			n--;
		}
		var hex = false;
		if (n >= 2 && s.charAt(0) == '0') {
			var p = Character.toLowerCase(s.charAt(1));
			if (p == 'b' || p == 'o' || p == 'x') {
				i = 2;
				saw = '0';
				hex = p == 'x';
			}
		}
		for (; i < n; i++) {
			var c = s.charAt(i);
			if (isDigit(c) || (hex && Character.toLowerCase(c) >= 'a' && Character.toLowerCase(c) <= 'f')) {
				saw = '0';
				continue;
			}
			if (c == '_') {
				if (saw != '0') {
					return false;
				}
				saw = '_';
				continue;
			}
			if (saw == '_') {
				return false;
			}
			saw = '!';
		}
		return saw != '_';
	}
}
