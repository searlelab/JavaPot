package org.searlelab.javapot.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import gnu.trove.set.hash.THashSet;
import org.searlelab.javapot.cli.CliConfig;
import org.searlelab.javapot.cli.OutputFormat;
import org.searlelab.javapot.data.ColumnGroups;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.io.ModelIO;
import org.searlelab.javapot.io.PinFileParser;
import org.searlelab.javapot.io.TsvWriter;
import org.searlelab.javapot.model.FoldSplitter;
import org.searlelab.javapot.model.FoldTrainingOutput;
import org.searlelab.javapot.model.PercolatorFoldModel;
import org.searlelab.javapot.model.PercolatorTrainer;
import org.searlelab.javapot.model.TrainingParams;
import org.searlelab.javapot.stats.ConfidenceMode;
import org.searlelab.javapot.stats.LabelUpdater;
import org.searlelab.javapot.stats.MixMaxQValues;
import org.searlelab.javapot.stats.PepEstimator;
import org.searlelab.javapot.stats.QValues;
import org.searlelab.javapot.stats.ScoreCalibrator;
import org.searlelab.javapot.util.DeterministicRandom;
import org.searlelab.javapot.util.StableIntSort;

/**
 * JavaPotRunner orchestrates the end-to-end Percolator workflow for one PIN input.
 * It drives parsing, fold training or model loading, scoring, confidence estimation, and TSV output writing.
 */
public final class JavaPotRunner {
	private static final String MOKAPOT_SCORE = "mokapot_score";
	private static final String MOKAPOT_QVALUE = "mokapot_qvalue";
	private static final String MOKAPOT_PEP = "mokapot_posterior_error_prob";
	private static final List<String> PERCOLATOR_HEADER = List.of(
		"PSMId",
		"score",
		"q-value",
		"posterior_error_prob",
		"peptide",
		"proteinIds"
	);

	private JavaPotRunner() {
	}

	/**
	 * Executes the full JavaPot pipeline for one parsed CLI configuration.
	 */
	public static void run(CliConfig config) {
		System.out.println("[INFO] JavaPot starting");
		PsmDataset dataset = PinFileParser.read(config.pinFile());
		ensureDestDir(config.destDir());
		printDatasetInfo(dataset);

		DeterministicRandom rng = new DeterministicRandom(config.seed());
		int[][] folds = FoldSplitter.split(dataset, config.folds(), rng, config.pinFile().getFileName().toString());
		List<PercolatorFoldModel> models;
		if (!config.loadModels().isEmpty()) {
			if (config.loadModels().size() != config.folds()) {
				throw new IllegalArgumentException(
					"--load_models count (" + config.loadModels().size() + ") must match --folds (" + config.folds() + ")"
				);
			}
			models = ModelIO.loadModels(config.loadModels());
			validateLoadedModelFolds(models, config.folds());
		} else {
			models = trainModels(dataset, folds, config, rng);
		}
		ConfidenceMode confidenceMode = config.mixmax() ? ConfidenceMode.MIXMAX : ConfidenceMode.TDC;

		double[] scores = predictScores(dataset, folds, models, config.testFdr());
		double[] finalScores = maybeFallbackToBestFeature(dataset, models, scores, config.testFdr(), confidenceMode, config.seed());

		OutputTables tables = assignConfidenceAndBuildOutputs(
			dataset,
			finalScores,
			config.testFdr(),
			config.outputFormat(),
			confidenceMode,
			config.seed()
		);

		Path psmPath = config.destDir().resolve("targets.psms.tsv");
		Path peptidePath = config.destDir().resolve("targets.peptides.tsv");
		TsvWriter.write(psmPath, tables.psmHeader(), tables.psmRows());
		TsvWriter.write(peptidePath, tables.peptideHeader(), tables.peptideRows());

		if (config.saveModels()) {
			ModelIO.saveModels(models, config.destDir());
		}

		System.out.println("[INFO] Found " + tables.peptidesAtThreshold() + " peptides with q<=" + config.testFdr());
		System.out.println("[INFO] Wrote " + psmPath + " and " + peptidePath);
	}

