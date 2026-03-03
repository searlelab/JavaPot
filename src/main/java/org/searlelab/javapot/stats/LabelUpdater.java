package org.searlelab.javapot.stats;

/**
 * LabelUpdater converts score/q-value results into Percolator-style training labels.
 * Targets below the FDR threshold become positive, targets above become unlabeled, and decoys stay negative.
 */
public final class LabelUpdater {
	private LabelUpdater() {
	}

	/**
	 * Converts calibrated scores into Percolator labels (-1, 0, +1) at the requested FDR.
	 */
	public static int[] updateLabels(double[] scores, boolean[] targets, double evalFdr, boolean desc) {
		double[] qvals = QValues.tdc(scores, targets, desc);
		int[] out = new int[qvals.length];
		for (int i = 0; i < qvals.length; i++) {
			if (!targets[i]) {
				out[i] = -1;
			} else if (qvals[i] > evalFdr) {
				out[i] = 0;
			} else {
				out[i] = 1;
			}
		}
		return out;
	}
}
