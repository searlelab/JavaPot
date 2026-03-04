package org.searlelab.javapot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CliParserTest {
	@Test
	void parsesDefaultsAndPositionalPin() {
		CliConfig cfg = CliParser.parse(new String[]{"/tmp/test.pin"});
		assertEquals(Path.of("/tmp/test.pin"), cfg.pinFile());
		assertEquals(Path.of("."), cfg.destDir());
		assertEquals(3, cfg.maxWorkers());
		assertEquals(OutputFormat.PERCOLATOR, cfg.outputFormat());
		assertEquals(0.01, cfg.trainFdr());
		assertEquals(0.01, cfg.testFdr());
		assertEquals(10, cfg.maxIter());
		assertEquals(1L, cfg.seed());
		assertEquals(3, cfg.folds());
		assertTrue(cfg.loadModels().isEmpty());
		assertFalse(cfg.mixmax());
	}

	@Test
	void rejectsUnknownOption() {
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"--nope", "/tmp/test.pin"}));
	}

	@Test
	void rejectsXmlInput() {
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"/tmp/test.pepXML"}));
	}

	@Test
	void rejectsMultipleInputs() {
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"a.pin", "b.pin"}));
	}

	@Test
	void parsesLoadModelsList() {
		CliConfig cfg = CliParser.parse(new String[]{"input.pin", "--load_models", "m1.bin", "m2.bin", "--folds", "2"});
		assertEquals(2, cfg.loadModels().size());
		assertEquals(2, cfg.folds());
		assertEquals(2, cfg.maxWorkers());
		assertEquals(OutputFormat.PERCOLATOR, cfg.outputFormat());
	}

	@Test
	void defaultsMaxWorkersToFoldsWhenOmitted() {
		CliConfig cfg = CliParser.parse(new String[]{"input.pin", "--folds", "7"});
		assertEquals(7, cfg.folds());
		assertEquals(7, cfg.maxWorkers());
		assertEquals(OutputFormat.PERCOLATOR, cfg.outputFormat());
	}

	@Test
	void explicitMaxWorkersOverridesFoldsDefaulting() {
		CliConfig cfg = CliParser.parse(new String[]{"input.pin", "--folds", "7", "--max_workers", "2"});
		assertEquals(7, cfg.folds());
		assertEquals(2, cfg.maxWorkers());
		assertEquals(OutputFormat.PERCOLATOR, cfg.outputFormat());
	}

	@Test
	void parsesAllSupportedOptions() {
		CliConfig cfg = CliParser.parse(new String[]{
			"input.pin",
			"-d", "/tmp/out",
			"-w", "3",
			"--train_fdr", "0.02",
			"--test_fdr", "0.03",
			"--max_iter", "20",
			"--seed", "1234",
			"--direction", "featA",
			"--subset_max_train", "777",
			"--output_format", "mokapot",
			"--mixmax",
			"--save_models",
			"--folds", "5"
		});
		assertEquals(Path.of("input.pin"), cfg.pinFile());
		assertEquals(Path.of("/tmp/out"), cfg.destDir());
		assertEquals(3, cfg.maxWorkers());
		assertEquals(0.02, cfg.trainFdr());
		assertEquals(0.03, cfg.testFdr());
		assertEquals(20, cfg.maxIter());
		assertEquals(1234L, cfg.seed());
		assertEquals("featA", cfg.direction());
		assertEquals(777, cfg.subsetMaxTrain());
		assertEquals(OutputFormat.MOKAPOT, cfg.outputFormat());
		assertTrue(cfg.saveModels());
		assertEquals(5, cfg.folds());
		assertTrue(cfg.mixmax());
	}

	@Test
	void parsesMixmaxAlias() {
		CliConfig cfg = CliParser.parse(new String[]{"input.pin", "--post-processing-mix-max"});
		assertTrue(cfg.mixmax());
	}

	@Test
	void rejectsMissingOrInvalidValues() {
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--max_iter"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--max_workers", "0"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--max_workers", "abc"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--folds", "1"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--max_iter", "0"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--max_iter", "xyz"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--subset_max_train", "0"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--train_fdr", "0"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--test_fdr", "1"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--seed", "x"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--train_fdr", "x"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--load_models"}));
		assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[]{"input.pin", "--output_format", "foo"}));
	}

	@Test
	void printsHelpText() {
		PrintStream original = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(baos));
			CliParser.printHelp();
		} finally {
			System.setOut(original);
		}
		String help = baos.toString();
		assertTrue(help.contains("Usage: javapot"));
		assertTrue(help.contains("--train_fdr"));
		assertTrue(help.contains("--output_format"));
		assertTrue(help.contains("--mixmax"));
	}

	@Test
	void privateCliEntrypointConstructorIsInvocableViaReflection() throws Exception {
		var ctor = JavaPotCli.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		Object instance = ctor.newInstance();
		assertEquals(JavaPotCli.class, instance.getClass());
	}
}
