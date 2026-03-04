package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;

class OutputFormatIntegrationTest {
	private static final String PERCOLATOR_HEADER = "PSMId\tscore\tq-value\tposterior_error_prob\tpeptide\tproteinIds";

	@TempDir
	Path tempDir;

	@Test
	void defaultsToPercolatorHeaders() throws Exception {
		Path pinFile = resourcePin();
		Path outputDir = tempDir.resolve("percolator_default");
		Files.createDirectories(outputDir);

		JavaPotCli.main(new String[]{
			pinFile.toString(),
			"--dest_dir", outputDir.toString(),
			"--write_psm_files",
			"--max_iter", "2",
			"--seed", "1"
		});

		assertEquals(PERCOLATOR_HEADER, Files.readAllLines(outputDir.resolve("10k_psms_test.psms.tsv")).get(0));
		assertEquals(PERCOLATOR_HEADER, Files.readAllLines(outputDir.resolve("10k_psms_test.peptides.tsv")).get(0));
	}

	@Test
	void supportsMokapotHeaderToggle() throws Exception {
		Path pinFile = resourcePin();
		Path outputDir = tempDir.resolve("mokapot_toggle");
		Files.createDirectories(outputDir);

		JavaPotCli.main(new String[]{
			pinFile.toString(),
			"--dest_dir", outputDir.toString(),
			"--write_psm_files",
			"--max_iter", "2",
			"--seed", "1",
			"--output_format", "mokapot"
		});

		String psmHeader = Files.readAllLines(outputDir.resolve("10k_psms_test.psms.tsv")).get(0);
		String peptideHeader = Files.readAllLines(outputDir.resolve("10k_psms_test.peptides.tsv")).get(0);
		assertTrue(psmHeader.contains("mokapot_qvalue"));
		assertTrue(psmHeader.contains("mokapot_score"));
		assertTrue(peptideHeader.contains("mokapot_qvalue"));
	}

	private static Path resourcePin() throws URISyntaxException {
		return Path.of(Objects.requireNonNull(
			OutputFormatIntegrationTest.class.getResource("/data/10k_psms_test.pin"),
			"Resource not found: /data/10k_psms_test.pin"
		).toURI());
	}
}