	private static void printDatasetInfo(PsmDataset dataset) {
		boolean[] targets = dataset.rawTargets();
		int targetCount = 0;
		for (boolean target : targets) {
			if (target) {
				targetCount++;
			}
		}
		System.out.println("[INFO] Found " + dataset.size() + " total PSMs");
		System.out.println("[INFO]   - " + targetCount + " target PSMs and " + (dataset.size() - targetCount) + " decoy PSMs detected.");
		System.out.println("[INFO] Using " + dataset.featureCount() + " features: " + String.join(",", dataset.featureNames()));
	}

	private static void ensureDestDir(Path destDir) {
		try {
			Files.createDirectories(destDir);
		} catch (Exception e) {
			throw new RuntimeException("Unable to create dest dir: " + destDir, e);
		}
	}

	private static List<PercolatorFoldModel> trainModels(
		PsmDataset dataset,
		int[][] folds,
		CliConfig config,
		DeterministicRandom rng
	) {
		System.out.println("[INFO] Splitting PSMs into " + config.folds() + " folds...");
		List<Callable<FoldTrainingOutput>> tasks = new ArrayList<>(folds.length);
		for (int fi = 0; fi < folds.length; fi++) {
			int fold = fi + 1;
			int[] trainIdx = complement(dataset.size(), folds[fi]);
			trainIdx = maybeSubsetTrain(trainIdx, config.subsetMaxTrain(), rng);
			long foldSeed = rng.nextLong();
			TrainingParams params = new TrainingParams(
				config.trainFdr(),
				config.maxIter(),
				config.direction(),
				foldSeed,
				config.mixmax() ? ConfidenceMode.MIXMAX : ConfidenceMode.TDC
			);
			final int[] trainCopy = Arrays.copyOf(trainIdx, trainIdx.length);
			tasks.add(() -> {
				System.out.println("[INFO] === Analyzing Fold " + fold + " ===");
				return PercolatorTrainer.trainFold(dataset, trainCopy, fold, params);
			});
		}

		ExecutorService pool = Executors.newFixedThreadPool(Math.min(config.maxWorkers(), tasks.size()));
		List<Future<FoldTrainingOutput>> futures = new ArrayList<>(tasks.size());
		for (Callable<FoldTrainingOutput> task : tasks) {
			futures.add(pool.submit(task));
		}
		pool.shutdown();

		List<FoldTrainingOutput> outputs = new ArrayList<>(futures.size());
		for (Future<FoldTrainingOutput> future : futures) {
			try {
				outputs.add(future.get());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Training interrupted", e);
			} catch (ExecutionException e) {
				throw new RuntimeException("Training failed", e.getCause());
			}
		}

		outputs.sort(Comparator.comparingInt(o -> o.model().fold()));
		List<PercolatorFoldModel> models = new ArrayList<>(outputs.size());
		for (FoldTrainingOutput out : outputs) {
			models.add(out.model());
		}
		return models;
	}

	private static int[] maybeSubsetTrain(int[] trainIdx, Integer subsetMaxTrain, DeterministicRandom rng) {
		if (subsetMaxTrain == null || trainIdx.length <= subsetMaxTrain) {
			return trainIdx;
		}
		return rng.choiceWithoutReplacement(trainIdx, subsetMaxTrain);
	}

	private static int[] complement(int n, int[] excluded) {
		boolean[] mask = new boolean[n];
		for (int idx : excluded) {
			mask[idx] = true;
		}
		int size = 0;
		for (boolean b : mask) {
			if (!b) {
				size++;
			}
		}
		int[] out = new int[size];
		int p = 0;
		for (int i = 0; i < n; i++) {
			if (!mask[i]) {
				out[p++] = i;
			}
		}
		return out;
	}

	private static double[] predictScores(PsmDataset dataset, int[][] folds, List<PercolatorFoldModel> models, double testFdr) {
		if (folds.length != models.size()) {
			throw new IllegalStateException("Number of folds does not match number of models");
		}
		double[] out = new double[dataset.size()];
		for (int fi = 0; fi < folds.length; fi++) {
			int[] testIdx = folds[fi];
			double[][] x = subsetRows(dataset.rawFeatures(), testIdx);
			boolean[] y = subsetTargets(dataset.rawTargets(), testIdx);
			double[] raw = models.get(fi).predict(x);
			double[] calibrated = ScoreCalibrator.calibrate(raw, y, testFdr);
			for (int i = 0; i < testIdx.length; i++) {
				out[testIdx[i]] = calibrated[i];
			}
		}
		return out;
	}

