package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;

class SeedConsistencyIntegrationTest {
	private static final double EVAL_FDR = 0.01;
	private static final double MAX_RELATIVE_DIFF = 0.05;
	private static final double MIN_PEPTIDE_OVERLAP = 0.90;

	@TempDir
	Path tempDir;

	@Test
	void repeatedSeedRunsAreDeterministicAndDifferentSeedsStayComparable() throws Exception {
		Path pinFile = resourcePin();
		RunOutput seedOneA = runAnalysis(pinFile, 1L, "seed1_a");
		RunOutput seedOneB = runAnalysis(pinFile, 1L, "seed1_b");
		RunOutput seedTwo = runAnalysis(pinFile, 2L, "seed2");

		assertEquals(-1L, Files.mismatch(seedOneA.psmFile(), seedOneB.psmFile()), "PSM output changed for same seed");
		assertEquals(-1L, Files.mismatch(seedOneA.peptideFile(), seedOneB.peptideFile()), "Peptide output changed for same seed");
		assertEquals(seedOneA.summary(), seedOneB.summary(), "Summary changed for same seed");

		boolean seedChangedOutput =
			Files.mismatch(seedOneA.psmFile(), seedTwo.psmFile()) != -1L ||
			Files.mismatch(seedOneA.peptideFile(), seedTwo.peptideFile()) != -1L;
		assertTrue(seedChangedOutput, "Changing seed should produce a slightly different result");

		assertRelativeDiffAtMost(
			seedOneA.summary().psmAtFdr(),
			seedTwo.summary().psmAtFdr(),
			MAX_RELATIVE_DIFF,
			"PSM count"
		);
		assertRelativeDiffAtMost(
			seedOneA.summary().peptideAtFdr(),
			seedTwo.summary().peptideAtFdr(),
			MAX_RELATIVE_DIFF,
			"Peptide count"
		);

		double overlapOneInTwo = overlapFraction(seedOneA.summary().peptidesAtFdr(), seedTwo.summary().peptidesAtFdr());
		double overlapTwoInOne = overlapFraction(seedTwo.summary().peptidesAtFdr(), seedOneA.summary().peptidesAtFdr());
		double overlap = Math.min(overlapOneInTwo, overlapTwoInOne);
		assertTrue(
			overlap >= MIN_PEPTIDE_OVERLAP,
			"Peptide overlap should be >= " + MIN_PEPTIDE_OVERLAP +
				", observed min overlap=" + overlap +
				" (seed1->seed2=" + overlapOneInTwo + ", seed2->seed1=" + overlapTwoInOne + ")"
		);
	}

	private RunOutput runAnalysis(Path pinFile, long seed, String runName) throws IOException {
		Path outputDir = tempDir.resolve(runName);
		Files.createDirectories(outputDir);
		JavaPotCli.main(new String[]{
			pinFile.toString(),
			"--dest_dir", outputDir.toString(),
			"--max_workers", "2",
			"--write_psm_files",
			"--seed", Long.toString(seed)
		});

		Path psmFile = outputDir.resolve("10k_psms_test.psms.tsv");
		Path peptideFile = outputDir.resolve("10k_psms_test.peptides.tsv");
		RunSummary summary = new RunSummary(
			countAtThreshold(psmFile, EVAL_FDR),
			countAtThreshold(peptideFile, EVAL_FDR),
			readPeptidesAtThreshold(peptideFile, EVAL_FDR)
		);
		return new RunOutput(psmFile, peptideFile, summary);
	}

	private static Path resourcePin() throws URISyntaxException {
		return Path.of(Objects.requireNonNull(
			SeedConsistencyIntegrationTest.class.getResource("/data/10k_psms_test.pin"),
			"Resource not found: /data/10k_psms_test.pin"
		).toURI());
	}

	private static int countAtThreshold(Path file, double threshold) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return 0;
			}
			int qIdx = findColumnIndex(header, "q-value", "mokapot_qvalue", "mokapot q-value");
			if (qIdx < 0) {
				throw new IllegalStateException("q-value column not found in " + file);
			}

			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				double q = Double.parseDouble(parts[qIdx]);
				if (q <= threshold) {
					count++;
				}
			}
			return count;
		}
	}

	private static Set<String> readPeptidesAtThreshold(Path file, double threshold) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return Set.of();
			}

			String[] cols = header.split("\\t");
			int qIdx = -1;
			int peptideIdx = -1;
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].equals("q-value") || cols[i].equals("mokapot_qvalue") || cols[i].equals("mokapot q-value")) {
					qIdx = i;
				} else if (cols[i].equalsIgnoreCase("Peptide")) {
					peptideIdx = i;
				}
			}
			if (qIdx < 0 || peptideIdx < 0) {
				throw new IllegalStateException("Required columns not found in " + file);
			}

			Set<String> peptides = new HashSet<>();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				double q = Double.parseDouble(parts[qIdx]);
				if (q <= threshold) {
					peptides.add(parts[peptideIdx]);
				}
			}
			return peptides;
		}
	}

	private static int findColumnIndex(String header, String... names) {
		String[] cols = header.split("\\t");
		for (int i = 0; i < cols.length; i++) {
			for (String name : names) {
				if (cols[i].equals(name)) {
					return i;
				}
			}
		}
		return -1;
	}

	private static double overlapFraction(Set<String> reference, Set<String> observed) {
		if (reference.isEmpty()) {
			return 1.0;
		}
		int shared = 0;
		for (String peptide : reference) {
			if (observed.contains(peptide)) {
				shared++;
			}
		}
		return shared / (double) reference.size();
	}

	private static void assertRelativeDiffAtMost(int observed, int reference, double maxRelativeDiff, String label) {
		double relativeDiff = Math.abs(observed - reference) / Math.max(1.0, reference);
		assertTrue(
			relativeDiff <= maxRelativeDiff,
			label + " relative difference should be <= " + maxRelativeDiff + ", observed=" + relativeDiff +
				" (observed=" + observed + ", reference=" + reference + ")"
		);
	}

	private record RunSummary(int psmAtFdr, int peptideAtFdr, Set<String> peptidesAtFdr) {
	}

	private record RunOutput(Path psmFile, Path peptideFile, RunSummary summary) {
	}
}
