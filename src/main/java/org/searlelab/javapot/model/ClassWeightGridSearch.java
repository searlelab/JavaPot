package org.searlelab.javapot.model;

import org.searlelab.javapot.util.DeterministicRandom;

/**
 * ClassWeightGridSearch selects linear SVM class weights from a small fixed grid.
 * It uses deterministic inner cross-validation to keep model selection reproducible for a given seed.
 */
public final class ClassWeightGridSearch {
	private static final double[] GRID = new double[]{0.1, 1.0, 10.0};

	private ClassWeightGridSearch() {
	}

	/**
	 * Selects class weights with deterministic inner cross-validation on labeled rows.
	 */
	public static ClassWeightPair select(double[][] x, int[] y01, long seed) {
		if (x.length != y01.length) {
			throw new IllegalArgumentException("X/Y mismatch in grid search");
		}
		if (x.length < 3) {
			return new ClassWeightPair(1.0, 1.0);
		}

		DeterministicRandom rng = new DeterministicRandom(seed);
		int[] perm = rng.permutation(x.length);
		int[][] folds = splitContiguous(perm, 3);
		CvSplit[] splits = buildCvSplits(x, y01, folds);

		ClassWeightPair best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (double neg : GRID) {
			for (double pos : GRID) {
				double score = evaluate(splits, neg, pos);
				if (score > bestScore) {
					bestScore = score;
					best = new ClassWeightPair(neg, pos);
				}
			}
		}

		if (best == null) {
			throw new IllegalStateException("Failed to select class weights");
		}
		return best;
	}

	private static double evaluate(CvSplit[] splits, double neg, double pos) {
		double total = 0.0;
		for (CvSplit split : splits) {
			LinearSvmModel model = LinearSvmModel.fit(
				split.xTrain(),
				split.yTrainSigned(),
				neg,
				pos,
				40
			);
			int[] pred = model.predictClasses(split.xTest());
			total += accuracy(pred, split.yTest01());
		}
		return total / splits.length;
	}

	private static double accuracy(int[] pred, int[] truth) {
		int ok = 0;
		for (int i = 0; i < pred.length; i++) {
			if (pred[i] == truth[i]) {
				ok++;
			}
		}
		return ok / (double) pred.length;
	}

	private static int[][] splitContiguous(int[] values, int folds) {
		int n = values.length;
		int foldSize = n / folds;
		int remainder = n % folds;
		int[][] out = new int[folds][];
		int start = 0;
		for (int i = 0; i < folds; i++) {
			int size = foldSize + (i < remainder ? 1 : 0);
			out[i] = new int[size];
			System.arraycopy(values, start, out[i], 0, size);
			start += size;
		}
		return out;
	}

	private static CvSplit[] buildCvSplits(double[][] x, int[] y01, int[][] folds) {
		CvSplit[] out = new CvSplit[folds.length];
		for (int fi = 0; fi < folds.length; fi++) {
			int[] test = folds[fi];
			int[] train = mergeExcept(folds, fi);
			double[][] xTrain = subsetRows(x, train);
			int[] yTrainSigned = map01toSigned(subsetLabels(y01, train));
			double[][] xTest = subsetRows(x, test);
			int[] yTest01 = subsetLabels(y01, test);
			out[fi] = new CvSplit(xTrain, yTrainSigned, xTest, yTest01);
		}
		return out;
	}

	private static int[] mergeExcept(int[][] folds, int skip) {
		int size = 0;
		for (int i = 0; i < folds.length; i++) {
			if (i == skip) {
				continue;
			}
			size += folds[i].length;
		}
		int[] out = new int[size];
		int p = 0;
		for (int i = 0; i < folds.length; i++) {
			if (i == skip) {
				continue;
			}
			System.arraycopy(folds[i], 0, out, p, folds[i].length);
			p += folds[i].length;
		}
		return out;
	}

	private static double[][] subsetRows(double[][] x, int[] idx) {
		double[][] out = new double[idx.length][x[0].length];
		for (int i = 0; i < idx.length; i++) {
			System.arraycopy(x[idx[i]], 0, out[i], 0, x[0].length);
		}
		return out;
	}

	private static int[] subsetLabels(int[] y, int[] idx) {
		int[] out = new int[idx.length];
		for (int i = 0; i < idx.length; i++) {
			out[i] = y[idx[i]];
		}
		return out;
	}

	private static int[] map01toSigned(int[] y01) {
		int[] out = new int[y01.length];
		for (int i = 0; i < y01.length; i++) {
			out[i] = y01[i] == 1 ? 1 : -1;
		}
		return out;
	}

	/**
	 * CvSplit stores one deterministic train/test split for class-weight evaluation.
	 */
	private record CvSplit(double[][] xTrain, int[] yTrainSigned, double[][] xTest, int[] yTest01) {
	}
}
