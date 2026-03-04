package org.searlelab.javapot.model;

import java.util.Arrays;

import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.stats.ConfidenceMode;
import org.searlelab.javapot.stats.LabelUpdater;
import org.searlelab.javapot.util.DeterministicRandom;

/**
 * PercolatorTrainer runs the iterative fold-training loop for JavaPot.
 * It initializes labels from the best feature, selects class weights, fits the linear SVM, and relabels across iterations.
 */
public final class PercolatorTrainer {
	private PercolatorTrainer() {
	}

	/**
	 * Trains one fold model using best-feature initialization and iterative relabeling.
	 */
	public static FoldTrainingOutput trainFold(
		PsmDataset dataset,
		int[] trainIdx,
		int fold,
		TrainingParams params
	) {
		double[][] raw = subsetRows(dataset.rawFeatures(), trainIdx);
		boolean[] targets = subsetTargets(dataset.rawTargets(), trainIdx);
		String[] featureNames = dataset.featureNames();
		boolean hasDecoys = containsLabel(targets, false);

		StartResult start = startingLabels(
			raw,
			targets,
			featureNames,
			params.direction(),
			params.trainFdr(),
			params.confidenceMode(),
			params.seed(),
			!hasDecoys
		);
		if (countOnes(start.labels()) == 0) {
			throw new RuntimeException("No PSMs accepted at train_fdr=" + params.trainFdr());
		}
		log(params, "Fold " + fold + " selected feature '" + start.bestFeature() + "' with " + start.bestPass() + " PSMs at q<=" + params.trainFdr() + ".");

		StandardScaler scaler = new StandardScaler();
		double[][] norm = scaler.fitTransform(raw);

		DeterministicRandom rng = new DeterministicRandom(params.seed());
		int[] shuffledIdx = rng.permutation(start.labels().length);
		int[] originalIdx = inversePermutation(shuffledIdx);

		double[][] normShuffled = takeRows(norm, shuffledIdx);
		int[] startShuffled = takeLabels(start.labels(), shuffledIdx);
		int startPositive = countOnes(startShuffled);

		TrainSubset cvSubset = filterLabeled(normShuffled, startShuffled);
		int cvSeed = rng.nextInt(1, 1_000_000);
		ClassWeightPair pair = ClassWeightGridSearch.select(cvSubset.x(), cvSubset.y01(), cvSeed);
		log(params, "Fold " + fold + " class_weight = {0: " + pair.negative() + ", 1: " + pair.positive() + "}");

		int[] target = Arrays.copyOf(startShuffled, startShuffled.length);
		int[] numPassed = new int[params.maxIter()];
		LinearSvmModel model = null;
		boolean degradedDuringIterations = false;
		for (int i = 0; i < params.maxIter(); i++) {
			TrainSubset iterSubset = filterLabeled(normShuffled, target);
			model = LinearSvmModel.fit(iterSubset.x(), iterSubset.ySigned(), pair.negative(), pair.positive(), 40);
			double[] scoresShuffled = model.decisionFunction(normShuffled);
			double[] scoresOriginal = reorderByOriginal(scoresShuffled, originalIdx);
			long labelSeed = params.seed() + (i + 1L) * 10_000L;
			boolean skipDecoysPlusOne = true;
			int[] labelsOriginal = LabelUpdater.updateLabels(
				scoresOriginal,
				targets,
				params.trainFdr(),
				true,
				params.confidenceMode(),
				labelSeed,
				skipDecoysPlusOne
			);
			target = takeLabels(labelsOriginal, shuffledIdx);
			numPassed[i] = countOnes(target);
			log(params, "Fold " + fold + " Iteration " + (i + 1) + ": " + numPassed[i] + " training PSMs passed.");
			if (numPassed[i] == 0) {
				degradedDuringIterations = true;
				break;
			}
		}

		if (model == null) {
			throw new IllegalStateException("Failed to fit model for fold " + fold);
		}

		int lastPassed = numPassed[numPassed.length - 1];
		boolean underperformed = degradedDuringIterations || lastPassed <= start.bestPass() || lastPassed <= startPositive;
		if (underperformed) {
			log(params, "Fold " + fold + " model underperformed best feature; keeping model and deferring to fallback checks.");
		}
		log(params, "Finished training Fold " + fold + ".");

		PercolatorFoldModel foldModel = new PercolatorFoldModel(
			featureNames,
			scaler.means(),
			scaler.scales(),
			model,
			start.bestFeature(),
			start.bestPass(),
			start.bestDesc(),
			fold
		);

		return new FoldTrainingOutput(foldModel, start.bestFeature(), start.bestPass(), start.bestDesc());
	}

