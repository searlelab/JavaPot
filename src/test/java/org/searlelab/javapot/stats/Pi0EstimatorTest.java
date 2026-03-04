package org.searlelab.javapot.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Pi0EstimatorTest {
	@Test
	void isDeterministicForFixedSeed() {
		double[] scores = new double[]{9, 8, 7, 6, 5, 4, 3, 2, 1};
		boolean[] targets = new boolean[]{true, true, false, true, false, true, false, false, true};
		double p1 = Pi0Estimator.estimate(scores, targets, true, 99L);
		double p2 = Pi0Estimator.estimate(scores, targets, true, 99L);
		assertEquals(p1, p2, 1e-12);
	}

	@Test
	void staysWithinBounds() {
		double[] scores = new double[]{12, 11, 10, 9, 8, 7, 6, 5};
		boolean[] targets = new boolean[]{true, false, true, false, true, false, true, false};
		double pi0 = Pi0Estimator.estimate(scores, targets, true, 11L);
		assertTrue(pi0 >= 0.0 && pi0 <= 1.0);
	}

	@Test
	void returnsOneWhenNoTargetPValuesAvailable() {
		double[] scores = new double[]{5, 4, 3};
		boolean[] targets = new boolean[]{false, false, false};
		double pi0 = Pi0Estimator.estimate(scores, targets, true, 1L);
		assertEquals(1.0, pi0, 0.0);
	}

	@Test
	void computesPercolatorStyleTargetPValues() {
		double[] scores = new double[]{5, 5, 4, 4, 3, 2};
		boolean[] targets = new boolean[]{true, false, true, false, true, false};
		double[] p = Pi0Estimator.pValuesFromSorted(scores, targets);
		assertTrue(p.length == 3);
		assertTrue(p[0] <= p[1] && p[1] <= p[2]);
		assertArrayEquals(new double[]{0.375, 0.625, 0.75}, p, 1e-12);
	}
}
