package org.searlelab.javapot.io;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.searlelab.javapot.model.PercolatorFoldModel;

/**
 * ModelIO persists trained fold models and restores them for scoring-only runs.
 * It uses Java serialization with predictable per-fold filenames.
 */
public final class ModelIO {
	private ModelIO() {
	}

	/**
	 * Serializes fold models to deterministic per-fold filenames in the destination directory.
	 */
	public static void saveModels(List<PercolatorFoldModel> models, Path destDir) {
		for (int i = 0; i < models.size(); i++) {
			Path out = destDir.resolve("javapot.model_fold-" + (i + 1) + ".bin");
			try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(out))) {
				oos.writeObject(models.get(i));
			} catch (IOException e) {
				throw new RuntimeException("Failed to save model: " + out, e);
			}
		}
	}

	/**
	 * Loads serialized fold models in caller-provided order.
	 */
	public static List<PercolatorFoldModel> loadModels(List<Path> paths) {
		List<PercolatorFoldModel> out = new ArrayList<>();
		for (Path path : paths) {
			try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(path))) {
				Object obj = ois.readObject();
				if (!(obj instanceof PercolatorFoldModel model)) {
					throw new RuntimeException("Model file is not a PercolatorFoldModel: " + path);
				}
				out.add(model);
			} catch (IOException | ClassNotFoundException e) {
				throw new RuntimeException("Failed to load model: " + path, e);
			}
		}
		out.sort(Comparator.comparingInt(PercolatorFoldModel::fold));
		Set<Integer> seen = new HashSet<>(out.size());
		for (PercolatorFoldModel model : out) {
			if (model.fold() < 1) {
				throw new RuntimeException("Loaded model has invalid fold index (<1): " + model.fold());
			}
			if (!seen.add(model.fold())) {
				throw new RuntimeException("Loaded models contain duplicate fold index: " + model.fold());
			}
		}
		return out;
	}
}
