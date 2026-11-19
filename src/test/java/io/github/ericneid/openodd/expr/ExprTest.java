// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid.openodd.expr;

import io.github.ericneid.openodd.OpenOddException;
import io.github.ericneid.openodd.parser.YamlParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the scalar expression parser. */
class ExprTest {

	private record Case(String in, Expression want) {
	}

	@Test
	void parseExpression() {
		// arrange
		var tests = List.of(
				new Case("true", EqualExpr.boolValue(true)),
				new Case("True", EqualExpr.boolValue(true)),
				new Case("false", EqualExpr.boolValue(false)),
				new Case("unknown", EqualExpr.unknown()),
				new Case("none", EqualExpr.unknown()),
				new Case("null", EqualExpr.unknown()),
				new Case("40", EqualExpr.number(40, "")),
				new Case("2.5", EqualExpr.number(2.5, "")),
				new Case("40 km/h", EqualExpr.number(40, "km/h")),
				new Case("91%", EqualExpr.number(91, "%")),
				new Case("light_rain", EqualExpr.literal("light_rain")),
				new Case("dry", EqualExpr.literal("dry")),
				new Case("< 40 km/h", new BoundExpr(CmpOp.LESS, Term.of(Factor.number(40)), "km/h")),
				new Case("<= 2.5 mm/h", new BoundExpr(CmpOp.LESS_EQUAL, Term.of(Factor.number(2.5)), "mm/h")),
				new Case(">=7.6 mm/h", new BoundExpr(CmpOp.GREATER_EQUAL, Term.of(Factor.number(7.6)), "mm/h")),
				new Case("> 15 mm/h", new BoundExpr(CmpOp.GREATER, Term.of(Factor.number(15)), "mm/h")),
				new Case("[2 .. 10] mm/h",
						new RangeExpr(Term.of(Factor.number(2)), Term.of(Factor.number(10)), "mm/h")),
				new Case("[fog_mist, fog_medium]", new ListExpr(List.of("fog_mist", "fog_medium"))),
				new Case("> 1.75*ego_width", new BoundExpr(CmpOp.GREATER,
						new Term(Factor.number(1.75), '*', Factor.ref("ego_width")), "")),
				new Case("> 0.5 * $ego_width", new BoundExpr(CmpOp.GREATER,
						new Term(Factor.number(0.5), '*', Factor.ref("$ego_width")), "")),
				new Case("[1.25*a .. 2.25*a]", new RangeExpr(
						new Term(Factor.number(1.25), '*', Factor.ref("a")),
						new Term(Factor.number(2.25), '*', Factor.ref("a")), "")),
				new Case("[2/3 .. 4/3]", new RangeExpr(
						new Term(Factor.number(2), '/', Factor.number(3)),
						new Term(Factor.number(4), '/', Factor.number(3)), "")));
		for (var tt : tests) {
			// action
			var got = Expressions.parse(tt.in());
			// verify
			assertEquals(tt.want(), got, tt.in());
			// The rendered form must parse back to the same expression.
			var reparsed = Expressions.parse(got.toString());
			assertEquals(tt.want(), reparsed, tt.in() + " re-parsed");
		}
	}

	@Test
	void parseExpressionErrors() {
		// arrange
		var inputs = List.of(
				"",                   // empty
				"[2 .. 10",           // unterminated bracket
				"[a, b] m/s",         // list expressions take no units
				"> 1.75*ego_width*2",  // terms combine exactly two factors
				"> ");                // missing term
		for (var in : inputs) {
			var name = in.isEmpty() ? "empty" : in;
			// action & verify
			assertThrows(OpenOddException.class, () -> Expressions.parse(in), name);
		}
	}

	@Test
	void parseBareRange() {
		// Unquoted YAML sequences deliver ranges without their brackets.
		{
			// action
			var got = Expressions.parse("2 .. 10");
			// verify
			var rng = assertInstanceOf(RangeExpr.class, got);
			assertTrue(rng.min().left().number() == 2 && rng.max().left().number() == 10 && rng.unit().isEmpty(),
					"unexpected range: " + rng);
		}
		{
			// action
			var got = Expressions.parse("a..b");
			// verify
			assertFalse(got instanceof RangeExpr, "a..b should not become a range: " + got);
		}
	}

	@Test
	void parseExpressionFromYAMLSequence() {
		{
			// action
			var doc = YamlParser.parse("MODULES:\n  m:\n    INCLUDE_AND:\n      speed: [2 .. 10]\n");
			// verify
			var expr = doc.modules.get("m").include.conditions.get(0).expr;
			var rng = assertInstanceOf(RangeExpr.class, expr);
			assertTrue(rng.min().left().number() == 2 && rng.max().left().number() == 10 && rng.unit().isEmpty(),
					"unexpected range " + rng);
		}
		{
			// action
			var doc = YamlParser.parse("MODULES:\n  m:\n    INCLUDE_AND:\n      state: [icy, snowy]\n");
			// verify
			var expr = doc.modules.get("m").include.conditions.get(0).expr;
			var list = assertInstanceOf(ListExpr.class, expr);
			assertEquals(List.of("icy", "snowy"), list.items());
		}
		{
			// action
			var doc = YamlParser.parse("MODULES:\n  m:\n    INCLUDE_AND:\n      other: true\n");
			// verify
			var expr = doc.modules.get("m").include.conditions.get(0).expr;
			var eq = assertInstanceOf(EqualExpr.class, expr);
			assertTrue(eq.kind() == ScalarKind.BOOL && eq.bool(), "expected a boolean equality, got " + expr);
		}
	}
}
