package org.searlelab.javapot.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.io.ModelIO;
import org.searlelab.javapot.io.PinFileParser;
import org.searlelab.javapot.model.PercolatorFoldModel;
import org.searlelab.javapot.pipeline.JavaPotApi;
import org.searlelab.javapot.pipeline.JavaPotPeptide;
import org.searlelab.javapot.pipeline.JavaPotRunResult;
import org.searlelab.javapot.stats.ConfidenceMode;
import org.searlelab.javapot.stats.LabelUpdater;

/**
 * JavaPotBakeoff performs greedy forward feature selection over one or more PIN files.
 * It repeatedly tests adding one candidate feature at a time and keeps the best improvement.
 */
public final class JavaPotBakeoff {
	private static final double DEFAULT_MIN_IMPROVEMENT_PERCENT = 0.1;
	private static final double LEVEL_DIRECTIONALITY_THRESHOLD = 0.02;
	private static final double COEFFICIENT_SIGN_EPSILON = 1e-8;

	private JavaPotBakeoff() {
	}

	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			printHelp();
			return;
		}
		try {
			BakeoffConfig config = parse(args);
			run(config);
		} catch (HelpRequestedException e) {
			printHelp();
		}
	}

	static BakeoffConfig parse(String[] args) {
		String requiredStartFeaturesRaw = null;
		double trainFdr = JavaPotOptions.DEFAULT_FDR;
		double testFdr = JavaPotOptions.DEFAULT_FDR;
		int maxIter = JavaPotOptions.DEFAULT_MAX_ITER;
		long seed = JavaPotOptions.DEFAULT_SEED;
		Integer subsetMaxTrain = null;
		int folds = JavaPotOptions.DEFAULT_FOLDS;
		Integer maxWorkers = null;
		int maxRetries = JavaPotOptions.DEFAULT_MAX_RETRIES;
		boolean mixmax = false;
		double minImprovementPercent = DEFAULT_MIN_IMPROVEMENT_PERCENT;
		Path featureSetDir = null;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "-h", "--help" -> {
					throw new HelpRequestedException();
				}
				case "--direction" -> {
					requiredStartFeaturesRaw = requireValue(args, ++i, arg);
				}
				case "--train_fdr" -> {
					trainFdr = parseDouble(requireValue(args, ++i, arg), arg);
				}
				case "--test_fdr" -> {
					testFdr = parseDouble(requireValue(args, ++i, arg), arg);
				}
				case "--max_iter" -> {
					maxIter = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "--seed" -> {
					seed = parseLong(requireValue(args, ++i, arg), arg);
				}
				case "--subset_max_train" -> {
					subsetMaxTrain = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "--folds" -> {
					folds = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "-w", "--max_workers" -> {
					maxWorkers = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "--max_retries" -> {
					maxRetries = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "--mixmax", "--post-processing-mix-max" -> {
					mixmax = true;
				}
				case "--min_improvement_percent" -> {
					minImprovementPercent = parseDouble(requireValue(args, ++i, arg), arg);
				}
				default -> {
					if (arg.startsWith("-")) {
						throw new IllegalArgumentException("Unknown option: " + arg);
					}
					if (featureSetDir != null) {
						throw new IllegalArgumentException("Exactly one feature-set directory is required.");
					}
					featureSetDir = Path.of(arg);
				}
			}
		}

		if (requiredStartFeaturesRaw == null || requiredStartFeaturesRaw.isBlank()) {
			throw new IllegalArgumentException("--direction is required");
		}
		if (featureSetDir == null) {
			throw new IllegalArgumentException("A feature-set directory path is required.");
		}
		if (!Files.isDirectory(featureSetDir)) {
			throw new IllegalArgumentException("Feature-set path is not a directory: " + featureSetDir);
		}
		if (folds < 2) {
			throw new IllegalArgumentException("--folds must be >= 2");
		}
		if (maxWorkers != null && maxWorkers < 1) {
			throw new IllegalArgumentException("--max_workers must be >= 1");
		}
		if (maxRetries < 0) {
			throw new IllegalArgumentException("--max_retries must be >= 0");
		}
		if (maxIter < 1) {
			throw new IllegalArgumentException("--max_iter must be >= 1");
		}
		if (subsetMaxTrain != null && subsetMaxTrain < 1) {
			throw new IllegalArgumentException("--subset_max_train must be >= 1");
		}
		if (trainFdr <= 0.0 || trainFdr >= 1.0) {
			throw new IllegalArgumentException("--train_fdr must be in (0,1)");
		}
		if (testFdr <= 0.0 || testFdr >= 1.0) {
			throw new IllegalArgumentException("--test_fdr must be in (0,1)");
		}
		if (minImprovementPercent < 0.0) {
			throw new IllegalArgumentException("--min_improvement_percent must be >= 0");
		}

		List<Path> inputPins = discoverInputPins(featureSetDir);
		if (inputPins.isEmpty()) {
			throw new IllegalArgumentException(
				"No supported feature files were found in directory: " + featureSetDir +
					" (supported extensions: .pin, .tsv, .txt)"
			);
		}

		List<String> requiredStartFeatures = parseCommaSeparatedFeatures(requiredStartFeaturesRaw, "--direction");
		String direction = requiredStartFeatures.get(0);
		int resolvedMaxWorkers = maxWorkers != null ? maxWorkers : folds;
		return new BakeoffConfig(
			featureSetDir,
			inputPins,
			direction,
			requiredStartFeatures,
			trainFdr,
			testFdr,
			maxIter,
			seed,
			subsetMaxTrain,
			folds,
			resolvedMaxWorkers,
			maxRetries,
			mixmax,
			minImprovementPercent
		);
	}

	public static void printHelp() {
		String help = """
			Usage: javapot-bakeoff [options] --direction FEATURE[,FEATURE...] <feature_dir>
			Options:
			  -h, --help            Show this help message and exit.
			  --direction FEATURES  Comma-separated required starting features; first feature is used as training direction.
			  --min_improvement_percent PCT
			                        Minimum required percent improvement to keep adding features (default: 0.1).
			  --train_fdr TRAIN_FDR
			                        Train-time FDR threshold. Default: 0.01.
			  --test_fdr TEST_FDR   Test-time FDR threshold used for peptide counting. Default: 0.01.
			  --max_iter MAX_ITER   Max training iterations per fold. Default: 10.
			  --seed SEED           Random seed for deterministic fold splits/training. Default: 1.
			  --subset_max_train N  Optional cap on fold training-set size.
			  --folds FOLDS         Number of cross-validation folds. Default: 3.
			  -w, --max_workers N   Number of training workers (defaults to --folds).
			  --max_retries N       Re-fold retries after no-label fold failures. Default: 1.
			  --mixmax, --post-processing-mix-max
			                        Use Percolator mix-max post-processing.
			Output annotations:
			  feature*             Feature coefficient sign flipped against baseline direction.
			  feature~             Feature has a minimal directionality vector (level / near non-directional).
		""";
		System.out.println(help);
	}

	static BakeoffOutcome run(BakeoffConfig config) {
		List<SourcePin> sources = loadAndValidateSources(config.pinFiles());
		List<String> featureNames = sources.get(0).featureNames();
		System.out.println("Loaded " + sources.size() + " feature files from " + config.featureSetDir());
		List<String> missingRequired = new ArrayList<>();
		for (String required : config.requiredStartFeatures()) {
			if (!featureNames.contains(required)) {
				missingRequired.add(required);
			}
		}
		if (!missingRequired.isEmpty()) {
			throw new IllegalArgumentException(
				"Required starting feature(s) not found: " + String.join(", ", missingRequired) +
					". Usable feature columns: " + String.join(", ", featureNames)
			);
		}

		Path workDir;
		try {
			workDir = Files.createTempDirectory("javapot-bakeoff-");
		} catch (IOException e) {
			throw new RuntimeException("Unable to create temporary working directory for bakeoff runs", e);
		}

		try {
			Map<String, FeatureDirectionality> directionality = computeDirectionality(sources, featureNames, config);
			printDirectionalitySummary(directionality);
			BakeoffOutcome outcome = runGreedyBakeoff(
				featureNames,
				config.requiredStartFeatures(),
				directionality,
				config.minImprovementPercent(),
				featureSet -> evaluateAcrossSources(sources, featureSet, config, directionality, workDir),
				System.out
			);
			System.out.println("Final feature set: " + joinFeatures(outcome.keptFeatures()));
			System.out.println("Final total unique peptides: " + outcome.totalPeptides());
			return outcome;
		} finally {
			deleteDirectoryQuietly(workDir);
		}
	}

	static BakeoffOutcome runGreedyBakeoff(
		List<String> allFeatures,
		List<String> startFeatures,
		Map<String, FeatureDirectionality> directionality,
		double minImprovementPercent,
		FeatureSetEvaluator evaluator,
		PrintStream out
	) {
		if (startFeatures == null || startFeatures.isEmpty()) {
			throw new IllegalArgumentException("At least one required starting feature is required.");
		}
		List<String> dedupedStart = deduplicatePreservingOrder(startFeatures);
		List<String> missing = new ArrayList<>();
		for (String feature : dedupedStart) {
			if (!allFeatures.contains(feature)) {
				missing.add(feature);
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("Starting feature(s) not found in available features: " + String.join(", ", missing));
		}

		List<String> kept = new ArrayList<>(dedupedStart);
		List<String> remaining = new ArrayList<>();
		for (String feature : allFeatures) {
			if (!kept.contains(feature)) {
				remaining.add(feature);
			}
		}

		TrialEvaluation current = evaluator.evaluate(List.copyOf(kept));
		long currentTotal = current.totalPeptides();
		out.println("Starting with " + formatFeaturesForOutput(kept, directionality, current.flippedFeatures()));

		while (!remaining.isEmpty()) {
			String bestCandidate = null;
			TrialEvaluation bestTrial = null;
			for (String candidate : remaining) {
				List<String> trial = new ArrayList<>(kept.size() + 1);
				trial.addAll(kept);
				trial.add(candidate);
				TrialEvaluation trialResult = evaluator.evaluate(List.copyOf(trial));
				out.println(
					trialResult.totalPeptides() + " " +
						formatFeaturesForOutput(trial, directionality, trialResult.flippedFeatures())
				);
				if (bestCandidate == null || trialResult.totalPeptides() > bestTrial.totalPeptides()) {
					bestCandidate = candidate;
					bestTrial = trialResult;
				}
			}

			if (!improvesMoreThanThreshold(currentTotal, bestTrial.totalPeptides(), minImprovementPercent)) {
				double pct = percentImprovement(currentTotal, bestTrial.totalPeptides());
				out.println(
					String.format(
						Locale.US,
						"Stopping because best candidate improvement %.4f%% is not greater than %.4f%%.",
						pct,
						minImprovementPercent
					)
				);
				break;
			}

			kept.add(bestCandidate);
			remaining.remove(bestCandidate);
			current = bestTrial;
			currentTotal = current.totalPeptides();
			out.println("Picking " + formatFeaturesForOutput(kept, directionality, current.flippedFeatures()) + " to continue");
		}

		return new BakeoffOutcome(List.copyOf(kept), currentTotal);
	}

	private static TrialEvaluation evaluateAcrossSources(
		List<SourcePin> sources,
		List<String> selectedFeatures,
		BakeoffConfig config,
		Map<String, FeatureDirectionality> directionality,
		Path workDir
	) {
		long total = 0L;
		Map<String, Double> coefficientSums = new LinkedHashMap<>();
		Map<String, Integer> coefficientCounts = new LinkedHashMap<>();
		for (int i = 0; i < sources.size(); i++) {
			SourcePin source = sources.get(i);
			Path subsetPin = workDir.resolve("source_" + i + ".pin");
			Path targetPeptides = workDir.resolve("source_" + i + ".targets.peptides.tsv");
			Path savedModel = workDir.resolve("source_" + i + ".model.tsv");
			writeSubsetPin(source, selectedFeatures, subsetPin);
			JavaPotOptions options = new JavaPotOptions(
				subsetPin,
				workDir,
				config.maxWorkers(),
				OutputFormat.PERCOLATOR,
				true,
				config.trainFdr(),
				config.testFdr(),
				config.maxIter(),
				config.seed(),
				config.direction(),
				config.subsetMaxTrain(),
				savedModel,
				false,
				false,
				targetPeptides,
				null,
				null,
				null,
				null,
				config.folds(),
				config.maxRetries(),
				config.mixmax()
			);
			JavaPotRunResult result = JavaPotApi.run(options);
			total += countAcceptedTargetPeptides(result.peptides(), config.testFdr());
			if (Files.exists(savedModel)) {
				List<PercolatorFoldModel> models = ModelIO.loadModels(savedModel);
				accumulateFeatureCoefficientStats(models, selectedFeatures, coefficientSums, coefficientCounts);
			}
		}
		Set<String> flipped = detectFlippedDirectionalFeatures(
			selectedFeatures,
			coefficientSums,
			coefficientCounts,
			directionality
		);
		return new TrialEvaluation(total, flipped);
	}

	private static long countAcceptedTargetPeptides(List<JavaPotPeptide> peptides, double testFdr) {
		long count = 0L;
		for (JavaPotPeptide peptide : peptides) {
			if (!peptide.isDecoy() && peptide.qValue() <= testFdr) {
				count++;
			}
		}
		return count;
	}

	private static Map<String, FeatureDirectionality> computeDirectionality(
		List<SourcePin> sources,
		List<String> featureNames,
		BakeoffConfig config
	) {
		Map<String, FeatureDirectionality> out = new LinkedHashMap<>();
		ConfidenceMode mode = config.mixmax() ? ConfidenceMode.MIXMAX : ConfidenceMode.TDC;
		for (int featureIndex = 0; featureIndex < featureNames.size(); featureIndex++) {
			String feature = featureNames.get(featureIndex);
			long descendingPass = 0L;
			long ascendingPass = 0L;
			for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
				PsmDataset dataset = sources.get(sourceIndex).dataset();
				double[] scores = dataset.featureColumn(feature);
				boolean[] targets = dataset.rawTargets();
				long descSeed = config.seed() + (featureIndex * 2L) + (sourceIndex * 101L);
				long ascSeed = descSeed + 1L;
				descendingPass += countPositiveLabels(
					LabelUpdater.updateLabels(scores, targets, config.trainFdr(), true, mode, descSeed, true)
				);
				ascendingPass += countPositiveLabels(
					LabelUpdater.updateLabels(scores, targets, config.trainFdr(), false, mode, ascSeed, true)
				);
			}
			out.put(feature, classifyDirectionality(descendingPass, ascendingPass));
		}
		return out;
	}

	private static void printDirectionalitySummary(Map<String, FeatureDirectionality> directionality) {
		int high = 0;
		int low = 0;
		int level = 0;
		for (FeatureDirectionality value : directionality.values()) {
			switch (value) {
				case HIGH_TARGET_WHEN_HIGHER -> high++;
				case HIGH_TARGET_WHEN_LOWER -> low++;
				case LEVEL -> level++;
			}
		}
		System.out.println(
			"Directionality baseline: " + high + " high-is-target, " + low + " low-is-target, " + level + " level (~)."
		);
	}

	private static long countPositiveLabels(int[] labels) {
		long count = 0L;
		for (int label : labels) {
			if (label > 0) {
				count++;
			}
		}
		return count;
	}

	private static FeatureDirectionality classifyDirectionality(long descendingPass, long ascendingPass) {
		long best = Math.max(descendingPass, ascendingPass);
		long diff = Math.abs(descendingPass - ascendingPass);
		if (best <= 0) {
			return FeatureDirectionality.LEVEL;
		}
		double ratio = diff / (double) best;
		if (ratio < LEVEL_DIRECTIONALITY_THRESHOLD) {
			return FeatureDirectionality.LEVEL;
		}
		return descendingPass >= ascendingPass
			? FeatureDirectionality.HIGH_TARGET_WHEN_HIGHER
			: FeatureDirectionality.HIGH_TARGET_WHEN_LOWER;
	}

	private static void accumulateFeatureCoefficientStats(
		List<PercolatorFoldModel> models,
		List<String> selectedFeatures,
		Map<String, Double> coefficientSums,
		Map<String, Integer> coefficientCounts
	) {
		for (PercolatorFoldModel model : models) {
			String[] names = model.featureNames();
			double[] weights = model.svm().weights();
			Map<String, Double> byFeature = new LinkedHashMap<>();
			for (int i = 0; i < names.length; i++) {
				byFeature.put(names[i], weights[i]);
			}
			for (String feature : selectedFeatures) {
				Double weight = byFeature.get(feature);
				if (weight == null) {
					continue;
				}
				coefficientSums.merge(feature, weight, Double::sum);
				coefficientCounts.merge(feature, 1, Integer::sum);
			}
		}
	}

	private static Set<String> detectFlippedDirectionalFeatures(
		List<String> selectedFeatures,
		Map<String, Double> coefficientSums,
		Map<String, Integer> coefficientCounts,
		Map<String, FeatureDirectionality> directionality
	) {
		Set<String> flipped = new HashSet<>();
		for (String feature : selectedFeatures) {
			FeatureDirectionality expected = directionality.getOrDefault(feature, FeatureDirectionality.LEVEL);
			if (expected == FeatureDirectionality.LEVEL) {
				continue;
			}
			int count = coefficientCounts.getOrDefault(feature, 0);
			if (count <= 0) {
				continue;
			}
			double meanWeight = coefficientSums.getOrDefault(feature, 0.0) / count;
			if (Math.abs(meanWeight) < COEFFICIENT_SIGN_EPSILON) {
				continue;
			}
			boolean observedHighIsTarget = meanWeight > 0.0;
			boolean expectedHighIsTarget = expected == FeatureDirectionality.HIGH_TARGET_WHEN_HIGHER;
			if (observedHighIsTarget != expectedHighIsTarget) {
				flipped.add(feature);
			}
		}
		return flipped;
	}

	private static String formatFeaturesForOutput(
		List<String> features,
		Map<String, FeatureDirectionality> directionality,
		Set<String> flippedFeatures
	) {
		List<String> out = new ArrayList<>(features.size());
		for (String feature : features) {
			FeatureDirectionality dir = directionality.getOrDefault(feature, FeatureDirectionality.LEVEL);
			if (dir == FeatureDirectionality.LEVEL) {
				out.add(feature + "~");
				continue;
			}
			if (flippedFeatures.contains(feature)) {
				out.add(feature + "*");
				continue;
			}
			out.add(feature);
		}
		return String.join(", ", out);
	}

	private static void writeSubsetPin(SourcePin source, List<String> selectedFeatures, Path outPin) {
		Set<String> selected = new HashSet<>(selectedFeatures);
		List<String> outputHeader = new ArrayList<>();
		List<Integer> columnIndices = new ArrayList<>();
		for (String column : source.headers()) {
			if (!source.featureNameSet().contains(column) || selected.contains(column)) {
				outputHeader.add(column);
				columnIndices.add(source.dataset().colIndex(column));
			}
		}

		try (BufferedWriter writer = Files.newBufferedWriter(outPin)) {
			writer.write(String.join("\t", outputHeader));
			writer.newLine();
			for (int row = 0; row < source.dataset().size(); row++) {
				for (int i = 0; i < columnIndices.size(); i++) {
					if (i > 0) {
						writer.write('\t');
					}
					writer.write(source.dataset().rawValueAt(row, columnIndices.get(i)));
				}
				writer.newLine();
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed writing temporary bakeoff PIN: " + outPin, e);
		}
	}

	private static List<SourcePin> loadAndValidateSources(List<Path> pinFiles) {
		List<SourcePin> out = new ArrayList<>(pinFiles.size());
		List<String> referenceHeaders = null;
		List<String> referenceFeatures = null;
		for (Path pinFile : pinFiles) {
			PsmDataset dataset = PinFileParser.read(pinFile);
			List<String> headers = dataset.headers();
			List<String> features = List.of(dataset.featureNames());
			if (referenceHeaders == null) {
				referenceHeaders = headers;
				referenceFeatures = features;
			} else {
				if (!referenceHeaders.equals(headers)) {
					throw new IllegalArgumentException(
						"All input files must have identical headers. Mismatch detected in: " + pinFile
					);
				}
				if (!referenceFeatures.equals(features)) {
					throw new IllegalArgumentException(
						"All input files must have identical usable feature columns. Mismatch detected in: " + pinFile
					);
				}
			}
			out.add(new SourcePin(pinFile, dataset, headers, features, new HashSet<>(features)));
		}
		return out;
	}

	private static List<Path> discoverInputPins(Path featureSetDir) {
		try (Stream<Path> stream = Files.list(featureSetDir)) {
			return stream
				.filter(Files::isRegularFile)
				.filter(JavaPotBakeoff::isSupportedInputFile)
				.sorted()
				.toList();
		} catch (IOException e) {
			throw new RuntimeException("Failed listing feature-set directory: " + featureSetDir, e);
		}
	}

	private static boolean isSupportedInputFile(Path path) {
		String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return lower.endsWith(".pin") || lower.endsWith(".tsv") || lower.endsWith(".txt");
	}

	private static void deleteDirectoryQuietly(Path dir) {
		try (Stream<Path> stream = Files.walk(dir)) {
			List<Path> toDelete = stream.sorted(Comparator.reverseOrder()).toList();
			for (Path path : toDelete) {
				Files.deleteIfExists(path);
			}
		} catch (IOException ignored) {
			// Best-effort cleanup for temporary bakeoff files.
		}
	}

	private static boolean improvesMoreThanThreshold(long previousTotal, long candidateTotal, double minImprovementPercent) {
		if (candidateTotal <= previousTotal) {
			return false;
		}
		if (previousTotal <= 0L) {
			return true;
		}
		return percentImprovement(previousTotal, candidateTotal) > minImprovementPercent;
	}

	private static double percentImprovement(long previousTotal, long candidateTotal) {
		if (previousTotal <= 0L) {
			return candidateTotal > previousTotal ? Double.POSITIVE_INFINITY : 0.0;
		}
		return ((candidateTotal - previousTotal) * 100.0) / previousTotal;
	}

	private static String joinFeatures(List<String> features) {
		return String.join(", ", features);
	}

	private static String requireValue(String[] args, int idx, String opt) {
		if (idx >= args.length) {
			throw new IllegalArgumentException("Missing value for option " + opt);
		}
		return args[idx];
	}

	private static List<String> parseCommaSeparatedFeatures(String raw, String opt) {
		String[] parts = raw.split(",");
		List<String> out = new ArrayList<>(parts.length);
		for (String part : parts) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				out.add(trimmed);
			}
		}
		if (out.isEmpty()) {
			throw new IllegalArgumentException(opt + " must contain at least one feature name");
		}
		return deduplicatePreservingOrder(out);
	}

	private static List<String> deduplicatePreservingOrder(List<String> features) {
		LinkedHashSet<String> seen = new LinkedHashSet<>(features);
		return new ArrayList<>(seen);
	}

	private static int parseInt(String value, String opt) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid integer for " + opt + ": " + value, e);
		}
	}

	private static long parseLong(String value, String opt) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid long for " + opt + ": " + value, e);
		}
	}

	private static double parseDouble(String value, String opt) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid double for " + opt + ": " + value, e);
		}
	}

	public static final class HelpRequestedException extends RuntimeException {
		public HelpRequestedException() {
			super("Help requested");
		}
	}

	@FunctionalInterface
	interface FeatureSetEvaluator {
		TrialEvaluation evaluate(List<String> featureSet);
	}

	record BakeoffConfig(
		Path featureSetDir,
		List<Path> pinFiles,
		String direction,
		List<String> requiredStartFeatures,
		double trainFdr,
		double testFdr,
		int maxIter,
		long seed,
		Integer subsetMaxTrain,
		int folds,
		int maxWorkers,
		int maxRetries,
		boolean mixmax,
		double minImprovementPercent
	) {
	}

	record BakeoffOutcome(
		List<String> keptFeatures,
		long totalPeptides
	) {
	}

	record TrialEvaluation(
		long totalPeptides,
		Set<String> flippedFeatures
	) {
	}

	enum FeatureDirectionality {
		HIGH_TARGET_WHEN_HIGHER,
		HIGH_TARGET_WHEN_LOWER,
		LEVEL
	}

	private record SourcePin(
		Path pinFile,
		PsmDataset dataset,
		List<String> headers,
		List<String> featureNames,
		Set<String> featureNameSet
	) {
	}
}
