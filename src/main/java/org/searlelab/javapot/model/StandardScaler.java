package org.searlelab.javapot.model;

/**
 * StandardScaler performs column-wise z-score normalization for feature matrices.
 * It can be fit once on training data and reused to transform held-out folds.
 */
public final class StandardScaler {
	private double[] means;
	private double[] scales;
	private boolean fitted;

	/**
	 * Fits scaling parameters and returns a normalized copy of the input matrix.
	 */
	public double[][] fitTransform(double[][] x) {
		fit(x);
		return transform(x);
	}

	/**
	 * Learns per-column means and standard deviations.
	 */
	public void fit(double[][] x) {
		if (x.length == 0) {
			throw new IllegalArgumentException("Cannot scale empty matrix");
		}
		int cols = x[0].length;
		means = new double[cols];
		scales = new double[cols];
		for (double[] row : x) {
			for (int j = 0; j < cols; j++) {
				means[j] += row[j];
			}
		}
		for (int j = 0; j < cols; j++) {
			means[j] /= x.length;
		}
		for (double[] row : x) {
			for (int j = 0; j < cols; j++) {
				double d = row[j] - means[j];
				scales[j] += d * d;
			}
		}
		for (int j = 0; j < cols; j++) {
			scales[j] = Math.sqrt(scales[j] / x.length);
			if (scales[j] == 0.0) {
				scales[j] = 1.0;
			}
		}
		fitted = true;
	}

	/**
	 * Applies learned scaling parameters to a matrix.
	 */
	public double[][] transform(double[][] x) {
		if (!fitted) {
			throw new IllegalStateException("Scaler is not fitted");
		}
		double[][] out = new double[x.length][means.length];
		for (int i = 0; i < x.length; i++) {
			for (int j = 0; j < means.length; j++) {
				out[i][j] = (x[i][j] - means[j]) / scales[j];
			}
		}
		return out;
	}

	/**
	 * Returns fitted column means.
	 */
	public double[] means() {
		return means.clone();
	}

	/**
	 * Returns fitted column scales.
	 */
	public double[] scales() {
		return scales.clone();
	}

	/**
	 * Reconstructs a fitted scaler from persisted means and scales.
	 */
	public static StandardScaler from(double[] means, double[] scales) {
		StandardScaler scaler = new StandardScaler();
		scaler.means = means.clone();
		scaler.scales = scales.clone();
		scaler.fitted = true;
		return scaler;
	}
}
