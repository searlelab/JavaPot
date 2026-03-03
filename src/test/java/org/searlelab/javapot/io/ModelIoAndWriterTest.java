package org.searlelab.javapot.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ObjectOutputStream;
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
	void savesAndLoadsModelsRoundTrip() {
		PercolatorFoldModel model = new PercolatorFoldModel(
			new String[]{"f1", "f2"},
			new double[]{0.0, 0.0},
			new double[]{1.0, 1.0},
			new LinearSvmModel(new double[]{1.0, -1.0}, 0.5, 1.0, 1.0),
			"f1",
			99,
			true,
			1
		);

		ModelIO.saveModels(List.of(model), tmp);
		Path file = tmp.resolve("javapot.model_fold-1.bin");
		assertTrue(Files.exists(file));

		List<PercolatorFoldModel> loaded = ModelIO.loadModels(List.of(file));
		assertEquals(1, loaded.size());
		PercolatorFoldModel restored = loaded.get(0);
		assertEquals(1, restored.fold());
		assertEquals("f1", restored.bestFeature());
		assertArrayEquals(new double[]{2.5}, restored.predict(new double[][]{{3.0, 1.0}}), 1e-12);
	}

	@Test
	void loadModelsRejectsUnexpectedSerializedType() throws Exception {
		Path bad = tmp.resolve("bad.bin");
		try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(bad))) {
			oos.writeObject("not a model");
		}
		assertThrows(RuntimeException.class, () -> ModelIO.loadModels(List.of(bad)));
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
		Path missingDir = tmp.resolve("does/not/exist");
		assertThrows(RuntimeException.class, () -> ModelIO.saveModels(List.of(model), missingDir));
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
}
