package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinearSvmAndGridSearchTest {
	@Test
	void linearSvmFitsSeparableData() {
		double[][] x = new double[][]{
			{2, 2},
			{3, 3},
			{2, 3},
			{-2, -2},
			{-3, -3},
			{-2, -3}
		};
		int[] y = new int[]{1, 1, 1, -1, -1, -1};
		LinearSvmModel model = LinearSvmModel.fit(x, y, 1.0, 1.0, 40);
		int[] pred = model.predictClasses(x);
		int ok = 0;
		for (int i = 0; i < pred.length; i++) {
			int truth = y[i] == 1 ? 1 : 0;
			if (pred[i] == truth) {
				ok++;
			}
		}
		assertTrue(ok >= 5);
	}

	@Test
	void gridSearchReturnsValidClassWeightPair() {
		double[][] x = new double[][]{
			{2, 2},
			{3, 3},
			{2, 3},
			{-2, -2},
			{-3, -3},
			{-2, -3},
			{-1, -2},
			{1, 2},
			{2, 1}
		};
		int[] y01 = new int[]{1, 1, 1, 0, 0, 0, 0, 1, 1};
		ClassWeightPair pair = ClassWeightGridSearch.select(x, y01, 123);
		assertNotNull(pair);
		assertTrue(pair.negative() == 0.1 || pair.negative() == 1.0 || pair.negative() == 10.0);
		assertTrue(pair.positive() == 0.1 || pair.positive() == 1.0 || pair.positive() == 10.0);
	}
}
