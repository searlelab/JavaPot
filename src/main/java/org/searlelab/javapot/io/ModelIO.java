package org.searlelab.javapot.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.searlelab.javapot.model.LinearSvmModel;
import org.searlelab.javapot.model.PercolatorFoldModel;

/**
 * ModelIO persists trained fold models and restores them for scoring-only runs.
 * It uses Percolator-compatible text blocks (header + normalized row + raw row) for each fold.
 */
public final class ModelIO {
	private static final String META_PREFIX = "# javapot_meta";

	private ModelIO() {
	}

	/**
	 * Resolves the default model output path as <pin_base>.model.tsv in the destination directory.
	 */
	public static Path defaultModelPath(Path pinFile, Path destDir) {
		return destDir.resolve(pinOutputBaseName(pinFile) + ".model.tsv");
	}

	/**
	 * Writes all fold models serially to a Percolator-style text model file.
	 */
	public static void saveModels(List<PercolatorFoldModel> models, Path modelFile) {
		try (BufferedWriter writer = Files.newBufferedWriter(modelFile)) {
			for (PercolatorFoldModel model : models) {
				writeFoldBlock(writer, model);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to save model: " + modelFile, e);
		}
	}

	/**
	 * Loads Percolator-style text model blocks from a single file.
	 */
	public static List<PercolatorFoldModel> loadModels(Path modelFile) {
		List<PercolatorFoldModel> out;
		try {
			out = parseModelBlocks(modelFile);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load model: " + modelFile, e);
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

	private static void writeFoldBlock(BufferedWriter writer, PercolatorFoldModel model) throws IOException {
		String[] featureNames = model.featureNames();
		double[] means = model.means();
		double[] scales = model.scales();
		double[] normWeights = model.svm().weights();
		if (featureNames.length != means.length || means.length != scales.length || scales.length != normWeights.length) {
			throw new RuntimeException("Invalid model dimensions while writing fold " + model.fold());
		}

		double[] normalizedRow = new double[featureNames.length + 1];
		System.arraycopy(normWeights, 0, normalizedRow, 0, normWeights.length);
		normalizedRow[featureNames.length] = model.svm().bias();

		double[] rawRow = new double[featureNames.length + 1];
		double biasShift = 0.0;
		for (int i = 0; i < featureNames.length; i++) {
			double scale = scales[i];
			if (!Double.isFinite(scale) || Math.abs(scale) < 1e-12) {
				scale = 1.0;
			}
			rawRow[i] = normWeights[i] / scale;
			biasShift += means[i] * rawRow[i];
		}
		rawRow[featureNames.length] = model.svm().bias() - biasShift;

		writer.write(
			META_PREFIX +
				"\tfold=" + model.fold() +
				"\tbestFeature=" + model.bestFeature() +
				"\tbestFeaturePass=" + model.bestFeaturePass() +
				"\tbestFeatureDescending=" + model.bestFeatureDescending()
		);
		writer.newLine();
		writer.write(String.join("\t", featureNames));
		writer.write("\tm0");
		writer.newLine();
		writer.write(joinDoubles(normalizedRow));
		writer.newLine();
		writer.write(joinDoubles(rawRow));
		writer.newLine();
	}

	private static List<PercolatorFoldModel> parseModelBlocks(Path path) throws IOException {
		List<String> rawLines = Files.readAllLines(path);
		if (rawLines.isEmpty()) {
			throw new RuntimeException("Model file has no model rows: " + path);
		}
		int nextFold = 1;
		List<PercolatorFoldModel> models = new ArrayList<>();
		FoldMetadata pendingMetadata = null;
		int cursor = 0;
		while (cursor < rawLines.size()) {
			String line = rawLines.get(cursor).trim();
			cursor++;
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("#")) {
				FoldMetadata parsed = parseMetadata(line, path);
				if (parsed != null) {
					pendingMetadata = parsed;
				}
				continue;
			}

			LineRead normalizedRead = nextDataLine(rawLines, path, cursor);
			LineRead rawRead = nextDataLine(rawLines, path, normalizedRead.nextIndex());
			cursor = rawRead.nextIndex();

			String[] header = splitHeader(line);
			if (header.length < 2) {
				throw new RuntimeException("Invalid model header in " + path + ": " + line);
			}
			int featureCount = header.length - 1;
			if (!"m0".equalsIgnoreCase(header[featureCount])) {
				throw new RuntimeException("Model header must end with m0 in " + path + ": " + line);
			}
			String[] featureNames = Arrays.copyOf(header, featureCount);
			parseNumericRow(normalizedRead.line(), featureCount + 1, path);
			double[] rawRow = parseNumericRow(rawRead.line(), featureCount + 1, path);

			double[] rawWeights = Arrays.copyOf(rawRow, featureCount);
			double rawBias = rawRow[featureCount];
			double[] means = new double[featureCount];
			double[] scales = new double[featureCount];
			Arrays.fill(scales, 1.0);
			LinearSvmModel svm = new LinearSvmModel(rawWeights, rawBias, 1.0, 1.0);
			int fold;
			if (pendingMetadata != null && pendingMetadata.fold() != null) {
				fold = pendingMetadata.fold();
				nextFold = Math.max(nextFold, fold + 1);
			} else {
				fold = nextFold++;
			}
			String bestFeature = featureCount > 0 ? featureNames[0] : "";
			int bestFeaturePass = Integer.MIN_VALUE;
			boolean bestFeatureDescending = true;
			if (pendingMetadata != null) {
				if (pendingMetadata.bestFeature() != null && !pendingMetadata.bestFeature().isEmpty()) {
					bestFeature = pendingMetadata.bestFeature();
				}
				if (pendingMetadata.bestFeaturePass() != null) {
					bestFeaturePass = pendingMetadata.bestFeaturePass();
				}
				if (pendingMetadata.bestFeatureDescending() != null) {
					bestFeatureDescending = pendingMetadata.bestFeatureDescending();
				}
			}
			pendingMetadata = null;

			models.add(
				new PercolatorFoldModel(
					featureNames,
					means,
					scales,
					svm,
					bestFeature,
					bestFeaturePass,
					bestFeatureDescending,
					fold
				)
			);
		}
		if (models.isEmpty()) {
			throw new RuntimeException("Model file has no model rows: " + path);
		}
		return models;
	}

	private static FoldMetadata parseMetadata(String line, Path path) {
		if (!line.startsWith(META_PREFIX)) {
			return null;
		}
		String tail = line.substring(META_PREFIX.length()).trim();
		if (tail.isEmpty()) {
			return new FoldMetadata(null, null, null, null);
		}
		String[] parts = tail.split("\t");
		Integer fold = null;
		String bestFeature = null;
		Integer bestFeaturePass = null;
		Boolean bestFeatureDescending = null;
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			int eq = part.indexOf('=');
			if (eq <= 0 || eq == part.length() - 1) {
				continue;
			}
			String key = part.substring(0, eq);
			String value = part.substring(eq + 1);
			switch (key) {
				case "fold" -> fold = parseInt(value, "fold", path, line);
				case "bestFeature" -> bestFeature = value;
				case "bestFeaturePass" -> bestFeaturePass = parseInt(value, "bestFeaturePass", path, line);
				case "bestFeatureDescending" -> bestFeatureDescending = Boolean.parseBoolean(value);
				default -> {
				}
			}
		}
		return new FoldMetadata(fold, bestFeature, bestFeaturePass, bestFeatureDescending);
	}

	private static LineRead nextDataLine(List<String> lines, Path path, int startIndex) {
		for (int i = startIndex; i < lines.size(); i++) {
			String trimmed = lines.get(i).trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			return new LineRead(trimmed, i + 1);
		}
		throw new RuntimeException("Incomplete model block in " + path + " (expected header, normalized row, and raw row)");
	}

	private static String[] splitHeader(String line) {
		String[] byTab = line.split("\t");
		if (byTab.length > 1) {
			return byTab;
		}
		return line.split("\\s+");
	}

	private static double[] parseNumericRow(String line, int expectedCount, Path path) {
		String[] tokens = line.split("\\s+");
		if (tokens.length != expectedCount) {
			throw new RuntimeException(
				"Model row has " + tokens.length + " values but expected " + expectedCount + " in " + path + ": " + line
			);
		}
		double[] out = new double[expectedCount];
		for (int i = 0; i < tokens.length; i++) {
			try {
				out[i] = Double.parseDouble(tokens[i]);
			} catch (NumberFormatException e) {
				throw new RuntimeException("Invalid numeric token '" + tokens[i] + "' in " + path + ": " + line, e);
			}
		}
		return out;
	}

	private static int parseInt(String value, String field, Path path, String line) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Invalid integer for " + field + " in " + path + ": " + line, e);
		}
	}

	private static String joinDoubles(double[] values) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append('\t');
			}
			sb.append(Double.toString(values[i]));
		}
		return sb.toString();
	}

	private static String pinOutputBaseName(Path pinFile) {
		String fileName = pinFile.getFileName().toString();
		String lower = fileName.toLowerCase();
		if (lower.endsWith(".pin")) {
			return fileName.substring(0, fileName.length() - 4);
		}
		if (lower.endsWith(".tsv")) {
			return fileName.substring(0, fileName.length() - 4);
		}
		if (lower.endsWith(".txt")) {
			return fileName.substring(0, fileName.length() - 4);
		}
		return fileName;
	}

	private record LineRead(String line, int nextIndex) {
	}

	private record FoldMetadata(Integer fold, String bestFeature, Integer bestFeaturePass, Boolean bestFeatureDescending) {
	}
}
