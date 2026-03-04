package org.searlelab.javapot.stats;

import java.util.Arrays;

import org.searlelab.javapot.util.StableIntSort;

/**
 * PepEstimator computes Percolator-style PEP values from sorted target/decoy labels using isotonic smoothing.
 */
public final class PepEstimator {
	private static final double EPSILON = 1e-20;

	private PepEstimator() {
	}

	/**
	 * Estimates PEP values with I-spline smoothing and score-aware interpolation.
	 */
	public static Result tdcToPep(double[] scoresInput, boolean[] targetsInput) {
		return tdcToPep(scoresInput, targetsInput, true, true);
	}

	/**
	 * Estimates PEP values from target/decoy labels with configurable smoothing.
	 */
	public static Result tdcToPep(
		double[] scoresInput,
		boolean[] targetsInput,
		boolean useSpline,
		boolean useInterpolation
	) {
		if (scoresInput.length != targetsInput.length) {
			throw new IllegalArgumentException("scores and targets must be the same length");
		}
		int n = scoresInput.length;
		if (n == 0) {
			return new Result(new double[0], false);
		}

		int[] order = scoreOrder(scoresInput);
		double[] sortedScores = new double[n];
		boolean[] sortedTargets = new boolean[n];
		for (int i = 0; i < n; i++) {
			sortedScores[i] = scoresInput[order[i]];
			sortedTargets[i] = targetsInput[order[i]];
		}

		double[] y = new double[n + 1];
		y[0] = 0.5;
		for (int i = 0; i < n; i++) {
			y[i + 1] = sortedTargets[i] ? 0.0 : 1.0;
		}

		double[] x = null;
		if (useInterpolation) {
			x = new double[n + 1];
			x[0] = sortedScores[0];
			System.arraycopy(sortedScores, 0, x, 1, n);
		}

		boolean usedFallback = false;
		double[] decoyRate;
		if (useSpline) {
			decoyRate = useInterpolation
				? IsplinePepEstimator.fitXY(x, y, EPSILON, 1.0 - EPSILON)
				: IsplinePepEstimator.fitY(y, EPSILON, 1.0 - EPSILON);
			if (!isValid(decoyRate)) {
				usedFallback = true;
				decoyRate = useInterpolation
					? PavaPepEstimator.fitXY(x, y, EPSILON, 1.0 - EPSILON)
					: PavaPepEstimator.fitY(y, EPSILON, 1.0 - EPSILON);
			}
		} else {
			decoyRate = useInterpolation
				? PavaPepEstimator.fitXY(x, y, EPSILON, 1.0 - EPSILON)
				: PavaPepEstimator.fitY(y, EPSILON, 1.0 - EPSILON);
		}

		double[] pepSorted = new double[n];
		for (int i = 0; i < n; i++) {
			double dp = clip(decoyRate[i + 1], EPSILON, 1.0 - EPSILON);
			double pep = dp / (1.0 - dp);
			pepSorted[i] = clip(pep, 0.0, 1.0);
		}
		return reorderToInput(pepSorted, order, usedFallback);
	}

	/**
	 * Estimates TDC PEP values from score-ranked q-values using Percolator-style q-to-local-error conversion.
	 */
	public static Result tdcQvalsToPep(double[] scoresInput, boolean[] targetsInput, double[] qvalsInput) {
		return tdcQvalsToPep(scoresInput, targetsInput, qvalsInput, true, true);
	}

	/**
	 * Estimates TDC PEP values from score-ranked q-values using Percolator-style q-to-local-error conversion.
	 */
	public static Result tdcQvalsToPep(
		double[] scoresInput,
		boolean[] targetsInput,
		double[] qvalsInput,
		boolean useSpline,
		boolean useInterpolation
	) {
		if (scoresInput.length != targetsInput.length || scoresInput.length != qvalsInput.length) {
			throw new IllegalArgumentException("scores, targets, and q-values must be the same length");
		}
		int n = scoresInput.length;
		if (n == 0) {
			return new Result(new double[0], false);
		}

		int[] order = scoreOrder(scoresInput);
		double[] sortedScores = new double[n];
		boolean[] sortedTargets = new boolean[n];
		double[] sortedQ = new double[n];
		for (int i = 0; i < n; i++) {
			sortedScores[i] = scoresInput[order[i]];
			sortedTargets[i] = targetsInput[order[i]];
			sortedQ[i] = clip(qvalsInput[order[i]], 0.0, 1.0);
		}

		int targetCount = 0;
		for (boolean target : sortedTargets) {
			if (target) {
				targetCount++;
			}
		}
		if (targetCount == 0) {
			double[] pep = new double[n];
			Arrays.fill(pep, 1.0);
			return reorderToInput(pep, order, false);
		}

		double[] targetQ = new double[targetCount];
		double[] targetScores = new double[targetCount];
		int pos = 0;
		for (int i = 0; i < n; i++) {
			if (sortedTargets[i]) {
				targetQ[pos] = sortedQ[i];
				targetScores[pos] = sortedScores[i];
				pos++;
			}
		}
		for (int i = 1; i < targetQ.length; i++) {
			if (targetQ[i] < targetQ[i - 1]) {
				targetQ[i] = targetQ[i - 1];
			}
		}

		RawPepResult targetPepResult = qValuesToPep(targetQ, targetScores, useSpline, useInterpolation);
		double[] targetPep = targetPepResult.pep();

		double[] targetQExt = Arrays.copyOf(targetQ, targetQ.length + 1);
		targetQExt[targetQ.length] = 1.0;
		double[] targetPepExt = Arrays.copyOf(targetPep, targetPep.length + 1);
		targetPepExt[targetPep.length] = 1.0;

		double[] pepSorted = new double[n];
		double leftQ = 0.0;
		double leftPep = 0.0;
		int targetCursor = 0;
		for (int i = 0; i < n; i++) {
			if (sortedTargets[i]) {
				double pep = targetPep[targetCursor];
				pepSorted[i] = clip(pep, 0.0, 1.0);
				leftQ = targetQ[targetCursor];
				leftPep = pepSorted[i];
				targetCursor++;
			} else {
				double rightQ = targetQExt[targetCursor];
				double rightPep = targetPepExt[targetCursor];
				double pep = interpolate(sortedQ[i], leftQ, rightQ, leftPep, rightPep);
				pepSorted[i] = clip(pep, 0.0, 1.0);
			}
		}

		return reorderToInput(pepSorted, order, targetPepResult.usedFallback());
	}

