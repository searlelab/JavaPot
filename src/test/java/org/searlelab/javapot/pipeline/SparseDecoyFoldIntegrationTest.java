package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.CliConfig;
import org.searlelab.javapot.cli.OutputFormat;

class SparseDecoyFoldIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void runCompletesWhenSomeFoldsHaveNoDecoys() throws Exception {
		Path pin = tempDir.resolve("sparse_decoy.pin");
		writeSparseDecoyPin(pin);
		Path outDir = tempDir.resolve("out");
		Files.createDirectories(outDir);

		CliConfig config = new CliConfig(
			pin,
			outDir,
			1,
			OutputFormat.MOKAPOT,
			true,
			0.5,
			0.5,
			2,
			3L,
			null,
			null,
			false,
			true,
			false,
			null,
			null,
			null,
			null,
			List.of(),
			3,
			false
		);
		JavaPotRunner.run(config);

		Path psm = outDir.resolve("sparse_decoy.psms.tsv");
		Path pep = outDir.resolve("sparse_decoy.peptides.tsv");
		assertTrue(Files.exists(psm), "PSM output missing");
		assertTrue(Files.exists(pep), "Peptide output missing");
		assertTrue(hasFiniteQAndPep(psm));
		assertTrue(hasFiniteQAndPep(pep));
	}

	private static void writeSparseDecoyPin(Path file) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins\n");
		sb.append("t1\t1\t1\t500.1\t7.0\tPEP1\tP1\n");
		sb.append("t2\t1\t2\t500.2\t6.5\tPEP2\tP1\n");
		sb.append("t3\t1\t3\t500.3\t6.0\tPEP3\tP1\n");
		sb.append("t4\t1\t4\t500.4\t5.5\tPEP4\tP1\n");
		sb.append("t5\t1\t5\t500.5\t5.0\tPEP5\tP1\n");
		sb.append("d1\t-1\t6\t500.6\t1.0\tDECOY\tD1\n");
		Files.writeString(file, sb.toString());
	}

	private static boolean hasFiniteQAndPep(Path file) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return false;
			}
			String[] cols = header.split("\\t");
			int qIdx = -1;
			int pepIdx = -1;
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].equals("mokapot_qvalue") || cols[i].equals("q-value")) {
					qIdx = i;
				} else if (cols[i].equals("mokapot_posterior_error_prob") || cols[i].equals("posterior_error_prob")) {
					pepIdx = i;
				}
			}
			if (qIdx < 0 || pepIdx < 0) {
				return false;
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
			}
			return true;
		}
	}
}
