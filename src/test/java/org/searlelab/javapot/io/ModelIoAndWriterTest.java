package org.searlelab.javapot.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.model.LinearSvmModel;
import org.searlelab.javapot.model.PercolatorFoldModel;

class ModelIoAndWriterTest {
	@TempDir
	Path tmp;

	@Test
	void savesAndLoadsModelsRoundTrip() throws Exception {
		PercolatorFoldModel model = new PercolatorFoldModel(
			new String[]{"f1", "f2"},
			new double[]{10.0, -2.0},
			new double[]{2.0, 4.0},
			new LinearSvmModel(new double[]{1.0, -1.0}, 0.5, 1.0, 1.0),
			"f1",
			99,
			true,
			1
		);

		Path file = tmp.resolve("roundtrip.model.tsv");
		ModelIO.saveModels(List.of(model), file);
		assertTrue(Files.exists(file));
		List<String> lines = Files.readAllLines(file);
		assertEquals(4, lines.size());
		assertTrue(lines.get(0).startsWith("# javapot_meta\tfold=1"));
		assertEquals("f1\tf2\tm0", lines.get(1));

		List<PercolatorFoldModel> loaded = ModelIO.loadModels(file);
		assertEquals(1, loaded.size());
		PercolatorFoldModel restored = loaded.get(0);
		assertEquals(1, restored.fold());
		assertEquals("f1", restored.bestFeature());
		assertArrayEquals(new double[]{0.5}, restored.predict(new double[][]{{12.0, 2.0}}), 1e-12);
	}

	@Test
	void loadModelsRejectsInvalidTextShape() throws Exception {
		Path bad = tmp.resolve("bad.model.txt");
		Files.writeString(bad, "f1\tf2\tm0\n1\t2\t3\n");
		assertThrows(RuntimeException.class, () -> ModelIO.loadModels(bad));
	}

	@Test
	void saveModelsWrapsIoFailures() {
		PercolatorFoldModel model = new PercolatorFoldModel(
			new String[]{"f1"},
			new double[]{0.0},
			new double[]{1.0},
			new LinearSvmModel(new double[]{1.0}, 0.0, 1.0, 1.0),
			"f1",
			1,
			true,
			1
		);
		Path missingFile = tmp.resolve("does/not/exist/model.tsv");
		assertThrows(RuntimeException.class, () -> ModelIO.saveModels(List.of(model), missingFile));
	}

	@Test
	void loadModelsReadsSerialFoldBlocksFromSingleFile() throws Exception {
		PercolatorFoldModel foldTwo = new PercolatorFoldModel(
			new String[]{"f1"},
			new double[]{0.0},
			new double[]{1.0},
			new LinearSvmModel(new double[]{2.0}, 0.0, 1.0, 1.0),
			"f1",
			5,
			true,
			2
		);
		PercolatorFoldModel foldOne = new PercolatorFoldModel(
			new String[]{"f1"},
			new double[]{0.0},
			new double[]{1.0},
			new LinearSvmModel(new double[]{1.0}, 0.0, 1.0, 1.0),
			"f1",
			5,
			true,
			1
		);
		Path file = tmp.resolve("serial.model.tsv");
		ModelIO.saveModels(List.of(foldOne, foldTwo), file);

		List<PercolatorFoldModel> loaded = ModelIO.loadModels(file);
		assertEquals(2, loaded.size());
		assertEquals(1, loaded.get(0).fold());
		assertEquals(2, loaded.get(1).fold());
	}

	@Test
	void loadModelsSkipsPercolatorCommentPreamble() throws Exception {
		Path file = tmp.resolve("commented.model.txt");
		Files.writeString(
			file,
			"# This file contains the weights from each cross validation bin from percolator training\n" +
				"# First line is the feature names, followed by normalized weights, and the raw weights of bin 1\n" +
				"# This is repeated for the other bins\n" +
				"f1\tm0\n" +
				"1.0\t0.5\n" +
				"0.2\t-1.0\n"
		);
		List<PercolatorFoldModel> loaded = ModelIO.loadModels(file);
		assertEquals(1, loaded.size());
		assertArrayEquals(new double[]{0.0}, loaded.get(0).predict(new double[][]{{5.0}}), 1e-12);
	}

	@Test
	void tsvWriterWritesRowsAndWrapsFailures() throws Exception {
		Path ok = tmp.resolve("out.tsv");
		TsvWriter.write(ok, List.of("a", "b"), List.of(new String[]{"1", "2"}, new String[]{"3", "4"}));
		List<String> lines = Files.readAllLines(ok);
		assertEquals(List.of("a\tb", "1\t2", "3\t4"), lines);

		Path missingParent = tmp.resolve("missing/path/out.tsv");
		assertThrows(
			RuntimeException.class,
			() -> TsvWriter.write(missingParent, List.of("h"), List.<String[]>of(new String[]{"x"}))
		);
	}

	@Test
	void defaultModelPathDropsPinLikeExtensions() {
		assertEquals(
			Path.of("/tmp/out/sample.model.tsv"),
			ModelIO.defaultModelPath(Path.of("sample.pin"), Path.of("/tmp/out"))
		);
		assertEquals(
			Path.of("/tmp/out/sample.model.tsv"),
			ModelIO.defaultModelPath(Path.of("sample.tsv"), Path.of("/tmp/out"))
		);
		assertEquals(
			Path.of("/tmp/out/sample.model.tsv"),
			ModelIO.defaultModelPath(Path.of("sample.txt"), Path.of("/tmp/out"))
		);
		assertEquals(
			Path.of("/tmp/out/sample.raw.model.tsv"),
			ModelIO.defaultModelPath(Path.of("sample.raw"), Path.of("/tmp/out"))
		);
	}
}
