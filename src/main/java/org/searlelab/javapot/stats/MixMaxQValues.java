package org.searlelab.javapot.stats;

import org.searlelab.javapot.util.StableIntSort;

/**
 * MixMaxQValues computes Percolator-style mix-max q-values for separate target/decoy searches.
 */
public final class MixMaxQValues {
	private MixMaxQValues() {
	}

	/**
	 * Computes mix-max q-values with default null target win probability.
	 */
	public static Result compute(double[] scoresInput, boolean[] targetsInput, boolean desc, long seed) {
		return compute(scoresInput, targetsInput, desc, seed, false, 0.5);
	}

	/**
	 * Computes mix-max q-values and estimated pi0.
	 */
	public static Result compute(
		double[] scoresInput,
		boolean[] targetsInput,
		boolean desc,
		long seed,
		boolean skipDecoysPlusOne,
		double nullTargetWinProb
	) {
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

		double pi0 = Pi0Estimator.estimateFromSorted(sortedScores, sortedTargets, seed);
		double[] sortedQ = computeSorted(sortedScores, sortedTargets, pi0, skipDecoysPlusOne, nullTargetWinProb);

		int[] inv = new int[n];
		for (int i = 0; i < n; i++) {
			inv[order[i]] = i;
		}
		double[] q = new double[n];
		for (int i = 0; i < n; i++) {
			q[i] = sortedQ[inv[i]];
		}
		return new Result(q, pi0);
	}

	private static double[] computeSorted(
		double[] sortedScores,
		boolean[] sortedTargets,
		double pi0,
		boolean skipDecoysPlusOne,
		double nullTargetWinProb
	) {
		int n = sortedScores.length;
		if (n == 0) {
			return new double[0];
		}
		double[] q = new double[n];
		double decoyFactor = nullTargetWinProb / (1.0 - nullTargetWinProb);
		Counts counts = pi0 < 1.0 ? getMixMaxCounts(sortedScores, sortedTargets) : Counts.empty();

		double estPxLtZj = 0.0;
		double expectedIncorrectTargets = 0.0;
		double fdr;

		int nDecoysAtThreshold = skipDecoysPlusOne ? 0 : 1;
		int nTargetsAtThreshold = 0;
		int decoyQueue = 0;
		int targetQueue = 0;
		int qPos = 0;

		for (int i = 0; i < n; i++) {
			if (sortedTargets[i]) {
				nTargetsAtThreshold++;
				targetQueue++;
			} else {
				nDecoysAtThreshold++;
				decoyQueue++;
			}
			boolean tieBoundary = (i + 1 == n) || Double.compare(sortedScores[i], sortedScores[i + 1]) != 0;
			if (!tieBoundary) {
				continue;
			}
			if (pi0 < 1.0 && decoyQueue > 0 && counts.size() > 0) {
				int j = counts.size() - (nDecoysAtThreshold - 1);
				if (j < 0) {
					j = 0;
				} else if (j >= counts.size()) {
					j = counts.size() - 1;
				}
				double cntW = counts.wAt(j);
				double cntZ = counts.zAt(j);
				double denom = (1.0 - pi0) * cntZ;
				if (denom == 0.0) {
					estPxLtZj = 0.0;
				} else {
					estPxLtZj = (cntW - pi0 * cntZ) / denom;
					estPxLtZj = clamp01(estPxLtZj);
				}
				expectedIncorrectTargets += decoyQueue * estPxLtZj * (1.0 - pi0);
			}

			targetQueue += decoyQueue;
			fdr = ((nDecoysAtThreshold * pi0) + expectedIncorrectTargets) / Math.max(1.0, nTargetsAtThreshold);
			fdr = clamp01(fdr * decoyFactor);

			for (int k = 0; k < targetQueue; k++) {
				q[qPos++] = fdr;
			}
			targetQueue = 0;
			decoyQueue = 0;
		}

		double runningMin = Double.POSITIVE_INFINITY;
		for (int i = q.length - 1; i >= 0; i--) {
			if (q[i] < runningMin) {
				runningMin = q[i];
			}
			q[i] = runningMin;
		}
		return q;
	}

	private static Counts getMixMaxCounts(double[] sortedScores, boolean[] sortedTargets) {
		int decoyCount = 0;
		for (boolean target : sortedTargets) {
			if (!target) {
				decoyCount++;
			}
		}
		if (decoyCount == 0) {
			return Counts.empty();
		}
		double[] hWLeZ = new double[decoyCount];
		double[] hZLeZ = new double[decoyCount];

		int cntZ = 0;
		int cntW = 0;
		int queue = 0;
		int pos = 0;
		for (int i = sortedScores.length - 1; i >= 0; i--) {
			if (sortedTargets[i]) {
				cntW++;
			} else {
				cntZ++;
				queue++;
			}
			boolean tieBoundary = (i == 0) || Double.compare(sortedScores[i], sortedScores[i - 1]) != 0;
			if (!tieBoundary) {
				continue;
			}
			for (int j = 0; j < queue; j++) {
				hWLeZ[pos] = cntW;
				hZLeZ[pos] = cntZ;
				pos++;
			}
			queue = 0;
		}
		return new Counts(hWLeZ, hZLeZ, pos);
	}

	private static double clamp01(double value) {
		if (value < 0.0) {
			return 0.0;
		}
		if (value > 1.0) {
			return 1.0;
		}
		return value;
	}

	/**
	 * Result carries q-values and pi0 estimated for the supplied score vector.
	 */
	public record Result(double[] qValues, double pi0) {
	}

	private record Counts(double[] hWLeZ, double[] hZLeZ, int size) {
		static Counts empty() {
			return new Counts(new double[0], new double[0], 0);
		}

		double wAt(int idx) {
			return hWLeZ[idx];
		}

		double zAt(int idx) {
			return hZLeZ[idx];
		}
	}
}