	private static void validateLoadedModelFolds(List<PercolatorFoldModel> models, int expectedFolds) {
		boolean[] seen = new boolean[expectedFolds + 1];
		for (PercolatorFoldModel model : models) {
			int fold = model.fold();
			if (fold < 1 || fold > expectedFolds) {
				throw new IllegalArgumentException(
					"Loaded model fold index out of range: " + fold + " (expected 1.." + expectedFolds + ")"
				);
			}
			if (seen[fold]) {
				throw new IllegalArgumentException("Loaded models contain duplicate fold index: " + fold);
			}
			seen[fold] = true;
		}
		for (int fold = 1; fold <= expectedFolds; fold++) {
			if (!seen[fold]) {
				throw new IllegalArgumentException("Missing loaded model for fold " + fold);
			}
		}
	}

	private static double[] maybeFallbackToBestFeature(
		PsmDataset dataset,
		List<PercolatorFoldModel> models,
		double[] scores,
		double testFdr,
		ConfidenceMode confidenceMode,
		long seed
	) {
		boolean skipDecoysPlusOne = confidenceMode == ConfidenceMode.MIXMAX;
		int[] pred = LabelUpdater.updateLabels(
			scores,
			dataset.rawTargets(),
			testFdr,
			true,
			confidenceMode,
			seed + 101L,
			skipDecoysPlusOne
		);
		int predTotal = countOnes(pred);

		PercolatorFoldModel best = null;
		for (PercolatorFoldModel model : models) {
			if (best == null || model.bestFeaturePass() > best.bestFeaturePass()) {
				best = model;
			}
		}
		if (best != null && best.bestFeaturePass() > predTotal) {
			System.out.println("[WARN] Learned model did not improve over best feature. Falling back to best feature scoring.");
			double[] fallback = dataset.featureColumn(best.bestFeature());
			if (!best.bestFeatureDescending()) {
				for (int i = 0; i < fallback.length; i++) {
					fallback[i] = -fallback[i];
				}
			}
			return fallback;
		}
		return scores;
	}

	private static OutputTables assignConfidenceAndBuildOutputs(
		PsmDataset dataset,
		double[] scores,
		double evalFdr,
		OutputFormat outputFormat,
		ConfidenceMode confidenceMode,
		long seed
	) {
		int[] psmBest = confidenceMode == ConfidenceMode.MIXMAX
			? sortedIndicesByScore(scores)
			: deduplicateBySpectrum(dataset, scores);
		int[] peptideBest = deduplicateByPeptide(dataset, psmBest, scores);

		double[] psmScores = gather(scores, psmBest);
		boolean[] psmTargets = gatherTargets(dataset.rawTargets(), psmBest);
		ConfidenceResult psmConfidence = estimateConfidence(psmScores, psmTargets, confidenceMode, seed + 131L);

		double[] pepScores = gather(scores, peptideBest);
		boolean[] pepTargets = gatherTargets(dataset.rawTargets(), peptideBest);
		ConfidenceResult peptideConfidence = estimateConfidence(pepScores, pepTargets, confidenceMode, seed + 197L);

		List<String> psmHeader;
		List<String[]> psmRows;
		List<String> peptideHeader;
		List<String[]> peptideRows;
		if (outputFormat == OutputFormat.MOKAPOT) {
			psmHeader = mokapotHeader(dataset.columnGroups());
			psmRows = buildMokapotRows(
				dataset,
				psmBest,
				psmScores,
				psmConfidence.qValues(),
				psmConfidence.pepValues(),
				psmHeader
			);
			peptideHeader = psmHeader;
			peptideRows = buildMokapotRows(
				dataset,
				peptideBest,
				pepScores,
				peptideConfidence.qValues(),
				peptideConfidence.pepValues(),
				peptideHeader
			);
		} else {
			psmHeader = PERCOLATOR_HEADER;
			psmRows = buildPercolatorRows(
				dataset,
				psmBest,
				psmScores,
				psmConfidence.qValues(),
				psmConfidence.pepValues()
			);
			peptideHeader = PERCOLATOR_HEADER;
			peptideRows = buildPercolatorRows(
				dataset,
				peptideBest,
				pepScores,
				peptideConfidence.qValues(),
				peptideConfidence.pepValues()
			);
		}
		int peptidesAtThreshold = countTargetsAtThreshold(pepTargets, peptideConfidence.qValues(), evalFdr);

		return new OutputTables(psmHeader, psmRows, peptideHeader, peptideRows, peptidesAtThreshold);
	}

