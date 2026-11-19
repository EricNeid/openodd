// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Eric Neidhardt
package io.github.ericneid;

import io.github.ericneid.openodd.model.Model;
import io.github.ericneid.openodd.parser.Loader;
import io.github.ericneid.openodd.parser.MapResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shared helpers of the test suite. The record builders live in
 * io.github.ericneid.TestRecords.
 */
public final class TestUtils {

	/** The weather taxonomy shared by several tests. */
	public static final String WEATHER_TAXONOMY_YAML = """
            TAXONOMY:
              environmental_conditions:
                weather:
                  rainfall_rate: float precipitation_rate
                  rain_level:
                    no_rain: {rainfall_rate: "[0 .. 0.1] mm/h"}
                    light_rain: {rainfall_rate: "[0.1 .. 2.5] mm/h"}
                    moderate_rain: {rainfall_rate: "[2.5 .. 7.6] mm/h"}
                    heavy_rain: {rainfall_rate: "[7.6 .. 15] mm/h"}
                  road_state: [dry, wet, icy, snowy]
                time_of_day:
                  is_night: boolean
                wind:
                  wind_speed: float velocity
              ego_vehicle:
                ego_width: float length
              obstacle:
                lateral_distance: float length
            """;

	private TestUtils() {
	}

	/** Resolves a test resource to a path on disk. */
	public static Path resourcePath(String relative) {
		try {
			return Path.of(TestUtils.class.getResource("/" + relative).toURI());
		} catch (Exception e) {
			throw new IllegalStateException("test resource not found: " + relative, e);
		}
	}

	/** Reads a test resource as UTF-8 text. */
	public static String resourceText(String relative) {
		try {
			return Files.readString(resourcePath(relative), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Loads a single-file model from YAML text. */
	public static Model loadModel(String src) {
		return new Loader(new MapResolver(Map.of("spec.yaml", src))).load("spec.yaml");
	}

	/** Returns the labels with each module list sorted, for comparisons. */
	public static Map<String, List<String>> sortedLabelCopy(Map<String, List<String>> labels) {
		var out = new TreeMap<String, List<String>>();
		for (var e : labels.entrySet()) {
			var sorted = new ArrayList<String>(e.getValue());
			Collections.sort(sorted);
			out.put(e.getKey(), sorted);
		}
		return out;
	}
}
