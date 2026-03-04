package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.cli.OutputFormat;

class JavaPotApiTest {
	@TempDir
	Path tempDir;

	@Test
	void convenienceConstructorAppliesDefaults() {
		Path pinFile = tempDir.resolve("input.pin");
		Path resultsPeptides = tempDir.resolve("out").resolve("target.tsv");
		Path decoyResultsPeptides = tempDir.resolve("out").resolve("decoy.tsv");
		Path saveModelFile = tempDir.resolve("out").resolve("saved.model.tsv");
		Path loadModelFile = tempDir.resolve("in").resolve("load.model.tsv");
		JavaPotOptions options = new JavaPotOptions(
			pinFile,
			0.02,
			0.03,
			resultsPeptides,
			decoyResultsPeptides,
			saveModelFile,
			loadModelFile,
			true
		);

		assertTrue(options.pinFile().equals(pinFile));
		assertTrue(options.destDir().equals(pinFile.toAbsolutePath().normalize().getParent()));
		assertTrue(options.maxWorkers() == JavaPotOptions.DEFAULT_FOLDS);
		assertTrue(options.outputFormat() == OutputFormat.PERCOLATOR);
		assertFalse(options.quiet());
		assertTrue(options.trainFdr() == 0.02);
		assertTrue(options.testFdr() == 0.03);
		assertTrue(options.maxIter() == JavaPotOptions.DEFAULT_MAX_ITER);
		assertTrue(options.seed() == JavaPotOptions.DEFAULT_SEED);
		assertTrue(options.folds() == JavaPotOptions.DEFAULT_FOLDS);
		assertTrue(options.saveModelFile().equals(saveModelFile));
		assertFalse(options.writePsmFiles());
		assertFalse(options.writeDecoyFiles());
		assertTrue(options.resultsPeptides().equals(resultsPeptides));
		assertTrue(options.decoyResultsPeptides().equals(decoyResultsPeptides));
		assertTrue(options.resultsPsms() == null);
		assertTrue(options.decoyResultsPsms() == null);
		assertTrue(options.loadModelFile().equals(loadModelFile));
		assertTrue(options.mixmax());
	}

	@Test
	void runReturnsProgrammaticResultsAndMixmaxPi0() throws Exception {
		Path pinFile = writeSyntheticSeparateSearchPin(tempDir.resolve("synthetic_mixmax.pin"));
		Path targetOut = tempDir.resolve("programmatic_targets.tsv");
		Path decoyOut = tempDir.resolve("programmatic_decoys.tsv");
		JavaPotOptions options = new JavaPotOptions(
			pinFile,
			0.5,
			0.5,
			targetOut,
			decoyOut,
			true
		);

		JavaPotRunResult result = JavaPotApi.run(options);
		assertNotNull(result);
		assertNotNull(result.peptides());
		assertNotNull(result.psms());
		assertFalse(result.peptides().isEmpty());
		assertFalse(result.psms().isEmpty());
		assertNotNull(result.psmPi0());
		assertNotNull(result.peptidePi0());
		assertInUnitInterval(result.psmPi0());
		assertInUnitInterval(result.peptidePi0());
		assertTrue(result.writtenFiles().contains(targetOut));
		assertTrue(result.writtenFiles().contains(decoyOut));
		assertTrue(Files.exists(targetOut));
		assertTrue(Files.exists(decoyOut));

		assertAnyDecoy(result.peptides());
		assertAnyTarget(result.peptides());
		assertFiniteConfidence(result.peptides());
		assertFiniteConfidence(result.psms());
	}

	private static void assertAnyDecoy(Iterable<JavaPotPeptide> rows) {
		for (JavaPotPeptide row : rows) {
			if (row.isDecoy()) {
				return;
			}
		}
		throw new AssertionError("Expected at least one decoy row");
	}

	private static void assertAnyTarget(Iterable<JavaPotPeptide> rows) {
		for (JavaPotPeptide row : rows) {
			if (!row.isDecoy()) {
				return;
			}
		}
		throw new AssertionError("Expected at least one target row");
	}

	private static void assertFiniteConfidence(Iterable<JavaPotPeptide> rows) {
		for (JavaPotPeptide row : rows) {
			assertTrue(Double.isFinite(row.score()));
			assertTrue(Double.isFinite(row.qValue()));
			assertTrue(Double.isFinite(row.pep()));
			assertInUnitInterval(row.qValue());
			assertInUnitInterval(row.pep());
		}
	}

	private static void assertInUnitInterval(double value) {
		assertTrue(value >= 0.0 && value <= 1.0, "Expected value in [0,1], got " + value);
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
}