	private static ConfidenceResult estimateConfidence(
		double[] scores,
		boolean[] targets,
		ConfidenceMode confidenceMode,
		long seed
	) {
		double[] qValues;
		double[] pepValues;
		if (confidenceMode == ConfidenceMode.MIXMAX) {
			qValues = MixMaxQValues.compute(scores, targets, true, seed, false, 0.5).qValues();
			pepValues = PepEstimator.tdcToPep(scores, targets).pepValues();
		} else {
			qValues = QValues.tdc(scores, targets, true);
			pepValues = PepEstimator.tdcQvalsToPep(scores, targets, qValues).pepValues();
		}
		return new ConfidenceResult(qValues, pepValues);
	}

	private static int countTargetsAtThreshold(boolean[] targets, double[] qvals, double evalFdr) {
		int out = 0;
		for (int i = 0; i < qvals.length; i++) {
			if (targets[i] && qvals[i] <= evalFdr) {
				out++;
			}
		}
		return out;
	}

	private static List<String> mokapotHeader(ColumnGroups columns) {
		List<String> out = new ArrayList<>(columns.spectrumColumns().size() + 8);
		if (columns.optionalColumns().id() != null) {
			out.add(columns.optionalColumns().id());
		}
		out.addAll(columns.spectrumColumns());
		out.add(columns.peptideColumn());
		out.add(MOKAPOT_SCORE);
		out.add(MOKAPOT_QVALUE);
		out.add(MOKAPOT_PEP);
		if (columns.optionalColumns().protein() != null) {
			out.add(columns.optionalColumns().protein());
		}
		return new ArrayList<>(new LinkedHashSet<>(out));
	}

	private static List<String[]> buildMokapotRows(
		PsmDataset dataset,
		int[] keptIdx,
		double[] scores,
		double[] qvals,
		double[] peps,
		List<String> header
	) {
		List<String[]> out = new ArrayList<>(keptIdx.length);
		for (int i = 0; i < keptIdx.length; i++) {
			int rowIdx = keptIdx[i];
			if (!dataset.targetAt(rowIdx)) {
				continue;
			}
			String[] row = new String[header.size()];
			for (int c = 0; c < header.size(); c++) {
				String col = header.get(c);
				if (col.equals(MOKAPOT_SCORE)) {
					row[c] = Double.toString(scores[i]);
				} else if (col.equals(MOKAPOT_QVALUE)) {
					row[c] = Double.toString(qvals[i]);
				} else if (col.equals(MOKAPOT_PEP)) {
					row[c] = Double.toString(peps[i]);
				} else {
					row[c] = dataset.valueAt(rowIdx, col);
				}
			}
			out.add(row);
		}
		return out;
	}

	private static List<String[]> buildPercolatorRows(
		PsmDataset dataset,
		int[] keptIdx,
		double[] scores,
		double[] qvals,
		double[] peps
	) {
		List<String[]> out = new ArrayList<>(keptIdx.length);
		for (int i = 0; i < keptIdx.length; i++) {
			int rowIdx = keptIdx[i];
			if (!dataset.targetAt(rowIdx)) {
				continue;
			}
			String[] row = new String[PERCOLATOR_HEADER.size()];
			row[0] = resolvePercolatorPsmId(dataset, rowIdx);
			row[1] = Double.toString(scores[i]);
			row[2] = Double.toString(qvals[i]);
			row[3] = Double.toString(peps[i]);
			row[4] = dataset.peptideAt(rowIdx);
			row[5] = resolvePercolatorProteinIds(dataset, rowIdx);
			out.add(row);
		}
		return out;
	}

