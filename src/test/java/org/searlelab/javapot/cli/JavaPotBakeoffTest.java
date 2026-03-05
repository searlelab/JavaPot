package org.searlelab.javapot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaPotBakeoffTest {
	@TempDir
	Path tempDir;

	@Test
	void parseDiscoversDirectoryFilesInSortedOrder() throws Exception {
		Path featureDir = tempDir.resolve("features");
		Files.createDirectories(featureDir);
		Path aPin = featureDir.resolve("a.pin");
		Path bTxt = featureDir.resolve("b.txt");
		Files.writeString(aPin, "header\n");
		Files.writeString(bTxt, "header\n");
		Files.writeString(featureDir.resolve("ignore.csv"), "header\n");

		JavaPotBakeoff.BakeoffConfig config = JavaPotBakeoff.parse(new String[]{
			"--direction", "featA",
			featureDir.toString()
		});

		assertEquals(featureDir, config.featureSetDir());
		assertEquals(List.of(aPin, bTxt), config.pinFiles());
		assertEquals("featA", config.direction());
		assertEquals(List.of("featA"), config.requiredStartFeatures());
		assertEquals(0.1, config.minImprovementPercent());
		assertEquals(JavaPotOptions.DEFAULT_FOLDS, config.folds());
		assertEquals(JavaPotOptions.DEFAULT_FOLDS, config.maxWorkers());
	}

	@Test
	void parseAcceptsCommaSeparatedRequiredStartingFeatures() throws Exception {
		Path featureDir = tempDir.resolve("features");
		Files.createDirectories(featureDir);
		Files.writeString(featureDir.resolve("a.txt"), "header\n");

		JavaPotBakeoff.BakeoffConfig config = JavaPotBakeoff.parse(new String[]{
			"--direction", "scribeScore,charge1,charge2,charge1, charge3 ",
			featureDir.toString()
		});

		assertEquals("scribeScore", config.direction());
		assertEquals(List.of("scribeScore", "charge1", "charge2", "charge3"), config.requiredStartFeatures());
	}

	@Test
	void parseRejectsMissingDirectionOrBadDirectoryInputs() throws Exception {
		Path featureDir = tempDir.resolve("features");
		Files.createDirectories(featureDir);
		Files.writeString(featureDir.resolve("a.txt"), "header\n");

		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA"})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", featureDir.toString(), featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", tempDir.resolve("missing").toString()})
		);
	}

	@Test
	void greedyBakeoffPicksBestFeatureAndStopsAtThreshold() {
		Map<String, Long> scores = new HashMap<>();
		scores.put("A", 1000L);
		scores.put("A,B", 1005L);
		scores.put("A,C", 1400L);
		scores.put("A,D", 1200L);
		scores.put("A,C,B", 1401L);
		scores.put("A,C,D", 1500L);
		scores.put("A,C,D,B", 1501L);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		JavaPotBakeoff.BakeoffOutcome outcome = JavaPotBakeoff.runGreedyBakeoff(
			List.of("A", "B", "C", "D"),
			List.of("A"),
			defaultDirectionalMap("A", "B", "C", "D"),
			0.1,
			featureSet -> {
				String key = String.join(",", featureSet);
				Long value = scores.get(key);
				if (value == null) {
					throw new AssertionError("No score configured for feature set: " + key);
				}
				return new JavaPotBakeoff.TrialEvaluation(value, Set.of());
			},
			out
		);

		assertEquals(List.of("A", "C", "D"), outcome.keptFeatures());
		assertEquals(1500L, outcome.totalPeptides());
		String log = baos.toString();
		assertTrue(log.contains("Starting with A"));
		assertTrue(log.contains("1400 A, C"));
		assertTrue(log.contains("Picking A, C to continue"));
		assertTrue(log.contains("Picking A, C, D to continue"));
		assertTrue(log.contains("Stopping because best candidate improvement"));
	}

	@Test
	void greedyBakeoffStartsFromMultipleRequiredFeatures() {
		Map<String, Long> scores = new HashMap<>();
		scores.put("A,B", 1000L);
		scores.put("A,B,C", 1300L);
		scores.put("A,B,D", 1200L);
		scores.put("A,B,C,D", 1301L);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		JavaPotBakeoff.BakeoffOutcome outcome = JavaPotBakeoff.runGreedyBakeoff(
			List.of("A", "B", "C", "D"),
			List.of("A", "B"),
			defaultDirectionalMap("A", "B", "C", "D"),
			0.1,
			featureSet -> {
				String key = String.join(",", featureSet);
				Long value = scores.get(key);
				if (value == null) {
					throw new AssertionError("No score configured for feature set: " + key);
				}
				return new JavaPotBakeoff.TrialEvaluation(value, Set.of());
			},
			out
		);

		assertEquals(List.of("A", "B", "C"), outcome.keptFeatures());
		assertEquals(1300L, outcome.totalPeptides());
		String log = baos.toString();
		assertTrue(log.contains("Starting with A, B"));
		assertTrue(log.contains("1300 A, B, C"));
		assertTrue(log.contains("Picking A, B, C to continue"));
	}

	@Test
	void greedyBakeoffAnnotatesFlippedAndLevelFeatures() {
		Map<String, Long> scores = new HashMap<>();
		scores.put("A,B", 1000L);
		scores.put("A,B,C", 1500L);

		Map<String, JavaPotBakeoff.FeatureDirectionality> directionality = new HashMap<>();
		directionality.put("A", JavaPotBakeoff.FeatureDirectionality.HIGH_TARGET_WHEN_HIGHER);
		directionality.put("B", JavaPotBakeoff.FeatureDirectionality.LEVEL);
		directionality.put("C", JavaPotBakeoff.FeatureDirectionality.HIGH_TARGET_WHEN_HIGHER);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(baos);
		JavaPotBakeoff.runGreedyBakeoff(
			List.of("A", "B", "C"),
			List.of("A", "B"),
			directionality,
			0.1,
			featureSet -> {
				String key = String.join(",", featureSet);
				Long value = scores.get(key);
				if (value == null) {
					throw new AssertionError("No score configured for feature set: " + key);
				}
				Set<String> flipped = key.equals("A,B,C") ? Set.of("C") : Set.of();
				return new JavaPotBakeoff.TrialEvaluation(value, flipped);
			},
			out
		);

		String log = baos.toString();
		assertTrue(log.contains("Starting with A, B~"));
		assertTrue(log.contains("1500 A, B~, C*"));
		assertTrue(log.contains("Picking A, B~, C* to continue"));
	}

	private static Map<String, JavaPotBakeoff.FeatureDirectionality> defaultDirectionalMap(String... features) {
		Map<String, JavaPotBakeoff.FeatureDirectionality> out = new HashMap<>();
		for (String feature : features) {
			out.put(feature, JavaPotBakeoff.FeatureDirectionality.HIGH_TARGET_WHEN_HIGHER);
		}
		return out;
	}
}
