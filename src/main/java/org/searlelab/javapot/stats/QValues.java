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
	 * Inputs are read-only for the duration of the call; callers must not mutate them concurrently.
	 */
	public static double[] tdc(
		double[] scoresInput,
		boolean[] targetsInput,
		boolean desc,
		boolean skipDecoysPlusOne
	) {
		return tdc(scoresInput, targetsInput, desc, skipDecoysPlusOne, null);
	}

	/**
	 * Computes q-values with optional caller-owned scratch for repeated, equally sized inputs.
	 * The returned q-values are independent of the workspace and remain valid after reuse.
	 */
	public static double[] tdc(
		double[] scoresInput,
		boolean[] targetsInput,
		boolean desc,
		boolean skipDecoysPlusOne,
		TdcWorkspace workspace
	) {
		if (scoresInput.length != targetsInput.length) {
			throw new IllegalArgumentException("scores and targets must be the same length");
		}
		int n = scoresInput.length;
		if (workspace != null && workspace.scratch.length != n) {
			throw new IllegalArgumentException("workspace must match the input length");
		}
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
		// Both inputs are read-only; scratch belongs to this call or its enclosing invocation.
		TdcWorkspace buffers = workspace == null ? new TdcWorkspace(n) : workspace;
		double[] scores = scoresInput;
		boolean[] targets = targetsInput;
		int[] scratch = buffers.scratch;
		int[] srtIdx = buffers.sorted;

		StableIntSort.sortIndicesInto(srtIdx, (a, b) -> {
			int scoreCmp = desc ? Double.compare(scores[b], scores[a]) : Double.compare(scores[a], scores[b]);
			if (scoreCmp != 0) {
				return scoreCmp;
			}
			return Boolean.compare(targets[a], targets[b]);
		}, scratch);

		int cumTargets = 0;
		int cumDecoys = 0;
		double[] fdr = buffers.fdr;
		for (int i = 0; i < n; i++) {
			if (targets[srtIdx[i]]) {
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

		int[] sort2 = buffers.reverse;
		StableIntSort.sortIndicesInto(sort2, (a, b) -> {
			int scoreCmp = desc ? Double.compare(scores[srtIdx[a]], scores[srtIdx[b]]) : Double.compare(scores[srtIdx[b]], scores[srtIdx[a]]);
			if (scoreCmp != 0) {
				return scoreCmp;
			}
			return Double.compare(fdr[a], fdr[b]);
		}, scratch);

		double[] tmp = buffers.minimum;
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

		double[] qvals = new double[n];
		for (int i = 0; i < n; i++) {
			qvals[srtIdx[i]] = tmp[n - 1 - i];
		}
		return qvals;
	}

	/**
	 * Fixed-size scratch owned by one feature-selection invocation or fold, never a shared cache.
	 * Do not use the same workspace concurrently. Discard it when the enclosing scan finishes.
	 */
	public static final class TdcWorkspace {
		private final int[] scratch;
		private final int[] sorted;
		private final int[] reverse;
		private final double[] fdr;
		private final double[] minimum;

		public TdcWorkspace(int size) {
			scratch = new int[size];
			sorted = new int[size];
			reverse = new int[size];
			fdr = new double[size];
			minimum = new double[size];
		}
	}
}
