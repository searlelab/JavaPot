package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;

class MixMaxIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void mixmaxSkipsSpectrumCompetitionAndProducesDeterministicPeps() throws Exception {
		Path pinFile = writeSyntheticSeparateSearchPin(tempDir.resolve("synthetic_mixmax.pin"));
		Path tdcOut = runAnalysis(pinFile, tempDir.resolve("tdc"), false, 7L);
		Path mixmaxOutA = runAnalysis(pinFile, tempDir.resolve("mixmax_a"), true, 7L);
		Path mixmaxOutB = runAnalysis(pinFile, tempDir.resolve("mixmax_b"), true, 7L);

		Path tdcPsm = tdcOut.resolve("synthetic_mixmax.psms.tsv");
		Path mixmaxPsmA = mixmaxOutA.resolve("synthetic_mixmax.psms.tsv");
		Path mixmaxPepA = mixmaxOutA.resolve("synthetic_mixmax.peptides.tsv");
		Path mixmaxPsmB = mixmaxOutB.resolve("synthetic_mixmax.psms.tsv");
		Path mixmaxPepB = mixmaxOutB.resolve("synthetic_mixmax.peptides.tsv");

		assertEquals(0, countDuplicateSpectra(tdcPsm), "TDC output should contain one target per spectrum key");
		assertTrue(countDuplicateSpectra(mixmaxPsmA) > 0, "Mix-max output should retain multiple targets per spectrum key");
		assertTrue(countDataRows(mixmaxPsmA) > countDataRows(tdcPsm), "Mix-max PSM output should be larger than TDC output");

		assertEquals(-1L, Files.mismatch(mixmaxPsmA, mixmaxPsmB), "Mix-max PSM output should be deterministic for fixed seed");
		assertEquals(-1L, Files.mismatch(mixmaxPepA, mixmaxPepB), "Mix-max peptide output should be deterministic for fixed seed");

		assertTrue(hasQvaluePepDifference(mixmaxPsmA), "PSM PEP values should not collapse to q-values");
		assertTrue(hasQvaluePepDifference(mixmaxPepA), "Peptide PEP values should not collapse to q-values");
	}

	private static Path runAnalysis(Path pinFile, Path outputDir, boolean mixmax, long seed) throws IOException {
		Files.createDirectories(outputDir);
		if (mixmax) {
			JavaPotCli.main(new String[]{
				pinFile.toString(),
				"--dest_dir", outputDir.toString(),
				"--output_format", "mokapot",
				"--max_workers", "1",
				"--write_psm_files",
				"--folds", "2",
				"--max_iter", "2",
				"--seed", Long.toString(seed),
				"--train_fdr", "0.5",
				"--test_fdr", "0.5",
				"--mixmax"
			});
		} else {
			JavaPotCli.main(new String[]{
				pinFile.toString(),
				"--dest_dir", outputDir.toString(),
				"--output_format", "mokapot",
				"--max_workers", "1",
				"--write_psm_files",
				"--folds", "2",
				"--max_iter", "2",
				"--seed", Long.toString(seed),
				"--train_fdr", "0.5",
				"--test_fdr", "0.5"
			});
		}
		return outputDir;
	}

	private static Path writeSyntheticSeparateSearchPin(Path file) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins\n");
		for (int scan = 1; scan <= 12; scan++) {
			double expMass = 500.0 + scan;
			double base = 40.0 - scan;
			sb.append("t").append(scan).append("a\t1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(base + 1.0).append("\tPEPTIDE_").append(scan).append("_A\tPROT_T\n");
			sb.append("t").append(scan).append("b\t1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(base + 0.5).append("\tPEPTIDE_").append(scan).append("_B\tPROT_T\n");
			sb.append("d").append(scan).append("\t-1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(base - 3.0).append("\tDECOY_").append(scan).append("\tPROT_D\n");
		}
		Files.writeString(file, sb.toString());
		return file;
	}

	private static int countDataRows(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			reader.readLine();
			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank()) {
					count++;
				}
			}
			return count;
		}
	}

	private static int countDuplicateSpectra(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return 0;
			}
			String[] cols = header.split("\\t");
			int scanIdx = findColumnIndex(cols, "ScanNr");
			int expMassIdx = findColumnIndex(cols, "ExpMass");
			if (scanIdx < 0 || expMassIdx < 0) {
				throw new IllegalStateException("ScanNr/ExpMass columns not found in " + file);
			}
			int duplicates = 0;
			Set<String> seen = new HashSet<>();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				String key = parts[scanIdx] + "\u0001" + parts[expMassIdx];
				if (!seen.add(key)) {
					duplicates++;
				}
			}
			return duplicates;
		}
	}

	private static boolean hasQvaluePepDifference(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return false;
			}
			String[] cols = header.split("\\t");
			Map<String, Integer> index = new HashMap<>();
			for (int i = 0; i < cols.length; i++) {
				index.put(cols[i], i);
			}
			int qIdx = pickFirst(index, "mokapot_qvalue", "q-value", "mokapot q-value");
			int pepIdx = pickFirst(index, "mokapot_posterior_error_prob", "posterior_error_prob");
			if (qIdx < 0 || pepIdx < 0) {
				throw new IllegalStateException("q-value/PEP columns not found in " + file);
			}
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				double q = Double.parseDouble(parts[qIdx]);
				double pep = Double.parseDouble(parts[pepIdx]);
				if (Math.abs(q - pep) > 1e-9) {
					return true;
				}
			}
			return false;
		}
	}

	private static int findColumnIndex(String[] columns, String name) {
		for (int i = 0; i < columns.length; i++) {
			if (columns[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	private static int pickFirst(Map<String, Integer> index, String... names) {
		for (String name : names) {
			Integer idx = index.get(name);
			if (idx != null) {
				return idx;
			}
		}
		return -1;
	}
}
