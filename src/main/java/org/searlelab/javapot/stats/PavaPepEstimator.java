package org.searlelab.javapot.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * PavaPepEstimator performs non-decreasing isotonic regression using pool-adjacent-violators.
 */
public final class PavaPepEstimator {
	private PavaPepEstimator() {
	}

	/**
	 * Fits isotonic values on y only.
	 */
	public static double[] fitY(double[] values, double minValue, double maxValue) {
		if (values.length == 0) {
			return new double[0];
		}
		List<Block> stack = new ArrayList<>(values.length);
		for (double value : values) {
			stack.add(new Block(value, 1));
			while (stack.size() > 1) {
				int n = stack.size();
				Block right = stack.get(n - 1);
				Block left = stack.get(n - 2);
				if (left.avg <= right.avg) {
					break;
				}
				Block merged = new Block(left.sum + right.sum, left.count + right.count);
				stack.remove(n - 1);
				stack.set(n - 2, merged);
			}
		}

		double[] out = new double[values.length];
		int pos = 0;
		for (Block block : stack) {
			double v = clamp(block.avg, minValue, maxValue);
			for (int i = 0; i < block.count; i++) {
				out[pos++] = v;
			}
		}
		return out;
	}

	/**
	 * Fits isotonic values for x/y pairs. PAVA does not use x and matches Percolator's fallback behavior.
	 */
	public static double[] fitXY(double[] x, double[] y, double minValue, double maxValue) {
		return fitY(y, minValue, maxValue);
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

	private record Block(double sum, int count, double avg) {
		Block(double sum, int count) {
			this(sum, count, sum / count);
		}
	}
}
