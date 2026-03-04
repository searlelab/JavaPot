package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;

class MixMaxBenchmarkIntegrationTest {
	private static final Path FIXTURE = Path.of("src/test/resources/data/minmax_10k.pin");
	private static final double FDR_THRESHOLD = 0.01;

	@TempDir
	Path tempDir;

	@Test
	void mixmaxFindsMorePeptidesThanTdcAndMaintainsQvaluePepOrdering() throws Exception {
		assertTrue(Files.exists(FIXTURE), "Fixture missing: " + FIXTURE);

		Path tdcOut = runAnalysis(tempDir.resolve("tdc"), false);
		Path mixmaxOut = runAnalysis(tempDir.resolve("mixmax"), true);

		int tdcPeptidesAtFdr = countAtThreshold(tdcOut.resolve("targets.peptides.tsv"), FDR_THRESHOLD);
		int mixmaxPeptidesAtFdr = countAtThreshold(mixmaxOut.resolve("targets.peptides.tsv"), FDR_THRESHOLD);
		assertTrue(
			mixmaxPeptidesAtFdr > tdcPeptidesAtFdr,
			"Expected mix-max peptides > TDC peptides at q<=" + FDR_THRESHOLD +
				" but observed mix-max=" + mixmaxPeptidesAtFdr + ", tdc=" + tdcPeptidesAtFdr
		);

		assertTrue(allQvaluesAtMostPep(tdcOut.resolve("targets.psms.tsv")), "TDC PSM output violates q-value <= PEP");
		assertTrue(allQvaluesAtMostPep(tdcOut.resolve("targets.peptides.tsv")), "TDC peptide output violates q-value <= PEP");
		assertTrue(allQvaluesAtMostPep(mixmaxOut.resolve("targets.psms.tsv")), "Mix-max PSM output violates q-value <= PEP");
		assertTrue(allQvaluesAtMostPep(mixmaxOut.resolve("targets.peptides.tsv")), "Mix-max peptide output violates q-value <= PEP");
	}

	private static Path runAnalysis(Path outputDir, boolean mixmax) throws IOException {
		Files.createDirectories(outputDir);
		List<String> args = new ArrayList<>();
		args.add(FIXTURE.toString());
		args.add("--dest_dir");
		args.add(outputDir.toString());
		args.add("--max_workers");
		args.add("1");
		args.add("--folds");
		args.add("2");
		args.add("--max_iter");
		args.add("3");
		args.add("--seed");
		args.add("1");
		args.add("--train_fdr");
		args.add(Double.toString(FDR_THRESHOLD));
		args.add("--test_fdr");
		args.add(Double.toString(FDR_THRESHOLD));
		if (mixmax) {
			args.add("--mixmax");
		}
		JavaPotCli.main(args.toArray(String[]::new));
		return outputDir;
	}

	private static int countAtThreshold(Path file, double threshold) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return 0;
			}
			String[] cols = header.split("\\t");
			int qIdx = findColumnIndex(cols, "q-value", "mokapot_qvalue", "mokapot q-value");
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
				if (Double.parseDouble(parts[qIdx]) <= threshold) {
					count++;
				}
			}
			return count;
		}
	}

	private static boolean allQvaluesAtMostPep(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return true;
			}
			String[] cols = header.split("\\t");
			int qIdx = findColumnIndex(cols, "q-value", "mokapot_qvalue", "mokapot q-value");
			int pepIdx = findColumnIndex(cols, "posterior_error_prob", "mokapot_posterior_error_prob");
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
				if (!Double.isFinite(q) || !Double.isFinite(pep)) {
					return false;
				}
				if (q > pep + 1e-12) {
					return false;
				}
			}
			return true;
		}
	}

	private static int findColumnIndex(String[] cols, String... candidates) {
		for (String candidate : candidates) {
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].equals(candidate)) {
					return i;
				}
			}
		}
		return -1;
	}
}
