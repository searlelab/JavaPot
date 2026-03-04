package org.searlelab.javapot.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MixMaxQValuesTest {
	@Test
	void isDeterministicForFixedSeed() {
		double[] scores = new double[]{9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
		boolean[] targets = new boolean[]{true, true, false, true, false, true, false, true, false, false};
		MixMaxQValues.Result first = MixMaxQValues.compute(scores, targets, true, 17L);
		MixMaxQValues.Result second = MixMaxQValues.compute(scores, targets, true, 17L);
		assertArrayEquals(first.qValues(), second.qValues(), 1e-12);
		assertEquals(first.pi0(), second.pi0(), 1e-12);
	}

	@Test
	void handlesTiesWithSharedQValuesPerTieBlock() {
		double[] scores = new double[]{5, 5, 4, 4, 3, 3};
		boolean[] targets = new boolean[]{true, false, true, false, true, false};
		double[] q = MixMaxQValues.compute(scores, targets, true, 7L).qValues();
		assertEquals(q[0], q[1], 1e-12);
		assertEquals(q[2], q[3], 1e-12);
		assertEquals(q[4], q[5], 1e-12);
	}

	@Test
	void returnsBoundedQValuesAndPiZero() {
		double[] scores = new double[]{12, 11, 10, 9, 8, 7, 6, 5};
		boolean[] targets = new boolean[]{true, false, true, false, true, false, true, false};
		MixMaxQValues.Result result = MixMaxQValues.compute(scores, targets, true, 123L);
		assertTrue(result.pi0() >= 0.0 && result.pi0() <= 1.0);
		for (double q : result.qValues()) {
			assertTrue(q >= 0.0 && q <= 1.0);
		}
	}
}
