package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.cli.OutputFormat;

class FoldFailureRecoveryIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void runRefoldsOnceThenForcesZeroDetectionsWhenFoldStillCannotTrain() throws Exception {
		Path pin = tempDir.resolve("single_target_sparse.pin");
		writeSingleTargetPin(pin);
		Path outDir = tempDir.resolve("out");
		Files.createDirectories(outDir);
		Path modelPath = outDir.resolve("single_target_sparse.model.tsv");

		JavaPotOptions config = new JavaPotOptions(
			pin,
			outDir,
			1,
			OutputFormat.PERCOLATOR,
			true,
			0.01,
			0.01,
			3,
			17L,
			null,
			null,
			modelPath,
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

		Path psm = outDir.resolve("single_target_sparse.psms.tsv");
		Path pep = outDir.resolve("single_target_sparse.peptides.tsv");
		assertTrue(Files.exists(psm), "PSM output missing");
		assertTrue(Files.exists(pep), "Peptide output missing");
		assertFalse(Files.exists(modelPath), "Model output should be skipped when training cannot recover");
		assertTrue(hasFiniteScores(result.psms()), "Expected finite API PSM scores");
		assertTrue(hasFiniteScores(result.peptides()), "Expected finite API peptide scores");
		assertAllConfidenceForcedToOne(result.psms());
		assertAllConfidenceForcedToOne(result.peptides());
	}

	private static void writeSingleTargetPin(Path file) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins\n");
		sb.append("t1\t1\t1\t500.01\t10.0\tPEPTIDE_TARGET\tP1\n");
		sb.append("d1\t-1\t2\t500.02\t0.0\tDECOY1\tD1\n");
		sb.append("d2\t-1\t3\t500.03\t0.0\tDECOY2\tD2\n");
		sb.append("d3\t-1\t4\t500.04\t0.0\tDECOY3\tD3\n");
		sb.append("d4\t-1\t5\t500.05\t0.0\tDECOY4\tD4\n");
		sb.append("d5\t-1\t6\t500.06\t0.0\tDECOY5\tD5\n");
		sb.append("d6\t-1\t7\t500.07\t0.0\tDECOY6\tD6\n");
		Files.writeString(file, sb.toString());
	}

	private static boolean hasFiniteScores(Iterable<JavaPotPeptide> rows) {
		for (JavaPotPeptide row : rows) {
			if (!Double.isFinite(row.score())) {
				return false;
			}
		}
		return true;
	}

	private static void assertAllConfidenceForcedToOne(Iterable<JavaPotPeptide> rows) {
		for (JavaPotPeptide row : rows) {
			assertEquals(1.0, row.qValue(), 1e-12);
			assertEquals(1.0, row.pep(), 1e-12);
		}
	}
}
