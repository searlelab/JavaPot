package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;

class PerformanceSmokeTest {
	private static final Path PIN_FILE = Path.of("/Users/searle.brian/Documents/projects/mokapot/data/10k_psms_test.pin");
	private static final long MAX_RUNTIME_MS = Long.getLong("javapot.smoke.max_runtime_ms", 30_000L);

	@TempDir
	Path tempDir;

	@Test
	void tenKPinCompletesWithinRuntimeBudget() throws Exception {
		Assumptions.assumeTrue(Files.exists(PIN_FILE), "PIN test file not available");

		Path outputDir = tempDir.resolve("perf");
		Files.createDirectories(outputDir);

		long start = System.nanoTime();
		JavaPotCli.main(new String[]{
			PIN_FILE.toString(),
			"--dest_dir", outputDir.toString(),
			"--max_workers", "1",
			"--seed", "1"
		});
		long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

		Path psmFile = outputDir.resolve("targets.psms.tsv");
		Path pepFile = outputDir.resolve("targets.peptides.tsv");
		assertTrue(Files.exists(psmFile), "PSM output missing");
		assertTrue(Files.exists(pepFile), "Peptide output missing");

		int psmAt = countAtThreshold(psmFile, 0.01);
		int pepAt = countAtThreshold(pepFile, 0.01);

		assertTrue(pepAt >= 280 && pepAt <= 330, "Unexpected peptide count at q<=0.01: " + pepAt);
		assertTrue(psmAt >= 440 && psmAt <= 530, "Unexpected PSM count at q<=0.01: " + psmAt);
		assertTrue(elapsedMs <= MAX_RUNTIME_MS, "Runtime budget exceeded: " + elapsedMs + " ms (limit=" + MAX_RUNTIME_MS + " ms)");

		System.out.println(
			"[PERF] JavaPot 10k PIN runtime(ms)=" + elapsedMs +
				", peptides@0.01=" + pepAt +
				", psms@0.01=" + psmAt
		);
	}

	private static int countAtThreshold(Path file, double threshold) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return 0;
			}
			String[] cols = header.split("\\t");
			int qIdx = -1;
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].equals("q-value") || cols[i].equals("mokapot_qvalue") || cols[i].equals("mokapot q-value")) {
					qIdx = i;
					break;
				}
			}
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
}
