package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;

class OutputRoutingIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void defaultsWriteOnlyPeptideFileNextToInput() throws Exception {
		Path pin = writePin(tempDir.resolve("routing_default.pin"));
		JavaPotCli.main(new String[]{
			pin.toString(),
			"--max_workers", "1",
			"--folds", "2",
			"--max_iter", "2",
			"--train_fdr", "0.5",
			"--test_fdr", "0.5",
			"--seed", "7",
			"--quiet"
		});

		Path pep = tempDir.resolve("routing_default.peptides.tsv");
		Path psm = tempDir.resolve("routing_default.psms.tsv");
		assertTrue(Files.exists(pep), "Expected peptide output next to input file");
		assertFalse(Files.exists(psm), "PSM output should not be written unless --write_psm_files is set");
	}

	@Test
	void writeFlagsControlTargetAndDecoyFileFamilies() throws Exception {
		Path pin = writePin(tempDir.resolve("routing_flags.pin"));
		Path out = tempDir.resolve("out");
		Files.createDirectories(out);

		JavaPotCli.main(new String[]{
			pin.toString(),
			"--dest_dir", out.toString(),
			"--write_psm_files",
			"--write_decoy_files",
			"--max_workers", "1",
			"--folds", "2",
			"--max_iter", "2",
			"--train_fdr", "0.5",
			"--test_fdr", "0.5",
			"--seed", "7",
			"--quiet"
		});

		Path targetPep = out.resolve("routing_flags.peptides.tsv");
		Path targetPsm = out.resolve("routing_flags.psms.tsv");
		Path decoyPep = out.resolve("routing_flags.decoy_peptides.tsv");
		Path decoyPsm = out.resolve("routing_flags.decoy_psms.tsv");
		assertTrue(Files.exists(targetPep));
		assertTrue(Files.exists(targetPsm));
		assertTrue(Files.exists(decoyPep));
		assertTrue(Files.exists(decoyPsm));
		assertTrue(countDataRows(decoyPep) > 0, "Decoy peptide output should include rows");
		assertTrue(countDataRows(decoyPsm) > 0, "Decoy PSM output should include rows");
	}

	@Test
	void explicitPercolatorPathsOverrideDestDirAndWriteFlags() throws Exception {
		Path pin = writePin(tempDir.resolve("routing_override.pin"));
		Path dest = tempDir.resolve("dest");
		Files.createDirectories(dest);

		Path targetPep = tempDir.resolve("forced/target.peptides.tsv");
		Path decoyPep = tempDir.resolve("forced/decoy.peptides.tsv");
		Path targetPsm = tempDir.resolve("forced/target.psms.tsv");
		Path decoyPsm = tempDir.resolve("forced/decoy.psms.tsv");

		JavaPotCli.main(new String[]{
			pin.toString(),
			"--dest_dir", dest.toString(),
			"--results-peptides", targetPep.toString(),
			"--decoy-results-peptides", decoyPep.toString(),
			"--results-psms", targetPsm.toString(),
			"--decoy-results-psms", decoyPsm.toString(),
			"--max_workers", "1",
			"--folds", "2",
			"--max_iter", "2",
			"--train_fdr", "0.5",
			"--test_fdr", "0.5",
			"--seed", "7",
			"--quiet"
		});

		assertTrue(Files.exists(targetPep));
		assertTrue(Files.exists(decoyPep));
		assertTrue(Files.exists(targetPsm));
		assertTrue(Files.exists(decoyPsm));
		assertFalse(Files.exists(dest.resolve("routing_override.peptides.tsv")));
		assertFalse(Files.exists(dest.resolve("routing_override.psms.tsv")));
		assertFalse(Files.exists(dest.resolve("routing_override.decoy_peptides.tsv")));
		assertFalse(Files.exists(dest.resolve("routing_override.decoy_psms.tsv")));
	}

	private static Path writePin(Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins\n");
		for (int scan = 1; scan <= 20; scan++) {
			double expMass = 550.0 + scan;
			double targetScore = 40.0 - scan;
			sb.append("t").append(scan).append("\t1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(targetScore).append("\tPEP_").append(scan).append("\tPROT_T\n");
		}
		for (int scan = 1; scan <= 10; scan++) {
			double expMass = 550.0 + scan;
			double decoyScore = scan % 2 == 0 ? 45.0 - scan : 5.0 - scan;
			sb.append("d").append(scan).append("\t-1\t").append(scan).append('\t').append(expMass).append('\t')
				.append(decoyScore).append("\tDECOY_").append(scan).append("\tPROT_D\n");
		}
		Files.writeString(path, sb.toString());
		return path;
	}

	private static int countDataRows(Path path) throws IOException {
		return Math.max(0, Files.readAllLines(path).size() - 1);
	}
}
