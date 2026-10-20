package org.searlelab.javapot.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.function.IntToDoubleFunction;

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
				double score = evaluate(splits, neg, pos, false);
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

	static ClassWeightPair select(double[][] x, int[] y01, long seed, ExecutorService helpers) {
		return select(x, y01, seed, helpers, helpers != null);
	}

	static ClassWeightPair select(double[][] x, int[] y01, long seed, ExecutorService helpers, boolean interruptible) {
		if (helpers == null && !interruptible) {
			return select(x, y01, seed);
		}
		if (x.length != y01.length) {
			throw new IllegalArgumentException("X/Y mismatch in grid search");
		}
		if (x.length < 3) {
			return new ClassWeightPair(1.0, 1.0);
		}

		double[] scores = scores(x, y01, seed, helpers, interruptible);
		ClassWeightPair best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		int ordinal = 0;
		for (double neg : GRID) {
			for (double pos : GRID) {
				double score = scores[ordinal++];
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

	// The caller owns the bounded helper pool; the outer-fold worker participates too.
	static double[] scores(double[][] x, int[] y01, long seed, ExecutorService helpers) {
		return scores(x, y01, seed, helpers, helpers != null);
	}

	private static double[] scores(double[][] x, int[] y01, long seed, ExecutorService helpers, boolean interruptible) {
		DeterministicRandom rng = new DeterministicRandom(seed);
		int[] perm = rng.permutation(x.length);
		int[][] folds = splitContiguous(perm, 3);
		CvSplit[] splits = buildCvSplits(x, y01, folds);
		return evaluatePairs(ordinal -> evaluate(splits, GRID[ordinal / GRID.length], GRID[ordinal % GRID.length], interruptible), helpers);
	}

	static double[] evaluatePairs(IntToDoubleFunction evaluation, ExecutorService helpers) {
		double[] scores = new double[GRID.length * GRID.length];
		if (helpers == null) {
			for (int i = 0; i < scores.length; i++) {
				scores[i] = evaluation.applyAsDouble(i);
			}
			return scores;
		}
		List<FutureTask<Double>> tasks = new ArrayList<>(scores.length);
		try {
			for (int i = 0; i < scores.length; i++) {
				final int ordinal = i;
				FutureTask<Double> task = new FutureTask<>(() -> evaluation.applyAsDouble(ordinal));
				tasks.add(task);
				// Reserve the first pair for this fold worker so helpers cannot claim every pair.
				if (i > 0) {
					helpers.execute(task);
				}
			}
			// FutureTask runs at most once, including when already queued on a helper.
			// Calling run here prevents parked fold workers from wasting their budget.
			for (FutureTask<Double> task : tasks) {
				checkInterrupted();
				task.run();
			}
			for (int i = 0; i < scores.length; i++) {
				try {
					scores[i] = tasks.get(i).get();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new CancellationException("Grid search interrupted");
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof RuntimeException) {
						throw (RuntimeException) cause;
					}
					if (cause instanceof Error) {
						throw (Error) cause;
					}
					throw new IllegalStateException("Grid search failed", cause);
				}
			}
			return scores;
		} finally {
			for (FutureTask<Double> task : tasks) {
				if (!task.isDone()) {
					task.cancel(true);
				}
			}
		}
	}

	private static void checkInterrupted() {
		if (Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Grid search interrupted");
		}
	}

	private static double evaluate(CvSplit[] splits, double neg, double pos, boolean interruptible) {
		double total = 0.0;
		for (CvSplit split : splits) {
			if (interruptible) {
				checkInterrupted();
			}
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
		// SVM consumers only read rows. Retain the historical behavior for irregular public inputs.
		double[][] out = new double[idx.length][];
		int width = x[0].length;
		for (int i = 0; i < idx.length; i++) {
			double[] row = x[idx[i]];
			if (row.length == width) {
				out[i] = row;
			} else {
				out[i] = new double[width];
				System.arraycopy(row, 0, out[i], 0, width);
			}
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
	private static final class CvSplit {
		private final double[][] xTrain;
		private final int[] yTrainSigned;
		private final double[][] xTest;
		private final int[] yTest01;

		private CvSplit(double[][] xTrain, int[] yTrainSigned, double[][] xTest, int[] yTest01) {
			this.xTrain = xTrain;
			this.yTrainSigned = yTrainSigned;
			this.xTest = xTest;
			this.yTest01 = yTest01;
		}

		private double[][] xTrain() {
			return xTrain;
		}

		private int[] yTrainSigned() {
			return yTrainSigned;
		}

		private double[][] xTest() {
			return xTest;
		}

		private int[] yTest01() {
			return yTest01;
		}
	}
}
