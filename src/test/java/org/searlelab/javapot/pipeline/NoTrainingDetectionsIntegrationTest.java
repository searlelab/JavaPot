package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.cli.OutputFormat;

class NoTrainingDetectionsIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void runCompletesWithZeroDetectionsWhenNoTrainingTargetsPassFdr() throws Exception {
		Path pin = tempDir.resolve("no_training_pass.pin");
		writeNoTrainingPassPin(pin);
		Path outDir = tempDir.resolve("out");
		Files.createDirectories(outDir);

		JavaPotOptions config = new JavaPotOptions(
			pin,
			outDir,
			1,
			OutputFormat.PERCOLATOR,
			true,
			0.01,
			0.01,
			3,
			11L,
			null,
			null,
			null,
			true,
			false,
			null,
			null,
			null,
			null,
			null,
			3,
			1,
			false
		);
		JavaPotRunResult result = JavaPotRunner.runForResult(config);

		Path psm = outDir.resolve("no_training_pass.psms.tsv");
		Path pep = outDir.resolve("no_training_pass.peptides.tsv");
		assertTrue(Files.exists(psm), "PSM output missing");
		assertTrue(Files.exists(pep), "Peptide output missing");
		assertAllConfidenceIsForcedToOne(psm);
		assertAllConfidenceIsForcedToOne(pep);
		assertAllScoresFinite(psm);
		assertAllScoresFinite(pep);
		assertEquals(0, countTargetsAtThreshold(pep, 0.01), "Expected zero peptide detections at q<=0.01");
		assertEquals(0, countTargetsAtThreshold(result.peptides(), 0.01), "Expected zero API peptide detections at q<=0.01");
		assertTrue(hasFiniteScores(result.psms()), "Expected finite API PSM scores");
		assertTrue(hasFiniteScores(result.peptides()), "Expected finite API peptide scores");
	}

	private static void writeNoTrainingPassPin(Path file) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SpecId\tLabel\tScanNr\tExpMass\tfeatA\tfeatB\tPeptide\tProteins\n");
		sb.append("t1\t1\t1\t500.01\t0.0\t0.0\tPEPTIDE1\tP1\n");
		sb.append("t2\t1\t2\t500.02\t0.0\t0.0\tPEPTIDE2\tP2\n");
		sb.append("t3\t1\t3\t500.03\t0.0\t0.0\tPEPTIDE3\tP3\n");
		sb.append("t4\t1\t4\t500.04\t0.0\t0.0\tPEPTIDE4\tP4\n");
		sb.append("d1\t-1\t5\t500.05\t0.0\t0.0\tDECOY1\tD1\n");
		sb.append("d2\t-1\t6\t500.06\t0.0\t0.0\tDECOY2\tD2\n");
		sb.append("d3\t-1\t7\t500.07\t0.0\t0.0\tDECOY3\tD3\n");
		sb.append("d4\t-1\t8\t500.08\t0.0\t0.0\tDECOY4\tD4\n");
		Files.writeString(file, sb.toString());
	}

	private static void assertAllConfidenceIsForcedToOne(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				throw new AssertionError("Missing header: " + file);
			}
			String[] cols = header.split("\\t");
			int qIdx = findColumn(cols, "q-value", "mokapot_qvalue");
			int pepIdx = findColumn(cols, "posterior_error_prob", "mokapot_posterior_error_prob");
			if (qIdx < 0 || pepIdx < 0) {
				throw new AssertionError("Missing confidence columns in: " + file);
			}
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				assertEquals(1.0, Double.parseDouble(parts[qIdx]), 1e-12);
				assertEquals(1.0, Double.parseDouble(parts[pepIdx]), 1e-12);
			}
		}
	}

	private static void assertAllScoresFinite(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				throw new AssertionError("Missing header: " + file);
			}
			String[] cols = header.split("\\t");
			int scoreIdx = findColumn(cols, "score", "mokapot_score");
			if (scoreIdx < 0) {
				throw new AssertionError("Missing score column in: " + file);
			}
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				assertTrue(Double.isFinite(Double.parseDouble(parts[scoreIdx])));
			}
		}
	}

	private static int countTargetsAtThreshold(Path file, double threshold) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return 0;
			}
			String[] cols = header.split("\\t");
			int qIdx = findColumn(cols, "q-value", "mokapot_qvalue");
			if (qIdx < 0) {
				throw new AssertionError("Missing q-value column in: " + file);
			}
			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				if (Double.parseDouble(parts[qIdx]) <= threshold) {
					count++;
				}
			}
			return count;
		}
	}

	private static int countTargetsAtThreshold(Iterable<JavaPotPeptide> rows, double threshold) {
		int count = 0;
		for (JavaPotPeptide row : rows) {
			if (!row.isDecoy() && row.qValue() <= threshold) {
				count++;
			}
		}
		return count;
	}

	private static boolean hasFiniteScores(Iterable<JavaPotPeptide> rows) {
		for (JavaPotPeptide row : rows) {
			if (!Double.isFinite(row.score())) {
				return false;
			}
		}
		return true;
	}

	private static int findColumn(String[] cols, String... names) {
		for (int i = 0; i < cols.length; i++) {
			for (String name : names) {
				if (cols[i].equals(name)) {
					return i;
				}
			}
		}
		return -1;
	}
}