	private static StartResult startingLabels(
		double[][] x,
		boolean[] targets,
		String[] featureNames,
		String direction,
		double trainFdr,
		ConfidenceMode confidenceMode,
		long seed,
		boolean forceSkipDecoysPlusOne
	) {
		if (direction == null) {
			int bestPass = -1;
			String bestFeature = null;
			boolean bestDesc = true;
			int[] bestLabels = null;
			for (int j = 0; j < featureNames.length; j++) {
				double[] scores = column(x, j);
				for (boolean desc : new boolean[]{true, false}) {
					long labelSeed = seed + (j * 2L) + (desc ? 0L : 1L);
					boolean skipDecoysPlusOne = true;
					int[] labels = LabelUpdater.updateLabels(
						scores,
						targets,
						trainFdr,
						desc,
						confidenceMode,
						labelSeed,
						skipDecoysPlusOne
					);
					int pass = countOnes(labels);
					if (pass > bestPass) {
						bestPass = pass;
						bestFeature = featureNames[j];
						bestDesc = desc;
						bestLabels = labels;
					}
				}
			}
			if (bestPass <= 0 || bestLabels == null) {
				if (!forceSkipDecoysPlusOne) {
					return startingLabels(x, targets, featureNames, direction, trainFdr, confidenceMode, seed, true);
				}
				throw new RuntimeException("No PSMs found below train_fdr for any feature.");
			}
			return new StartResult(bestFeature, bestPass, bestDesc, bestLabels);
		}

		int featureIdx = -1;
		for (int j = 0; j < featureNames.length; j++) {
			if (featureNames[j].equals(direction)) {
				featureIdx = j;
				break;
			}
		}
		if (featureIdx < 0) {
			throw new IllegalArgumentException("Direction feature not found: " + direction);
		}

		double[] scores = column(x, featureIdx);
		boolean skipDecoysPlusOne = true;
		int[] descLabels = LabelUpdater.updateLabels(scores, targets, trainFdr, true, confidenceMode, seed, skipDecoysPlusOne);
		int[] ascLabels = LabelUpdater.updateLabels(scores, targets, trainFdr, false, confidenceMode, seed + 1L, skipDecoysPlusOne);
		int descPass = countOnes(descLabels);
		int ascPass = countOnes(ascLabels);
		if (descPass <= 0 && ascPass <= 0 && !forceSkipDecoysPlusOne) {
			return startingLabels(x, targets, featureNames, direction, trainFdr, confidenceMode, seed, true);
		}
		if (descPass >= ascPass) {
			return new StartResult(direction, descPass, true, descLabels);
		}
		return new StartResult(direction, ascPass, false, ascLabels);
	}

	private static double[][] subsetRows(double[][] x, int[] idx) {
		double[][] out = new double[idx.length][x[0].length];
		for (int i = 0; i < idx.length; i++) {
			System.arraycopy(x[idx[i]], 0, out[i], 0, x[0].length);
		}
		return out;
	}

	private static boolean[] subsetTargets(boolean[] targets, int[] idx) {
		boolean[] out = new boolean[idx.length];
		for (int i = 0; i < idx.length; i++) {
			out[i] = targets[idx[i]];
		}
		return out;
	}

	private static int[] inversePermutation(int[] perm) {
		int[] out = new int[perm.length];
		for (int i = 0; i < perm.length; i++) {
			out[perm[i]] = i;
		}
		return out;
	}

	private static double[][] takeRows(double[][] x, int[] idx) {
		double[][] out = new double[idx.length][x[0].length];
		for (int i = 0; i < idx.length; i++) {
			out[i] = Arrays.copyOf(x[idx[i]], x[idx[i]].length);
		}
		return out;
	}

	private static int[] takeLabels(int[] labels, int[] idx) {
		int[] out = new int[idx.length];
		for (int i = 0; i < idx.length; i++) {
			out[i] = labels[idx[i]];
		}
		return out;
	}

	private static double[] reorderByOriginal(double[] scoresShuffled, int[] originalIdx) {
		double[] out = new double[scoresShuffled.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = scoresShuffled[originalIdx[i]];
		}
		return out;
	}

	private static TrainSubset filterLabeled(double[][] x, int[] labels) {
		int count = 0;
		for (int label : labels) {
			if (label != 0) {
				count++;
			}
		}
		double[][] outX = new double[count][x[0].length];
		int[] ySigned = new int[count];
		int[] y01 = new int[count];
		int c = 0;
		for (int i = 0; i < labels.length; i++) {
			if (labels[i] == 0) {
				continue;
			}
			System.arraycopy(x[i], 0, outX[c], 0, x[i].length);
			ySigned[c] = labels[i] == 1 ? 1 : -1;
			y01[c] = labels[i] == 1 ? 1 : 0;
			c++;
		}
		return new TrainSubset(outX, ySigned, y01);
	}

	private static int countOnes(int[] labels) {
		int c = 0;
		for (int label : labels) {
			if (label == 1) {
				c++;
			}
		}
		return c;
	}

	private static boolean containsLabel(boolean[] labels, boolean value) {
		for (boolean label : labels) {
			if (label == value) {
				return true;
			}
		}
		return false;
	}

	private static double[] column(double[][] x, int featureIdx) {
		double[] out = new double[x.length];
		for (int i = 0; i < x.length; i++) {
			out[i] = x[i][featureIdx];
		}
		return out;
	}

	private static void log(TrainingParams params, String message) {
		if (params.quiet()) {
			return;
		}
		System.out.println(message);
	}

	/**
	 * StartResult captures the initial best-feature selection used to seed iterative training.
	 */
	private record StartResult(String bestFeature, int bestPass, boolean bestDesc, int[] labels) {
	}

	/**
	 * TrainSubset holds labeled rows prepared for SVM fitting and class-weight search.
	 */
	private record TrainSubset(double[][] x, int[] ySigned, int[] y01) {
	}
}