	private static RawPepResult qValuesToPep(
		double[] qValues,
		double[] scores,
		boolean useSpline,
		boolean useInterpolation
	) {
		double[] qn = new double[qValues.length];
		for (int i = 0; i < qValues.length; i++) {
			qn[i] = qValues[i] * (i + 1.0);
		}
		double[] rawPep = new double[qValues.length];
		rawPep[0] = qn[0];
		for (int i = 1; i < qValues.length; i++) {
			rawPep[i] = qn[i] - qn[i - 1];
		}

		boolean usedFallback = false;
		double[] smoothed;
		if (useSpline) {
			smoothed = useInterpolation
				? IsplinePepEstimator.fitXY(scores, rawPep, EPSILON, 1.0 - EPSILON)
				: IsplinePepEstimator.fitY(rawPep, EPSILON, 1.0 - EPSILON);
			if (!isValid(smoothed)) {
				usedFallback = true;
				smoothed = useInterpolation
					? PavaPepEstimator.fitXY(scores, rawPep, EPSILON, 1.0 - EPSILON)
					: PavaPepEstimator.fitY(rawPep, EPSILON, 1.0 - EPSILON);
			}
		} else {
			smoothed = useInterpolation
				? PavaPepEstimator.fitXY(scores, rawPep, EPSILON, 1.0 - EPSILON)
				: PavaPepEstimator.fitY(rawPep, EPSILON, 1.0 - EPSILON);
		}

		double[] clipped = new double[smoothed.length];
		for (int i = 0; i < smoothed.length; i++) {
			clipped[i] = clip(smoothed[i], 0.0, 1.0);
		}
		return new RawPepResult(clipped, usedFallback);
	}

	private static Result reorderToInput(double[] sortedValues, int[] order, boolean usedFallback) {
		int n = sortedValues.length;
		int[] inv = new int[n];
		for (int i = 0; i < n; i++) {
			inv[order[i]] = i;
		}
		double[] out = new double[n];
		for (int i = 0; i < n; i++) {
			out[i] = sortedValues[inv[i]];
		}
		return new Result(out, usedFallback);
	}

	private static int[] scoreOrder(double[] scoresInput) {
		return StableIntSort.sortIndices(scoresInput.length, (a, b) -> {
			int scoreCmp = Double.compare(scoresInput[b], scoresInput[a]);
			if (scoreCmp != 0) {
				return scoreCmp;
			}
			return Integer.compare(a, b);
		});
	}

	private static double interpolate(double qValue, double q1, double q2, double pep1, double pep2) {
		double denominator = q2 - q1;
		if (!Double.isFinite(denominator) || Math.abs(denominator) < 1e-12) {
			return pep2;
		}
		double pep = pep1 + (qValue - q1) * (pep2 - pep1) / denominator;
		if (!Double.isFinite(pep)) {
			return pep2;
		}
		return pep;
	}

	private static boolean isValid(double[] values) {
		if (values == null || values.length == 0) {
			return false;
		}
		for (double v : values) {
			if (!Double.isFinite(v)) {
				return false;
			}
		}
		return true;
	}

	private static double clip(double value, double minValue, double maxValue) {
		if (value < minValue) {
			return minValue;
		}
		if (value > maxValue) {
			return maxValue;
		}
		return value;
	}

	private record RawPepResult(double[] pep, boolean usedFallback) {
	}

	/**
	 * Result stores PEP values and whether fallback from I-spline to PAVA occurred.
	 */
	public record Result(double[] pepValues, boolean usedFallback) {
	}
}
