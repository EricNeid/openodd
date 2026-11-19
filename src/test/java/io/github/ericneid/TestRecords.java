// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid;

import io.github.ericneid.openodd.value.Record;
import io.github.ericneid.openodd.value.Value;
import io.github.ericneid.openodd.value.Values;

import java.util.HashMap;

/**
 * Record builders of the test suite, shared by all test packages.
 * Parsing raw COD cells uses the public Values API of the library.
 */
public final class TestRecords {

	private TestRecords() {
	}

	/**
	 * Builds a record from alternating keys and values. String values are
	 * parsed as COD cells, booleans become boolean values and numbers
	 * become plain numeric values.
	 */
	public static Record record(Object... pairs) {
		var rec = new Record();
		putPairs(rec, pairs);
		return rec;
	}

	/** Copies a record and adds or replaces one parsed cell value. */
	public static Record recordWith(Record base, String key, String raw) {
		var out = new Record(new HashMap<>(base.values()));
		out.temporal(base.temporal()).spatial(base.spatial());
		out.values().put(key, Values.parseCellValue(raw, "", ""));
		return out;
	}

	private static void putPairs(Record rec, Object... pairs) {
		for (var i = 0; i + 1 < pairs.length; i += 2) {
			var key = (String) pairs[i];
			var v = pairs[i + 1];
			if (v instanceof Value value) {
				rec.values().put(key, value);
			} else if (v instanceof String s) {
				rec.values().put(key, Values.parseCellValue(s, "", ""));
			} else if (v instanceof Boolean b) {
				rec.values().put(key, Value.bool(b));
			} else if (v instanceof Double d) {
				rec.values().put(key, Value.number(d));
			} else {
				throw new IllegalArgumentException("unsupported value: " + v);
			}
		}
	}
}