	private static String resolvePercolatorPsmId(PsmDataset dataset, int rowIdx) {
		String idColumn = dataset.columnGroups().optionalColumns().id();
		if (idColumn != null) {
			String value = dataset.valueAt(rowIdx, idColumn);
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		String[] spectrumValues = dataset.spectrumValuesAt(rowIdx);
		if (spectrumValues.length > 0) {
			return String.join(":", spectrumValues);
		}
		return Integer.toString(rowIdx);
	}

	private static String resolvePercolatorProteinIds(PsmDataset dataset, int rowIdx) {
		String proteinColumn = dataset.columnGroups().optionalColumns().protein();
		if (proteinColumn == null) {
			return "";
		}
		String proteins = dataset.valueAt(rowIdx, proteinColumn);
		return proteins == null ? "" : proteins;
	}

	private static int[] deduplicateBySpectrum(PsmDataset dataset, double[] scores) {
		int[] idx = sortedIndicesByScore(scores);
		Set<String> seen = new THashSet<>(idx.length);
		int[] keep = new int[idx.length];
		int count = 0;
		for (int row : idx) {
			String key = String.join("\u0001", dataset.spectrumValuesAt(row));
			if (seen.add(key)) {
				keep[count++] = row;
			}
		}
		return Arrays.copyOf(keep, count);
	}

	private static int[] deduplicateByPeptide(PsmDataset dataset, int[] psmBest, double[] scores) {
		int[] orderedPos = StableIntSort.sortIndices(psmBest.length, (a, b) -> {
			int rowA = psmBest[a];
			int rowB = psmBest[b];
			int cmp = Double.compare(scores[rowB], scores[rowA]);
			if (cmp != 0) {
				return cmp;
			}
			return Integer.compare(rowA, rowB);
		});
		Set<String> seen = new THashSet<>(orderedPos.length);
		int[] keep = new int[orderedPos.length];
		int count = 0;
		for (int pos : orderedPos) {
			int row = psmBest[pos];
			String key = dataset.peptideAt(row) + "\u0001" + (dataset.targetAt(row) ? "T" : "D");
			if (seen.add(key)) {
				keep[count++] = row;
			}
		}
		return Arrays.copyOf(keep, count);
	}

	private static int[] sortedIndicesByScore(double[] scores) {
		return StableIntSort.sortIndices(scores.length, (a, b) -> {
			int cmp = Double.compare(scores[b], scores[a]);
			if (cmp != 0) {
				return cmp;
			}
			return Integer.compare(a, b);
		});
	}

	private static double[] gather(double[] values, int[] idx) {
		double[] out = new double[idx.length];
		for (int i = 0; i < idx.length; i++) {
			out[i] = values[idx[i]];
		}
		return out;
	}

	private static boolean[] gatherTargets(boolean[] values, int[] idx) {
		boolean[] out = new boolean[idx.length];
		for (int i = 0; i < idx.length; i++) {
			out[i] = values[idx[i]];
		}
		return out;
	}

	private static double[][] subsetRows(double[][] x, int[] idx) {
		double[][] out = new double[idx.length][x[0].length];
		for (int i = 0; i < idx.length; i++) {
			System.arraycopy(x[idx[i]], 0, out[i], 0, x[idx[i]].length);
		}
		return out;
	}

	private static boolean[] subsetTargets(boolean[] y, int[] idx) {
		boolean[] out = new boolean[idx.length];
		for (int i = 0; i < idx.length; i++) {
			out[i] = y[idx[i]];
		}
		return out;
	}

	private static int countOnes(int[] labels) {
		int c = 0;
		for (int v : labels) {
			if (v == 1) {
				c++;
			}
		}
		return c;
	}

	/**
	 * OutputTables carries fully materialized PSM and peptide report tables for file writing.
	 */
	private record OutputTables(
		List<String> psmHeader,
		List<String[]> psmRows,
		List<String> peptideHeader,
		List<String[]> peptideRows,
		int peptidesAtThreshold
	) {
	}

	private record ConfidenceResult(double[] qValues, double[] pepValues) {
	}
}
