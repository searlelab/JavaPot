package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

class BoundedScoringTest {
	@Test
	void boundedScoringMatchesFullMatrixNormalizationBitsWithoutMutatingRows() {
		double[] means = {-0.0, 1e7, -1e-8, 2};
		double[] scales = {1, 0.3, 2, 1};
		LinearSvmModel svm = new LinearSvmModel(new double[]{-0.0, Math.PI, 1e-7, -3}, -0.0, 1, 1);
		PercolatorFoldModel model = new PercolatorFoldModel(new String[]{"a", "b", "c", "d"}, means, scales, svm, "a", 1, true, 1);
		double[][] input = new double[10001][4];
		Random random = new Random(93);
		for (double[] row : input) {
			for (int col = 0; col < row.length; col++) {
				row[col] = random.nextDouble() * 1e4;
			}
		}
		input[4095][0] = -0.0;
		input[4096][1] = Double.POSITIVE_INFINITY;
		input[8192][2] = Double.longBitsToDouble(0x7ff8000000000012L);
		double[] expected = svm.decisionFunction(StandardScaler.from(means, scales).transform(input));
		double[] actual = model.predict(input);
		for (int row = 0; row < input.length; row++) {
			assertEquals(Double.doubleToRawLongBits(expected[row]), Double.doubleToRawLongBits(actual[row]), "row " + row);
		}
		assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(input[4095][0]));
		assertEquals(0x7ff8000000000012L, Double.doubleToRawLongBits(input[8192][2]));
		assertEquals(0, model.predict(new double[0][]).length);
	}
}
