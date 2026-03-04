package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;
import org.searlelab.javapot.io.ModelIO;

class LoadModelsOrderingIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void loadingModelTextFileProducesIdenticalOutputs() throws Exception {
		Path pin = writeSyntheticPin(tempDir.resolve("load_models.pin"));
		Path trainDir = tempDir.resolve("train");
		Files.createDirectories(trainDir);

		JavaPotCli.main(new String[]{
			pin.toString(),
			"--dest_dir", trainDir.toString(),
			"--output_format", "mokapot",
			"--max_workers", "1",
			"--write_psm_files",
			"--folds", "2",
			"--max_iter", "2",
			"--seed", "7",
			"--train_fdr", "0.5",
			"--test_fdr", "0.5",
			"--write_model_files"
		});

		Path modelFile = ModelIO.defaultModelPath(pin, trainDir);
		assertTrue(Files.exists(modelFile), "Expected text model file");

		Path loadedOut = tempDir.resolve("loaded");
		Files.createDirectories(loadedOut);

		JavaPotCli.main(new String[]{
			pin.toString(),
			"--dest_dir", loadedOut.toString(),
			"--output_format", "mokapot",
			"--max_workers", "1",
			"--write_psm_files",
			"--folds", "2",
			"--max_iter", "2",
			"--seed", "7",
			"--train_fdr", "0.5",
			"--test_fdr", "0.5",
			"--load_models", modelFile.toString()
		});

		assertEquals(-1L, Files.mismatch(trainDir.resolve("load_models.psms.tsv"), loadedOut.resolve("load_models.psms.tsv")));
		assertEquals(-1L, Files.mismatch(trainDir.resolve("load_models.peptides.tsv"), loadedOut.resolve("load_models.peptides.tsv")));
	}

	private static Path writeSyntheticPin(Path file) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins\n");
		for (int scan = 1; scan <= 12; scan++) {
			double expMass = 650.0 + scan;
			double base = 30.0 - scan;
			sb.append("t").append(scan).append("a\t1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(base + 1.0).append("\tPEP_").append(scan).append("_A\tPROT_T\n");
			sb.append("t").append(scan).append("b\t1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(base + 0.5).append("\tPEP_").append(scan).append("_B\tPROT_T\n");
			sb.append("d").append(scan).append("\t-1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(base - 2.0).append("\tDECOY_").append(scan).append("\tPROT_D\n");
		}
		Files.writeString(file, sb.toString());
		return file;
	}
}
