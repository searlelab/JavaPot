package org.searlelab.javapot.stats;

import java.util.Arrays;

import org.searlelab.javapot.util.DeterministicRandom;
import org.searlelab.javapot.util.StableIntSort;

/**
 * Pi0Estimator estimates the null fraction pi0 using Percolator/qvality-style Storey bootstrap selection.
 */
public final class Pi0Estimator {
	private static final int NUM_LAMBDA = 100;
	private static final double MAX_LAMBDA = 0.5;
	private static final int DEFAULT_NUM_BOOT = 100;
	private static final int MAX_BOOT_SAMPLE = 1000;

	private Pi0Estimator() {
	}

	/**
	 * Estimates pi0 from scored target/decoy rows.
	 */
	public static double estimate(double[] scoresInput, boolean[] targetsInput, boolean desc, long seed) {
		if (scoresInput.length != targetsInput.length) {
			throw new IllegalArgumentException("scores and targets must be the same length");
		}
		int n = scoresInput.length;
		int[] order = StableIntSort.sortIndices(n, (a, b) -> {
			int scoreCmp = desc ? Double.compare(scoresInput[b], scoresInput[a]) : Double.compare(scoresInput[a], scoresInput[b]);
			if (scoreCmp != 0) {
				return scoreCmp;
			}
			return Boolean.compare(targetsInput[b], targetsInput[a]);
		});
		double[] sortedScores = new double[n];
		boolean[] sortedTargets = new boolean[n];
		for (int i = 0; i < n; i++) {
			sortedScores[i] = scoresInput[order[i]];
			sortedTargets[i] = targetsInput[order[i]];
		}
		return estimateFromSorted(sortedScores, sortedTargets, seed);
	}

	/**
	 * Estimates pi0 from pre-sorted rows (best score first).
	 */
	public static double estimateFromSorted(double[] sortedScores, boolean[] sortedTargets, long seed) {
		double[] pValues = pValuesFromSorted(sortedScores, sortedTargets);
		return estimateFromPValues(pValues, seed, DEFAULT_NUM_BOOT);
	}

	/**
	 * Computes Percolator-style target p-values from sorted score/label pairs.
	 */
	public static double[] pValuesFromSorted(double[] sortedScores, boolean[] sortedTargets) {
		if (sortedScores.length != sortedTargets.length) {
			throw new IllegalArgumentException("scores and targets must be the same length");
		}
		int targetCount = 0;
		for (boolean target : sortedTargets) {
			if (target) {
				targetCount++;
			}
		}
		if (targetCount == 0) {
			return new double[0];
		}
		double[] p = new double[targetCount];
		int pPos = 0;
		int nDecoys = 1;
		int posSame = 0;
		int negSame = 0;
		for (int i = 0; i < sortedScores.length; i++) {
			if (sortedTargets[i]) {
				posSame++;
			} else {
				negSame++;
			}
			boolean tieBoundary = (i + 1 == sortedScores.length) || Double.compare(sortedScores[i], sortedScores[i + 1]) != 0;
			if (!tieBoundary) {
				continue;
			}
			for (int ix = 0; ix < posSame; ix++) {
				p[pPos++] = nDecoys + (negSame * (ix + 1.0)) / (posSame + 1.0);
			}
			nDecoys += negSame;
			negSame = 0;
			posSame = 0;
		}
		for (int i = 0; i < p.length; i++) {
			p[i] /= nDecoys;
		}
		return p;
	}

	private static double estimateFromPValues(double[] pValues, long seed, int numBoot) {
		if (pValues.length == 0) {
			return 1.0;
		}
		double[] lambdas = new double[NUM_LAMBDA + 1];
		double[] pi0s = new double[NUM_LAMBDA + 1];
		int count = 0;
		int n = pValues.length;
		for (int ix = 0; ix <= NUM_LAMBDA; ix++) {
			double lambda = ((ix + 1) / (double) NUM_LAMBDA) * MAX_LAMBDA;
			int start = lowerBound(pValues, lambda);
			double wl = n - start;
			double pi0 = wl / n / (1.0 - lambda);
			if (pi0 > 0.0) {
				lambdas[count] = lambda;
				pi0s[count] = pi0;
				count++;
			}
		}
		if (count == 0) {
			return 1.0;
		}
		double minPi0 = Double.POSITIVE_INFINITY;
		for (int i = 0; i < count; i++) {
			if (pi0s[i] < minPi0) {
				minPi0 = pi0s[i];
			}
		}

		double[] mse = new double[count];
		DeterministicRandom random = new DeterministicRandom(seed);
		for (int boot = 0; boot < numBoot; boot++) {
			double[] pBoot = bootstrapSorted(pValues, random);
			int nBoot = pBoot.length;
			for (int i = 0; i < count; i++) {
				int start = lowerBound(pBoot, lambdas[i]);
				double wl = nBoot - start;
				double pi0Boot = wl / nBoot / (1.0 - lambdas[i]);
				double diff = pi0Boot - minPi0;
				mse[i] += diff * diff;
			}
		}
		int minIx = 0;
		for (int i = 1; i < count; i++) {
			if (mse[i] < mse[minIx]) {
				minIx = i;
			}
		}
		double pi0 = pi0s[minIx];
		if (pi0 < 0.0) {
			return 0.0;
		}
		return Math.min(1.0, pi0);
	}

	private static double[] bootstrapSorted(double[] input, DeterministicRandom random) {
		int sampleSize = Math.min(input.length, MAX_BOOT_SAMPLE);
		double[] out = new double[sampleSize];
		for (int i = 0; i < sampleSize; i++) {
			int draw = random.nextInt(0, input.length);
			out[i] = input[draw];
		}
		Arrays.sort(out);
		return out;
	}

	private static int lowerBound(double[] sorted, double value) {
		int left = 0;
		int right = sorted.length;
		while (left < right) {
			int mid = left + ((right - left) >>> 1);
			if (sorted[mid] < value) {
				left = mid + 1;
			} else {
				right = mid;
			}
		}
		return left;
	}
}
