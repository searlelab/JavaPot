package org.searlelab.javapot.stats;

import java.util.Arrays;

import org.searlelab.javapot.util.StableIntSort;

/**
 * QValues computes target-decoy competition q-values from scored PSMs.
 * The implementation keeps deterministic ordering and tie-handling behavior aligned with Mokapot expectations.
 */
public final class QValues {
	private QValues() {
	}

	/**
	 * Computes target-decoy-competition q-values for scored rows.
	 */
	public static double[] tdc(double[] scoresInput, boolean[] targetsInput, boolean desc) {
		return tdc(scoresInput, targetsInput, desc, false);
	}

	/**
	 * Computes target-decoy-competition q-values for scored rows.
	 * When {@code skipDecoysPlusOne} is true, the +1 pseudocount is omitted from the FDR numerator.
	 */
	public static double[] tdc(
		double[] scoresInput,
		boolean[] targetsInput,
		boolean desc,
		boolean skipDecoysPlusOne
	) {
		if (scoresInput.length != targetsInput.length) {
			throw new IllegalArgumentException("scores and targets must be the same length");
		}
		int n = scoresInput.length;
		int decoyTotal = 0;
		for (boolean target : targetsInput) {
			if (!target) {
				decoyTotal++;
			}
		}
		if (decoyTotal == 0) {
			double[] out = new double[n];
			Arrays.fill(out, skipDecoysPlusOne ? 0.0 : 1.0);
			return out;
		}
		double[] scores = Arrays.copyOf(scoresInput, n);
		boolean[] targets = Arrays.copyOf(targetsInput, n);

		int[] srtIdx = StableIntSort.sortIndices(n, (a, b) -> {
			int scoreCmp = desc ? Double.compare(scores[b], scores[a]) : Double.compare(scores[a], scores[b]);
			if (scoreCmp != 0) {
				return scoreCmp;
			}
			return Boolean.compare(targets[a], targets[b]);
		});

		double[] sortedScores = new double[n];
		boolean[] sortedTargets = new boolean[n];
		for (int i = 0; i < n; i++) {
			sortedScores[i] = scores[srtIdx[i]];
			sortedTargets[i] = targets[srtIdx[i]];
		}

		int cumTargets = 0;
		int cumDecoys = 0;
		double[] fdr = new double[n];
		for (int i = 0; i < n; i++) {
			if (sortedTargets[i]) {
				cumTargets++;
			} else {
				cumDecoys++;
			}
			if (cumTargets == 0) {
				fdr[i] = 1.0;
			} else {
				double decoyNumerator = skipDecoysPlusOne ? cumDecoys : (cumDecoys + 1.0);
				fdr[i] = Math.min(1.0, decoyNumerator / cumTargets);
			}
		}

		int[] sort2 = StableIntSort.sortIndices(n, (a, b) -> {
			int scoreCmp = desc ? Double.compare(sortedScores[a], sortedScores[b]) : Double.compare(sortedScores[b], sortedScores[a]);
			if (scoreCmp != 0) {
				return scoreCmp;
			}
			return Double.compare(fdr[a], fdr[b]);
		});

		double[] tmp = new double[n];
		double runningMin = Double.POSITIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			double v = fdr[sort2[i]];
			if (v < runningMin) {
				runningMin = v;
			}
			tmp[i] = runningMin;
		}

		double first = tmp[0];
		for (int i = 0; i < n; i++) {
			if (Double.compare(tmp[i], first) == 0) {
				tmp[i] = 1.0;
			}
		}

		double[] flipped = new double[n];
		for (int i = 0; i < n; i++) {
			flipped[i] = tmp[n - 1 - i];
		}

		int[] invSrt = new int[n];
		for (int i = 0; i < n; i++) {
			invSrt[srtIdx[i]] = i;
		}

		double[] qvals = new double[n];
		for (int i = 0; i < n; i++) {
			qvals[i] = flipped[invSrt[i]];
		}
		return qvals;
	}
}
