package org.searlelab.javapot.io;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.searlelab.javapot.model.PercolatorFoldModel;

/**
 * ModelIO persists trained fold models and restores them for scoring-only runs.
 * It uses Java serialization with predictable per-fold filenames.
 */
public final class ModelIO {
	private ModelIO() {
	}

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
		return out;
	}
}
