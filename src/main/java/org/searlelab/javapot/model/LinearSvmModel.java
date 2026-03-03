package org.searlelab.javapot.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * LinearSvmModel is a serializable linear classifier used for the Percolator main training path.
 * It supports fitting with class-weighted squared hinge loss and scoring new feature matrices.
 */
public final class LinearSvmModel implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private final double[] weights;
	private final double bias;
	private final double classWeightNeg;
	private final double classWeightPos;

	public LinearSvmModel(double[] weights, double bias, double classWeightNeg, double classWeightPos) {
		this.weights = Arrays.copyOf(weights, weights.length);
		this.bias = bias;
		this.classWeightNeg = classWeightNeg;
		this.classWeightPos = classWeightPos;
	}

	public static LinearSvmModel fit(
		double[][] x,
		int[] y,
		double classWeightNeg,
		double classWeightPos,
		int maxEpochs
	) {
		if (x.length != y.length) {
			throw new IllegalArgumentException("X/Y length mismatch");
		}
		int n = x.length;
		int p = x[0].length;
		double[] w = new double[p];
		double b = 0.0;
		double c = 1.0;
		int dim = p + 1;
		for (int epoch = 0; epoch < maxEpochs; epoch++) {
			double[] grad = new double[dim];
			double[][] h = new double[dim][dim];
			for (int j = 0; j < p; j++) {
				grad[j] = w[j];
				h[j][j] = 1.0;
			}

			for (int i = 0; i < n; i++) {
				double yi = y[i];
				double weight = yi > 0 ? classWeightPos : classWeightNeg;
				double margin = 1.0 - yi * (dot(w, x[i]) + b);
				if (margin <= 0) {
					continue;
				}
				double alpha = 2.0 * c * weight;
				double g = -alpha * margin * yi;
				for (int j = 0; j < p; j++) {
					grad[j] += g * x[i][j];
				}
				grad[p] += g;

				for (int j = 0; j < p; j++) {
					for (int k = 0; k < p; k++) {
						h[j][k] += alpha * x[i][j] * x[i][k];
					}
					h[j][p] += alpha * x[i][j];
					h[p][j] += alpha * x[i][j];
				}
				h[p][p] += alpha;
			}

			double[] delta = solveLinearSystem(h, grad);
			double norm = 0.0;
			for (double v : delta) {
				norm += v * v;
			}
			norm = Math.sqrt(norm);
			if (norm < 1e-7) {
				break;
			}

			double oldObjective = objective(x, y, w, b, c, classWeightNeg, classWeightPos);
			double step = 1.0;
			boolean accepted = false;
			while (step > 1e-7) {
				double[] nextW = Arrays.copyOf(w, p);
				for (int j = 0; j < p; j++) {
					nextW[j] -= step * delta[j];
				}
				double nextB = b - step * delta[p];
				double newObjective = objective(x, y, nextW, nextB, c, classWeightNeg, classWeightPos);
				if (newObjective <= oldObjective) {
					System.arraycopy(nextW, 0, w, 0, p);
					b = nextB;
					accepted = true;
					break;
				}
				step *= 0.5;
			}
			if (!accepted) {
				break;
			}
		}
		return new LinearSvmModel(w, b, classWeightNeg, classWeightPos);
	}

	public double[] decisionFunction(double[][] x) {
		double[] out = new double[x.length];
		for (int i = 0; i < x.length; i++) {
			out[i] = dot(weights, x[i]) + bias;
		}
		return out;
	}

	public int[] predictClasses(double[][] x) {
		double[] scores = decisionFunction(x);
		int[] out = new int[scores.length];
		for (int i = 0; i < scores.length; i++) {
			out[i] = scores[i] >= 0 ? 1 : 0;
		}
		return out;
	}

	public double[] weights() {
		return Arrays.copyOf(weights, weights.length);
	}

	public double bias() {
		return bias;
	}

	public double classWeightNeg() {
		return classWeightNeg;
	}

	public double classWeightPos() {
		return classWeightPos;
	}

	private static double dot(double[] a, double[] b) {
		double out = 0.0;
		for (int i = 0; i < a.length; i++) {
			out += a[i] * b[i];
		}
		return out;
	}

	private static double objective(
		double[][] x,
		int[] y,
		double[] w,
		double b,
		double c,
		double classWeightNeg,
		double classWeightPos
	) {
		double reg = 0.0;
		for (double wi : w) {
			reg += wi * wi;
		}
		reg *= 0.5;
		double loss = 0.0;
		for (int i = 0; i < x.length; i++) {
			double yi = y[i];
			double weight = yi > 0 ? classWeightPos : classWeightNeg;
			double margin = 1.0 - yi * (dot(w, x[i]) + b);
			if (margin > 0) {
				loss += weight * margin * margin;
			}
		}
		return reg + c * loss;
	}

	private static double[] solveLinearSystem(double[][] a, double[] b) {
		int n = b.length;
		double[][] m = new double[n][n + 1];
		for (int i = 0; i < n; i++) {
			System.arraycopy(a[i], 0, m[i], 0, n);
			m[i][n] = b[i];
		}

		for (int col = 0; col < n; col++) {
			int pivot = col;
			double maxAbs = Math.abs(m[col][col]);
			for (int r = col + 1; r < n; r++) {
				double v = Math.abs(m[r][col]);
				if (v > maxAbs) {
					maxAbs = v;
					pivot = r;
				}
			}
			if (maxAbs < 1e-12) {
				m[col][col] += 1e-8;
			}
			if (pivot != col) {
				double[] tmp = m[col];
				m[col] = m[pivot];
				m[pivot] = tmp;
			}

			double div = m[col][col];
			if (Math.abs(div) < 1e-12) {
				div = div < 0 ? -1e-12 : 1e-12;
			}
			for (int j = col; j <= n; j++) {
				m[col][j] /= div;
			}

			for (int r = 0; r < n; r++) {
				if (r == col) {
					continue;
				}
				double factor = m[r][col];
				if (factor == 0.0) {
					continue;
				}
				for (int j = col; j <= n; j++) {
					m[r][j] -= factor * m[col][j];
				}
			}
		}

		double[] xOut = new double[n];
		for (int i = 0; i < n; i++) {
			xOut[i] = m[i][n];
		}
		return xOut;
	}
}
