package org.searlelab.javapot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
		writeString(aPin, "header\n");
		writeString(bTxt, "header\n");
		writeString(featureDir.resolve("ignore.csv"), "header\n");

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
		writeString(featureDir.resolve("a.txt"), "header\n");

		JavaPotBakeoff.BakeoffConfig config = JavaPotBakeoff.parse(new String[]{
			"--direction", "scribeScore,charge1,charge2,charge1, charge3 ",
			featureDir.toString()
		});

		assertEquals("scribeScore", config.direction());
		assertEquals(List.of("scribeScore", "charge1", "charge2", "charge3"), config.requiredStartFeatures());
	}

	@Test
	void parseAcceptsAllNumericOptionsAndAliases() throws Exception {
		Path featureDir = tempDir.resolve("features");
		Files.createDirectories(featureDir);
		Path input = featureDir.resolve("z.pin");
		writeString(input, "header\n");

		JavaPotBakeoff.BakeoffConfig config = JavaPotBakeoff.parse(new String[]{
			"--direction", "featA,featB",
			"--train_fdr", "0.02",
			"--test_fdr", "0.03",
			"--max_iter", "7",
			"--seed", "42",
			"--subset_max_train", "500",
			"--folds", "4",
			"-w", "2",
			"--max_retries", "3",
			"--post-processing-mix-max",
			"--min_improvement_percent", "1.5",
			featureDir.toString()
		});

		assertEquals(List.of(input), config.pinFiles());
		assertEquals("featA", config.direction());
		assertEquals(List.of("featA", "featB"), config.requiredStartFeatures());
		assertEquals(0.02, config.trainFdr());
		assertEquals(0.03, config.testFdr());
		assertEquals(7, config.maxIter());
		assertEquals(42L, config.seed());
		assertEquals(500, config.subsetMaxTrain());
		assertEquals(4, config.folds());
		assertEquals(2, config.maxWorkers());
		assertEquals(3, config.maxRetries());
		assertTrue(config.mixmax());
		assertEquals(1.5, config.minImprovementPercent());
	}

	@Test
	void parseRejectsMissingDirectionOrBadDirectoryInputs() throws Exception {
		Path featureDir = tempDir.resolve("features");
		Files.createDirectories(featureDir);
		writeString(featureDir.resolve("a.txt"), "header\n");

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
	void parseRejectsInvalidOptionsAndValues() throws Exception {
		Path featureDir = tempDir.resolve("features");
		Files.createDirectories(featureDir);
		writeString(featureDir.resolve("a.pin"), "header\n");
		Path emptyDir = tempDir.resolve("empty");
		Files.createDirectories(emptyDir);

		assertThrows(
			JavaPotBakeoff.HelpRequestedException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--help"})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--unknown", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--folds", "1", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--max_workers", "0", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--max_retries", "-1", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--max_iter", "0", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--subset_max_train", "0", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--train_fdr", "1.0", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--test_fdr", "0.0", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--min_improvement_percent", "-0.1", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--seed", "abc", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", "--train_fdr", "abc", featureDir.toString()})
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> JavaPotBakeoff.parse(new String[]{"--direction", "featA", emptyDir.toString()})
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

	@Test
	void mainPrintsHelpForEmptyArgsAndHelpFlag() {
		String noArgsOutput = captureStdout(() -> JavaPotBakeoff.main(new String[0]));
		String helpOutput = captureStdout(() -> JavaPotBakeoff.main(new String[]{"--help"}));

		assertTrue(noArgsOutput.contains("Usage: javapot-bakeoff"));
		assertTrue(helpOutput.contains("--min_improvement_percent"));
	}

	@Test
	void runRejectsMissingRequiredStartFeature() throws Exception {
		Path pin = writeSyntheticPin(tempDir.resolve("single.pin"), false, false);
		JavaPotBakeoff.BakeoffConfig config = new JavaPotBakeoff.BakeoffConfig(
			tempDir,
			List.of(pin),
			"missingFeature",
			List.of("missingFeature"),
			0.5,
			0.5,
			10,
			1L,
			null,
			3,
			1,
			1,
			false,
			0.1
		);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> JavaPotBakeoff.run(config));
		assertTrue(ex.getMessage().contains("Required starting feature(s) not found"));
	}

	@Test
	void runRejectsMismatchedHeadersAcrossSources() throws Exception {
		Path first = writeSyntheticPin(tempDir.resolve("first.pin"), false, false);
		Path second = writeSyntheticPin(tempDir.resolve("second.pin"), true, false);
		JavaPotBakeoff.BakeoffConfig config = new JavaPotBakeoff.BakeoffConfig(
			tempDir,
			List.of(first, second),
			"featHigh",
			List.of("featHigh"),
			0.5,
			0.5,
			10,
			1L,
			null,
			3,
			1,
			1,
			false,
			0.1
		);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> JavaPotBakeoff.run(config));
		assertTrue(ex.getMessage().contains("identical headers"));
	}

	@Test
	void runCompletesEndToEndAcrossSources() throws Exception {
		Path first = writeSyntheticPin(tempDir.resolve("a.pin"), false, false);
		Path second = writeSyntheticPin(tempDir.resolve("b.pin"), false, true);
		JavaPotBakeoff.BakeoffConfig config = new JavaPotBakeoff.BakeoffConfig(
			tempDir,
			List.of(first, second),
			"featHigh",
			List.of("featHigh"),
			0.5,
			0.5,
			5,
			1L,
			null,
			2,
			1,
			1,
			false,
			0.0
		);

		String output = captureStdout(() -> {
			JavaPotBakeoff.BakeoffOutcome outcome = JavaPotBakeoff.run(config);
			assertFalse(outcome.keptFeatures().isEmpty());
			assertEquals("featHigh", outcome.keptFeatures().get(0));
			assertTrue(outcome.totalPeptides() >= 0L);
		});

		assertTrue(output.contains("Loaded 2 feature files from"));
		assertTrue(output.contains("Directionality baseline:"));
		assertTrue(output.contains("Starting with featHigh"));
		assertTrue(output.contains("Final feature set:"));
		assertTrue(output.contains("Final total unique peptides:"));
	}

	private static Map<String, JavaPotBakeoff.FeatureDirectionality> defaultDirectionalMap(String... features) {
		Map<String, JavaPotBakeoff.FeatureDirectionality> out = new HashMap<>();
		for (String feature : features) {
			out.put(feature, JavaPotBakeoff.FeatureDirectionality.HIGH_TARGET_WHEN_HIGHER);
		}
		return out;
	}

	private static String captureStdout(Runnable action) {
		PrintStream original = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (PrintStream capture = new PrintStream(baos)) {
			System.setOut(capture);
			action.run();
		} finally {
			System.setOut(original);
		}
		return baos.toString();
	}

	private static Path writeSyntheticPin(Path file, boolean addExtraHeader, boolean invertSecondFeature) throws IOException {
		List<String> headers = new ArrayList<>(List.of(
			"SpecId", "Label", "ScanNr", "ExpMass", "featHigh", "featLow", "featLevel", "Peptide", "Proteins"
		));
		if (addExtraHeader) {
			headers.add(4, "featExtra");
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.join("\t", headers)).append('\n');
		for (int scan = 1; scan <= 18; scan++) {
			double expMass = 500.0 + scan;
			double targetHigh = 30.0 - scan;
			double targetLow = invertSecondFeature ? 10.0 + scan : 10.0 - scan;
			double decoyHigh = targetHigh - 15.0;
			double decoyLow = invertSecondFeature ? targetLow - 15.0 : targetLow + 15.0;
			appendRow(sb, "t" + scan + "a", 1, scan, expMass, targetHigh + 1.0, targetLow, 0.0, addExtraHeader, "PEPTIDE_" + scan + "_A");
			appendRow(sb, "t" + scan + "b", 1, scan, expMass, targetHigh, targetLow + 0.25, 0.0, addExtraHeader, "PEPTIDE_" + scan + "_B");
			appendRow(sb, "d" + scan, -1, scan, expMass, decoyHigh, decoyLow, 0.0, addExtraHeader, "DECOY_" + scan);
		}
		writeString(file, sb.toString());
		return file;
	}

	private static void writeString(Path file, String contents) throws IOException {
		Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
	}

	private static void appendRow(
		StringBuilder sb,
		String specId,
		int label,
		int scan,
		double expMass,
		double featHigh,
		double featLow,
		double featLevel,
		boolean addExtraHeader,
		String peptide
	) {
		sb.append(specId).append('\t')
			.append(label).append('\t')
			.append(scan).append('\t')
			.append(expMass).append('\t');
		if (addExtraHeader) {
			sb.append(scan * 0.1).append('\t');
		}
		sb.append(featHigh).append('\t')
			.append(featLow).append('\t')
			.append(featLevel).append('\t')
			.append(peptide).append('\t')
			.append(label > 0 ? "PROT_T" : "PROT_D")
			.append('\n');
	}
}
