package org.searlelab.javapot.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * IsplinePepEstimator fits a smoothed isotonic model using cubic I-spline basis functions.
 */
public final class IsplinePepEstimator {
	private static final int DEFAULT_NUM_BINS = 10_000;
	private static final double DEFAULT_LAMBDA = 1e-6;
	private static final int DEFAULT_MAX_KNOTS = 50;
	private static final double DEFAULT_SKEW_FACTOR = 0.75;

	private IsplinePepEstimator() {
	}

	/**
	 * Fits smoothed isotonic values using positional x coordinates.
	 */
	public static double[] fitY(double[] y, double minValue, double maxValue) {
		double[] x = new double[y.length];
		for (int i = 0; i < y.length; i++) {
			x[i] = i;
		}
		return fitXY(x, y, minValue, maxValue);
	}

	/**
	 * Fits smoothed isotonic values using the provided x/y coordinates.
	 */
	public static double[] fitXY(double[] x, double[] y, double minValue, double maxValue) {
		if (x.length != y.length) {
			throw new IllegalArgumentException("x and y must have the same length");
		}
		if (x.length == 0) {
			return new double[0];
		}

		BinnedData data = binData(x, y, DEFAULT_NUM_BINS);
		int numKnots = Math.min(DEFAULT_MAX_KNOTS, Math.max(2, (int) Math.sqrt(Math.max(1, data.x.length))));
		double[] knots = computeAdaptiveKnots(data.x, numKnots, DEFAULT_SKEW_FACTOR);
		double[] coeffs = fitSpline(data, knots, DEFAULT_LAMBDA);
		if (coeffs == null) {
			return null;
		}

		double[] smoothed = new double[x.length];
		for (int i = 0; i < x.length; i++) {
			double pred = coeffs[coeffs.length - 1];
			for (int j = 0; j < knots.length - 1; j++) {
				pred += coeffs[j] * cubicISpline(x[i], knots[j], knots[j + 1]);
			}
			smoothed[i] = clamp(pred, minValue, maxValue);
		}
		return PavaPepEstimator.fitXY(x, smoothed, minValue, maxValue);
	}

	private static BinnedData binData(double[] x, double[] y, int maxBins) {
		if (x.length == 0 || maxBins <= 0) {
			return new BinnedData(new double[0], new double[0], new double[0]);
		}
		List<Double> xBinned = new ArrayList<>();
		List<Double> yBinned = new ArrayList<>();
		List<Double> weights = new ArrayList<>();

		double targetBinSize = x.length / (double) maxBins;
		if (targetBinSize < 1.0) {
			targetBinSize = 1.0;
		}
		double nextThreshold = targetBinSize;
		int binStart = 0;
		for (int i = 0; i < x.length; i++) {
			boolean shouldCut = (i + 1) >= nextThreshold || i == x.length - 1;
			if (!shouldCut) {
				continue;
			}
			int binEnd = i + 1;
			int binSize = binEnd - binStart;
			double xSum = 0.0;
			double ySum = 0.0;
			for (int j = binStart; j < binEnd; j++) {
				xSum += x[j];
				ySum += y[j];
			}
			xBinned.add(xSum / binSize);
			yBinned.add(ySum / binSize);
			weights.add((double) binSize);

			binStart = binEnd;
			nextThreshold += targetBinSize;
		}
		return new BinnedData(toArray(xBinned), toArray(yBinned), toArray(weights));
	}

	private static double[] computeAdaptiveKnots(double[] x, int numKnots, double skewFactor) {
		if (x.length == 0) {
			return new double[0];
		}
		double[] knots = new double[numKnots + 1];
		knots[0] = x[0];
		for (int i = 1; i < numKnots; i++) {
			double q = 1.0 - Math.pow(1.0 - (i / (double) numKnots), skewFactor);
			int idx = (int) (q * (x.length - 1));
			if (idx < 0) {
				idx = 0;
			} else if (idx >= x.length) {
				idx = x.length - 1;
			}
			knots[i] = x[idx];
		}
		knots[numKnots] = x[x.length - 1];
		return knots;
	}

	private static double[] fitSpline(BinnedData data, double[] knots, double lambda) {
		if (data.x.length == 0 || knots.length < 2) {
			return null;
		}
		int n = data.x.length;
		int k = knots.length;

		double[][] a = new double[k][k];
		double[] b = new double[k];

		for (int i = 0; i < n; i++) {
			double[] row = new double[k];
			for (int j = 0; j < k - 1; j++) {
				row[j] = cubicISpline(data.x[i], knots[j], knots[j + 1]);
			}
			row[k - 1] = 1.0;
			double w = data.weights[i];
			double y = data.y[i];
			for (int col = 0; col < k; col++) {
				b[col] += w * row[col] * y;
				for (int col2 = 0; col2 < k; col2++) {
					a[col][col2] += w * row[col] * row[col2];
				}
			}
		}
		for (int i = 0; i < k; i++) {
			a[i][i] += lambda;
		}
		return solveLinearSystem(a, b);
	}

	private static double cubicISpline(double x, double left, double right) {
		if (x < left) {
			return 0.0;
		}
		if (right <= left) {
			return x >= right ? 1.0 : 0.0;
		}
		if (x >= right) {
			return 1.0;
		}
		double u = (x - left) / (right - left);
		return (3.0 * u * u) - (2.0 * u * u * u);
	}

	private static double[] solveLinearSystem(double[][] aIn, double[] bIn) {
		int n = bIn.length;
		double[][] a = new double[n][n];
		double[] b = new double[n];
		for (int i = 0; i < n; i++) {
			System.arraycopy(aIn[i], 0, a[i], 0, n);
			b[i] = bIn[i];
		}

		for (int col = 0; col < n; col++) {
			int pivot = col;
			double max = Math.abs(a[col][col]);
			for (int row = col + 1; row < n; row++) {
				double v = Math.abs(a[row][col]);
				if (v > max) {
					max = v;
					pivot = row;
				}
			}
			if (max < 1e-12) {
				return null;
			}
			if (pivot != col) {
				double[] tmpRow = a[col];
				a[col] = a[pivot];
				a[pivot] = tmpRow;
				double tmpB = b[col];
				b[col] = b[pivot];
				b[pivot] = tmpB;
			}

			for (int row = col + 1; row < n; row++) {
				double factor = a[row][col] / a[col][col];
				if (factor == 0.0) {
					continue;
				}
				for (int c = col; c < n; c++) {
					a[row][c] -= factor * a[col][c];
				}
				b[row] -= factor * b[col];
			}
		}

		double[] x = new double[n];
		for (int row = n - 1; row >= 0; row--) {
			double sum = b[row];
			for (int col = row + 1; col < n; col++) {
				sum -= a[row][col] * x[col];
			}
			double diag = a[row][row];
			if (Math.abs(diag) < 1e-12) {
				return null;
			}
			x[row] = sum / diag;
		}
		return x;
	}

	private static double clamp(double value, double minValue, double maxValue) {
		if (value < minValue) {
			return minValue;
		}
		if (value > maxValue) {
			return maxValue;
		}
		return value;
	}

	private static double[] toArray(List<Double> values) {
		double[] out = new double[values.size()];
		for (int i = 0; i < values.size(); i++) {
			out[i] = values.get(i);
		}
		return out;
	}

	private record BinnedData(double[] x, double[] y, double[] weights) {
	}
}
