// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.writer;

import io.github.ericneid.TestUtils;
import io.github.ericneid.openodd.expr.BoundExpr;
import io.github.ericneid.openodd.expr.CmpOp;
import io.github.ericneid.openodd.expr.EqualExpr;
import io.github.ericneid.openodd.expr.Expression;
import io.github.ericneid.openodd.expr.ListExpr;
import io.github.ericneid.openodd.expr.RangeExpr;
import io.github.ericneid.openodd.expr.ScalarKind;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.parser.MapResolver;
import io.github.ericneid.openodd.parser.YamlParser;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the YAML writer. */
class YamlWriteTest {

	@Test
	void writeYAMLRoundTrip() {
		// arrange
		var m = Loader.loadFile(TestUtils.resourcePath("testdata/weather/odd.yaml").toString());
		// action
		var out = m.writeYAML();
		var text = new String(out, StandardCharsets.UTF_8);
		var m2 = new Loader(new MapResolver(Map.of("written.yaml", text))).load("written.yaml");
		// verify
		// Taxonomy: same concept IDs and kinds.
		assertEquals(m.taxonomy.concepts.size(), m2.taxonomy.concepts.size());
		for (var e : m.taxonomy.concepts.entrySet()) {
			var want = e.getValue();
			var got = m2.taxonomy.concepts.get(e.getKey());
			assertNotNull(got, "concept \"" + e.getKey() + "\" lost in round trip");
			assertTrue(got.kind == want.kind && got.primType.equals(want.primType)
					&& got.unitType.equals(want.unitType) && got.parameter == want.parameter
					&& got.ref.equals(want.ref),
					"concept \"" + e.getKey() + "\": got " + got + ", want " + want);
			assertEquals(want.leafNames(), got.leafNames(), e.getKey());
		}
		// Modules: same sections and conditions.
		assertEquals(m.modules.size(), m2.modules.size());
		for (var e : m.modules.entrySet()) {
			var want = e.getValue();
			var got = m2.modules.get(e.getKey());
			assertNotNull(got, "module \"" + e.getKey() + "\" lost in round trip");
			assertTrue(got.title.equals(want.title) && got.active == want.active
					&& got.kind.equals(want.kind) && got.root == want.root,
					"module \"" + e.getKey() + "\": got " + got + ", want " + want);
			assertEquals(want.labels, got.labels, e.getKey());
			assertTrue((got.include == null) == (want.include == null),
					"module \"" + e.getKey() + "\": include presence differs");
			if (want.include != null) {
				assertEquals(want.include, got.include, e.getKey());
			}
			assertTrue((got.exclude == null) == (want.exclude == null),
					"module \"" + e.getKey() + "\": exclude presence differs");
			if (want.exclude != null) {
				assertEquals(want.exclude, got.exclude, e.getKey());
			}
		}
		// Roots survive; labels are compared order-insensitively because the
		// original model aggregates them in load order while the reloaded
		// model collects them in written order.
		assertEquals(m.rootIds, m2.rootIds);
		assertEquals(TestUtils.sortedLabelCopy(m.labels), TestUtils.sortedLabelCopy(m2.labels));
		// action: writing the reloaded model again reproduces the same bytes.
		assertArrayEquals(out, m2.writeYAML(), "output not stable:\nfirst:\n" + text);
	}

	@Test
	void writeYAMLExpressionForms() {
		// arrange
		var m = TestUtils.loadModel("""
                TAXONOMY:
                  t:
                    speed: float velocity
                    is_night: boolean
                    level: [a, b, c]
                ODD:
                  m:
                    INCLUDE_AND:
                      speed: "[2 .. 10] m/s"
                      is_night: false
                      level: [a, b]
                    EXCLUDE_OR:
                      speed: "> 25 m/s"
                """);
		// action
		var out = m.writeYAML();
		var text = new String(out, StandardCharsets.UTF_8);
		// verify: the written YAML is valid.
		assertNotNull(new Yaml().load(text), "written YAML is invalid:\n" + text);
		var doc = YamlParser.parse(text);
		var mod = doc.modules.get("m");
		var include = new HashMap<String, Expression>();
		for (var c : mod.include.conditions) {
			include.put(c.ref, c.expr);
		}
		var rng = assertInstanceOf(RangeExpr.class, include.get("speed"));
		assertTrue(rng.unit().equals("m/s") && rng.min().left().number() == 2
				&& rng.max().left().number() == 10, "range expression lost: " + rng);
		var eq = assertInstanceOf(EqualExpr.class, include.get("is_night"));
		assertTrue(eq.kind() == ScalarKind.BOOL && !eq.bool(), "boolean condition lost: " + eq);
		var list = assertInstanceOf(ListExpr.class, include.get("level"));
		assertEquals(List.of("a", "b"), list.items());
		var exclude = new HashMap<String, Expression>();
		for (var c : mod.exclude.conditions) {
			exclude.put(c.ref, c.expr);
		}
		var bound = assertInstanceOf(BoundExpr.class, exclude.get("speed"));
		assertTrue(bound.op() == CmpOp.GREATER && bound.unit().equals("m/s"),
				"bound expression lost: " + bound);
	}

	private record QuotingCase(String in, boolean plain) {
	}

	@Test
	void writeYAMLQuoting() {
		// arrange
		var tests = List.of(
				new QuotingCase("light_rain", true),
				new QuotingCase("rain_level", true),
				new QuotingCase("2.5 mm/h", true),
				new QuotingCase("true", false),
				new QuotingCase("false", false),
				new QuotingCase("yes", false),
				new QuotingCase("null", false),
				new QuotingCase("42", false),
				new QuotingCase("2.5", false),
				new QuotingCase("[2 .. 10] m/s", false),
				new QuotingCase("# comment", false),
				new QuotingCase("", false),
				new QuotingCase("a: b", false));
		for (var tt : tests) {
			var name = tt.in().isEmpty() ? "empty" : tt.in();
			// action
			var plain = !YamlWriter.needsQuoting(tt.in());
			// verify
			assertTrue(plain == tt.plain(),
					"needsQuoting(\"" + tt.in() + "\"): plain=" + plain + ", want " + tt.plain());
		}
		// Boolean-like literal names survive a round trip.
		{
			// arrange
			var m = TestUtils.loadModel(
					"TAXONOMY:\n  t:\n    flag:\n      \"true\": {x: \"1\"}\n      \"false\": {x: \"2\"}\n");
			// action
			var out = m.writeYAML();
			var text = new String(out, StandardCharsets.UTF_8);
			var doc = YamlParser.parse(text);
			// verify
			assertEquals(List.of("true", "false"), doc.taxonomy.concepts.get("t.flag").leafNames());
		}
	}
}
