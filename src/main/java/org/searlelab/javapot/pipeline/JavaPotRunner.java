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
import org.searlelab.javapot.cli.JavaPotOptions;
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
	private static final long REFOLD_SEED_STRIDE = 10_007L;
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
	public static void run(JavaPotOptions config) {
		runForResult(config);
	}

	/**
	 * Executes the full JavaPot pipeline and returns programmatic peptide/PSM confidence outputs.
	 */
	public static JavaPotRunResult runForResult(JavaPotOptions config) {
		log(config, "JavaPot starting");
		PsmDataset dataset = PinFileParser.read(config.pinFile());
		printDatasetInfo(dataset, config);
		OutputPlan outputPlan = buildOutputPlan(config);

		ConfidenceMode confidenceMode = config.mixmax() ? ConfidenceMode.MIXMAX : ConfidenceMode.TDC;
		List<PercolatorFoldModel> models = new ArrayList<>();
		boolean forceNoDetections = false;
		double[] finalScores;
		if (config.loadModelFile() != null) {
			DeterministicRandom rng = new DeterministicRandom(config.seed());
			int[][] folds = FoldSplitter.split(dataset, config.folds(), rng, config.pinFile().getFileName().toString());
			models = ModelIO.loadModels(config.loadModelFile());
			validateLoadedModelFolds(models, config.folds());
			double[] scores = predictScores(dataset, folds, models, config.testFdr());
			finalScores = maybeFallbackToBestFeature(
				dataset,
				models,
				scores,
				config.testFdr(),
				confidenceMode,
				config.seed(),
				config.quiet()
			);
		} else {
			FeatureStartChoice startChoice = chooseStartFeature(
				dataset,
				config.direction(),
				config.trainFdr(),
				confidenceMode,
				config.seed()
			);
			if (startChoice.passCount() <= 0) {
				forceNoDetections = true;
				log(
					config,
					"No target PSMs found below train_fdr=" + config.trainFdr() +
						"; skipping model training and forcing q-value/PEP to 1.0."
				);
				finalScores = scoreByFeatureDirection(dataset, startChoice.featureName(), startChoice.descending());
			} else {
				DeterministicRandom rng = new DeterministicRandom(config.seed());
				int[][] folds = FoldSplitter.split(dataset, config.folds(), rng, config.pinFile().getFileName().toString());
				try {
					models = trainModels(dataset, folds, config, rng);
					double[] scores = predictScores(dataset, folds, models, config.testFdr());
					finalScores = maybeFallbackToBestFeature(
						dataset,
						models,
						scores,
						config.testFdr(),
						confidenceMode,
						config.seed(),
						config.quiet()
					);
				} catch (RuntimeException e) {
					TrainingRecovery recovery = recoverFromFoldTrainingFailure(
						dataset,
						config,
						confidenceMode,
						startChoice,
						e
					);
					models = recovery.models();
					forceNoDetections = recovery.forceNoDetections();
					finalScores = recovery.scores();
				}
			}
		}

		OutputTables tables = assignConfidenceAndBuildOutputs(
			dataset,
			finalScores,
			config.testFdr(),
			config.outputFormat(),
			confidenceMode,
			config.seed(),
			forceNoDetections
		);

		List<Path> writtenPaths = writeOutputTables(tables, outputPlan);

		if (config.saveModelFile() != null) {
			if (models.isEmpty()) {
				log(config, "Skipping model write because no models were trained.");
			} else {
				ensureParentDirectory(config.saveModelFile());
				ModelIO.saveModels(models, config.saveModelFile());
			}
		}

		log(config, "Found " + tables.peptidesAtThreshold() + " peptides with q<=" + config.testFdr());
		if (!writtenPaths.isEmpty()) {
			log(config, "Wrote " + joinPaths(writtenPaths));
		}
		return new JavaPotRunResult(
			tables.peptideResults(),
			tables.psmResults(),
			tables.psmPi0(),
			tables.peptidePi0(),
			new ArrayList<>(writtenPaths)
		);
	}

	private static void printDatasetInfo(PsmDataset dataset, JavaPotOptions config) {
		boolean[] targets = dataset.rawTargets();
		int targetCount = 0;
		for (boolean target : targets) {
			if (target) {
				targetCount++;
			}
		}
		log(config, "Found " + dataset.size() + " total PSMs");
		log(config, "  - " + targetCount + " target PSMs and " + (dataset.size() - targetCount) + " decoy PSMs detected.");
		log(config, "Using " + dataset.featureCount() + " features: " + String.join(",", dataset.featureNames()));
	}

	private static List<Path> writeOutputTables(OutputTables tables, OutputPlan plan) {
		List<Path> written = new ArrayList<>(4);
		writeIfRequested(plan.targetPsmPath(), tables.psmHeader(), tables.targetPsmRows(), written);
		writeIfRequested(plan.targetPeptidePath(), tables.peptideHeader(), tables.targetPeptideRows(), written);
		writeIfRequested(plan.decoyPsmPath(), tables.psmHeader(), tables.decoyPsmRows(), written);
		writeIfRequested(plan.decoyPeptidePath(), tables.peptideHeader(), tables.decoyPeptideRows(), written);
		return written;
	}

	private static void writeIfRequested(
		Path path,
		List<String> header,
		List<String[]> rows,
		List<Path> written
	) {
		if (path == null) {
			return;
		}
		ensureParentDirectory(path);
		TsvWriter.write(path, header, rows);
		written.add(path);
	}

	private static void ensureParentDirectory(Path path) {
		Path parent = path.toAbsolutePath().normalize().getParent();
		if (parent == null) {
			return;
		}
		try {
			Files.createDirectories(parent);
		} catch (Exception e) {
			throw new RuntimeException("Unable to create output directory: " + parent, e);
		}
	}

	private static OutputPlan buildOutputPlan(JavaPotOptions config) {
		if (hasExplicitOutputOverride(config)) {
			return new OutputPlan(
				config.resultsPsms(),
				config.resultsPeptides(),
				config.decoyResultsPsms(),
				config.decoyResultsPeptides()
			);
		}

		String base = pinOutputBaseName(config.pinFile());
		Path targetPeptide = config.destDir().resolve(base + ".peptides.tsv");
		Path targetPsm = config.writePsmFiles() ? config.destDir().resolve(base + ".psms.tsv") : null;
		Path decoyPeptide = config.writeDecoyFiles() ? config.destDir().resolve(base + ".decoy_peptides.tsv") : null;
		Path decoyPsm = (config.writeDecoyFiles() && config.writePsmFiles())
			? config.destDir().resolve(base + ".decoy_psms.tsv")
			: null;
		return new OutputPlan(targetPsm, targetPeptide, decoyPsm, decoyPeptide);
	}

	private static boolean hasExplicitOutputOverride(JavaPotOptions config) {
		return config.resultsPeptides() != null ||
			config.decoyResultsPeptides() != null ||
			config.resultsPsms() != null ||
			config.decoyResultsPsms() != null;
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

	private static String joinPaths(List<Path> paths) {
		if (paths.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < paths.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(paths.get(i));
		}
		return sb.toString();
	}

	private static void log(JavaPotOptions config, String message) {
		if (config.quiet()) {
			return;
		}
		System.out.println(message);
	}

	private static List<PercolatorFoldModel> trainModels(
		PsmDataset dataset,
		int[][] folds,
		JavaPotOptions config,
		DeterministicRandom rng
	) {
		log(config, "Splitting PSMs into " + config.folds() + " folds...");
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
				config.mixmax() ? ConfidenceMode.MIXMAX : ConfidenceMode.TDC,
				config.quiet()
			);
			final int[] trainCopy = Arrays.copyOf(trainIdx, trainIdx.length);
			tasks.add(() -> {
				log(config, "Analyzing Fold " + fold + "...");
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

	private static TrainingRecovery recoverFromFoldTrainingFailure(
		PsmDataset dataset,
		JavaPotOptions config,
		ConfidenceMode confidenceMode,
		FeatureStartChoice startChoice,
		RuntimeException trainingError
	) {
		if (!isNoTrainingPassFailure(trainingError)) {
			throw trainingError;
		}

		for (int retry = 1; retry <= config.maxRetries(); retry++) {
			long retrySeed = config.seed() + (REFOLD_SEED_STRIDE * retry);
			log(
				config,
				"Fold training failed due to a no-label fold; re-folding attempt " + retry + "/" + config.maxRetries() + "."
			);
			try {
				DeterministicRandom retryRng = new DeterministicRandom(retrySeed);
				int[][] retryFolds = FoldSplitter.split(dataset, config.folds(), retryRng, config.pinFile().getFileName().toString());
				List<PercolatorFoldModel> retryModels = trainModels(dataset, retryFolds, config, retryRng);
				double[] retryScores = predictScores(dataset, retryFolds, retryModels, config.testFdr());
				double[] finalScores = maybeFallbackToBestFeature(
					dataset,
					retryModels,
					retryScores,
					config.testFdr(),
					confidenceMode,
					config.seed(),
					config.quiet()
				);
				log(config, "Recovered from fold training failure after re-folding.");
				return new TrainingRecovery(retryModels, finalScores, false);
			} catch (RuntimeException retryError) {
				if (!isNoTrainingPassFailure(retryError)) {
					throw retryError;
				}
			}
		}

		log(
			config,
			"Re-fold attempts exhausted (or disabled); forcing q-value/PEP to 1.0."
		);
		double[] fallbackScores = scoreByFeatureDirection(dataset, startChoice.featureName(), startChoice.descending());
		return new TrainingRecovery(new ArrayList<>(), fallbackScores, true);
	}

	private static FeatureStartChoice chooseStartFeature(
		PsmDataset dataset,
		String direction,
		double trainFdr,
		ConfidenceMode confidenceMode,
		long seed
	) {
		String[] featureNames = dataset.featureNames();
		boolean[] targets = dataset.rawTargets();
		if (featureNames.length == 0) {
			throw new IllegalStateException("No feature columns available for scoring.");
		}

		if (direction != null) {
			int featureIdx = -1;
			for (int i = 0; i < featureNames.length; i++) {
				if (featureNames[i].equals(direction)) {
					featureIdx = i;
					break;
				}
			}
			if (featureIdx < 0) {
				throw new IllegalArgumentException("Direction feature not found: " + direction);
			}
			double[] scores = dataset.featureColumn(direction);
			int descPass = countTrainingPasses(scores, targets, trainFdr, true, confidenceMode, seed);
			int ascPass = countTrainingPasses(scores, targets, trainFdr, false, confidenceMode, seed + 1L);
			boolean descending = descPass >= ascPass;
			int passCount = descending ? descPass : ascPass;
			return new FeatureStartChoice(direction, descending, passCount);
		}

		String bestFeature = featureNames[0];
		boolean bestDesc = true;
		int bestPass = Integer.MIN_VALUE;
		for (int j = 0; j < featureNames.length; j++) {
			double[] scores = dataset.featureColumn(featureNames[j]);
			for (boolean desc : new boolean[]{true, false}) {
				long labelSeed = seed + (j * 2L) + (desc ? 0L : 1L);
				int pass = countTrainingPasses(scores, targets, trainFdr, desc, confidenceMode, labelSeed);
				if (pass > bestPass) {
					bestPass = pass;
					bestFeature = featureNames[j];
					bestDesc = desc;
				}
			}
		}
		return new FeatureStartChoice(bestFeature, bestDesc, Math.max(bestPass, 0));
	}

	private static int countTrainingPasses(
		double[] scores,
		boolean[] targets,
		double trainFdr,
		boolean desc,
		ConfidenceMode confidenceMode,
		long seed
	) {
		boolean skipDecoysPlusOne = true;
		int[] labels = LabelUpdater.updateLabels(
			scores,
			targets,
			trainFdr,
			desc,
			confidenceMode,
			seed,
			skipDecoysPlusOne
		);
		return countOnes(labels);
	}

	private static double[] scoreByFeatureDirection(PsmDataset dataset, String featureName, boolean descending) {
		double[] scores = dataset.featureColumn(featureName);
		if (!descending) {
			for (int i = 0; i < scores.length; i++) {
				scores[i] = -scores[i];
			}
		}
		return scores;
	}

	private static boolean isNoTrainingPassFailure(Throwable error) {
		Throwable cursor = error;
		while (cursor != null) {
			String message = cursor.getMessage();
			if (message != null) {
				if (message.contains("No PSMs found below train_fdr for any feature.")) {
					return true;
				}
				if (message.startsWith("No PSMs accepted at train_fdr=")) {
					return true;
				}
			}
			cursor = cursor.getCause();
		}
		return false;
	}

	private static double[] maybeFallbackToBestFeature(
		PsmDataset dataset,
		List<PercolatorFoldModel> models,
		double[] scores,
		double testFdr,
		ConfidenceMode confidenceMode,
		long seed,
		boolean quiet
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
			if (!quiet) {
				System.out.println("Learned model did not improve over best feature. Falling back to best feature scoring.");
			}
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
		long seed,
		boolean forceNoDetections
	) {
		int[] psmBest = confidenceMode == ConfidenceMode.MIXMAX
			? sortedIndicesByScore(scores)
			: deduplicateBySpectrum(dataset, scores);
		int[] peptideBest = deduplicateByPeptide(dataset, psmBest, scores);

		double[] psmScores = gather(scores, psmBest);
		boolean[] psmTargets = gatherTargets(dataset.rawTargets(), psmBest);
		ConfidenceResult psmConfidence = forceNoDetections
			? forcedNoDetections(psmScores.length)
			: estimateConfidence(psmScores, psmTargets, confidenceMode, seed + 131L);

		double[] pepScores = gather(scores, peptideBest);
		boolean[] pepTargets = gatherTargets(dataset.rawTargets(), peptideBest);
		ConfidenceResult peptideConfidence = forceNoDetections
			? forcedNoDetections(pepScores.length)
			: estimateConfidence(pepScores, pepTargets, confidenceMode, seed + 197L);
		ArrayList<JavaPotPeptide> psmResults = buildApiRows(
			dataset,
			psmBest,
			psmScores,
			psmConfidence.qValues(),
			psmConfidence.pepValues()
		);
		ArrayList<JavaPotPeptide> peptideResults = buildApiRows(
			dataset,
			peptideBest,
			pepScores,
			peptideConfidence.qValues(),
			peptideConfidence.pepValues()
		);

		List<String> psmHeader;
		List<String[]> targetPsmRows;
		List<String[]> decoyPsmRows;
		List<String> peptideHeader;
		List<String[]> targetPeptideRows;
		List<String[]> decoyPeptideRows;
		if (outputFormat == OutputFormat.MOKAPOT) {
			psmHeader = mokapotHeader(dataset.columnGroups());
			targetPsmRows = buildMokapotRows(
				dataset,
				psmBest,
				psmScores,
				psmConfidence.qValues(),
				psmConfidence.pepValues(),
				psmHeader,
				true
			);
			decoyPsmRows = buildMokapotRows(
				dataset,
				psmBest,
				psmScores,
				psmConfidence.qValues(),
				psmConfidence.pepValues(),
				psmHeader,
				false
			);
			peptideHeader = psmHeader;
			targetPeptideRows = buildMokapotRows(
				dataset,
				peptideBest,
				pepScores,
				peptideConfidence.qValues(),
				peptideConfidence.pepValues(),
				peptideHeader,
				true
			);
			decoyPeptideRows = buildMokapotRows(
				dataset,
				peptideBest,
				pepScores,
				peptideConfidence.qValues(),
				peptideConfidence.pepValues(),
				peptideHeader,
				false
			);
		} else {
			psmHeader = PERCOLATOR_HEADER;
			targetPsmRows = buildPercolatorRows(
				dataset,
				psmBest,
				psmScores,
				psmConfidence.qValues(),
				psmConfidence.pepValues(),
				true
			);
			decoyPsmRows = buildPercolatorRows(
				dataset,
				psmBest,
				psmScores,
				psmConfidence.qValues(),
				psmConfidence.pepValues(),
				false
			);
			peptideHeader = PERCOLATOR_HEADER;
			targetPeptideRows = buildPercolatorRows(
				dataset,
				peptideBest,
				pepScores,
				peptideConfidence.qValues(),
				peptideConfidence.pepValues(),
				true
			);
			decoyPeptideRows = buildPercolatorRows(
				dataset,
				peptideBest,
				pepScores,
				peptideConfidence.qValues(),
				peptideConfidence.pepValues(),
				false
			);
		}
		int peptidesAtThreshold = countTargetsAtThreshold(pepTargets, peptideConfidence.qValues(), evalFdr);

		return new OutputTables(
			psmHeader,
			targetPsmRows,
			decoyPsmRows,
			peptideHeader,
			targetPeptideRows,
			decoyPeptideRows,
			peptidesAtThreshold,
			psmResults,
			peptideResults,
			psmConfidence.pi0(),
			peptideConfidence.pi0()
		);
	}

	private static ConfidenceResult estimateConfidence(
		double[] scores,
		boolean[] targets,
		ConfidenceMode confidenceMode,
		long seed
	) {
		double[] qValues;
		double[] pepValues;
		Double pi0 = null;
		if (confidenceMode == ConfidenceMode.MIXMAX) {
			MixMaxQValues.Result mixMax = MixMaxQValues.compute(scores, targets, true, seed, false, 0.5);
			qValues = mixMax.qValues();
			pi0 = mixMax.pi0();
			pepValues = PepEstimator.tdcToPep(scores, targets).pepValues();
		} else {
			qValues = QValues.tdc(scores, targets, true);
			pepValues = PepEstimator.tdcQvalsToPep(scores, targets, qValues).pepValues();
		}
		return new ConfidenceResult(qValues, pepValues, pi0);
	}

	private static ConfidenceResult forcedNoDetections(int length) {
		double[] qValues = new double[length];
		double[] pepValues = new double[length];
		Arrays.fill(qValues, 1.0);
		Arrays.fill(pepValues, 1.0);
		return new ConfidenceResult(qValues, pepValues, null);
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
		List<String> header,
		boolean targetsOnly
	) {
		List<String[]> out = new ArrayList<>(keptIdx.length);
		for (int i = 0; i < keptIdx.length; i++) {
			int rowIdx = keptIdx[i];
			if (dataset.targetAt(rowIdx) != targetsOnly) {
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
		double[] peps,
		boolean targetsOnly
	) {
		List<String[]> out = new ArrayList<>(keptIdx.length);
		for (int i = 0; i < keptIdx.length; i++) {
			int rowIdx = keptIdx[i];
			if (dataset.targetAt(rowIdx) != targetsOnly) {
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

	private static ArrayList<JavaPotPeptide> buildApiRows(
		PsmDataset dataset,
		int[] keptIdx,
		double[] scores,
		double[] qvals,
		double[] peps
	) {
		ArrayList<JavaPotPeptide> out = new ArrayList<>(keptIdx.length);
		for (int i = 0; i < keptIdx.length; i++) {
			int rowIdx = keptIdx[i];
			out.add(
				new JavaPotPeptide(
					scores[i],
					qvals[i],
					peps[i],
					!dataset.targetAt(rowIdx),
					resolvePercolatorPsmId(dataset, rowIdx),
					dataset.peptideAt(rowIdx)
				)
			);
		}
		return out;
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
		List<String[]> targetPsmRows,
		List<String[]> decoyPsmRows,
		List<String> peptideHeader,
		List<String[]> targetPeptideRows,
		List<String[]> decoyPeptideRows,
		int peptidesAtThreshold,
		ArrayList<JavaPotPeptide> psmResults,
		ArrayList<JavaPotPeptide> peptideResults,
		Double psmPi0,
		Double peptidePi0
	) {
	}

	private record OutputPlan(
		Path targetPsmPath,
		Path targetPeptidePath,
		Path decoyPsmPath,
		Path decoyPeptidePath
	) {
	}

	private record FeatureStartChoice(String featureName, boolean descending, int passCount) {
	}

	private record TrainingRecovery(List<PercolatorFoldModel> models, double[] scores, boolean forceNoDetections) {
	}

	private record ConfidenceResult(double[] qValues, double[] pepValues, Double pi0) {
	}
}
