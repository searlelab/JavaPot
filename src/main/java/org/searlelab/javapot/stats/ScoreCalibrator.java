package org.searlelab.javapot.stats;

import java.util.Arrays;

/**
 * ScoreCalibrator rescales raw model scores using the accepted-target cutoff and decoy median.
 * This mirrors the Percolator/Mokapot-style post-model score normalization step.
 */
public final class ScoreCalibrator {
	private ScoreCalibrator() {
	}

	public static double[] calibrate(double[] scores, boolean[] targets, double evalFdr) {
		int[] labels = LabelUpdater.updateLabels(scores, targets, evalFdr, true);
		double targetScore = Double.POSITIVE_INFINITY;
		int positiveCount = 0;
		int decoyCount = 0;
		double[] decoys = new double[scores.length];
		for (int i = 0; i < scores.length; i++) {
			if (labels[i] == 1) {
				positiveCount++;
				targetScore = Math.min(targetScore, scores[i]);
			} else if (labels[i] == -1) {
				decoys[decoyCount++] = scores[i];
			}
		}
		if (positiveCount == 0) {
			throw new RuntimeException("No target PSMs were below the eval_fdr threshold.");
		}
		if (decoyCount == 0) {
			throw new RuntimeException("No decoy PSMs were available for calibration.");
		}
		double[] usedDecoys = Arrays.copyOf(decoys, decoyCount);
		Arrays.sort(usedDecoys);
		double decoyMedian;
		if (usedDecoys.length % 2 == 0) {
			int upper = usedDecoys.length / 2;
			decoyMedian = (usedDecoys[upper - 1] + usedDecoys[upper]) / 2.0;
		} else {
			decoyMedian = usedDecoys[usedDecoys.length / 2];
		}
		double denominator = targetScore - decoyMedian;
		double[] out = new double[scores.length];
		for (int i = 0; i < scores.length; i++) {
			out[i] = (scores[i] - targetScore) / denominator;
		}
		return out;
	}
}
