package org.searlelab.javapot.stats;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PepEstimatorTest {
	@Test
	void returnsBoundedValues() {
		double[] scores = new double[]{10, 9, 8, 7, 6, 5, 4, 3};
		boolean[] targets = new boolean[]{true, true, false, true, false, true, false, false};
		PepEstimator.Result result = PepEstimator.tdcToPep(scores, targets);
		assertFalse(result.usedFallback());
		for (double pep : result.pepValues()) {
			assertTrue(pep >= 0.0 && pep <= 1.0);
		}
	}

	@Test
	void isNonDecreasingOverSortedScores() {
		double[] scores = new double[]{12, 11, 10, 9, 8, 7, 6, 5};
		boolean[] targets = new boolean[]{true, true, false, true, false, false, false, false};
		double[] pep = PepEstimator.tdcToPep(scores, targets).pepValues();
		for (int i = 1; i < pep.length; i++) {
			assertTrue(pep[i - 1] <= pep[i] + 1e-12);
		}
	}

	@Test
	void isNotTriviallyEqualToQValues() {
		double[] scores = new double[]{9, 8, 7, 6, 5, 4, 3, 2};
		boolean[] targets = new boolean[]{true, false, true, false, true, true, false, false};
		double[] q = QValues.tdc(scores, targets, true);
		double[] pep = PepEstimator.tdcToPep(scores, targets).pepValues();
		double maxDiff = 0.0;
		for (int i = 0; i < q.length; i++) {
			double diff = Math.abs(q[i] - pep[i]);
			if (diff > maxDiff) {
				maxDiff = diff;
			}
		}
		assertNotEquals(0.0, maxDiff, 1e-9);
	}

	@Test
	void fallsBackToPavaWhenSplineOutputIsInvalid() {
		double[] scores = new double[]{Double.NaN, 9, 8, 7, 6, 5};
		boolean[] targets = new boolean[]{true, false, true, false, true, false};
		PepEstimator.Result result = PepEstimator.tdcToPep(scores, targets, true, true);
		assertTrue(result.usedFallback());
		for (double pep : result.pepValues()) {
			assertTrue(Double.isFinite(pep));
		}
	}

	@Test
	void qDerivedTdcPepKeepsQvalueAtMostPep() {
		double[] scores = new double[]{12, 11, 10, 9, 8, 7, 6, 5, 4, 3};
		boolean[] targets = new boolean[]{true, false, true, true, false, true, false, true, false, true};
		double[] q = QValues.tdc(scores, targets, true);
		double[] pep = PepEstimator.tdcQvalsToPep(scores, targets, q).pepValues();
		for (int i = 0; i < q.length; i++) {
			assertTrue(q[i] <= pep[i] + 1e-12, "q-value should be <= PEP at index " + i);
		}
	}

	@Test
	void qDerivedTdcPepFallsBackToPavaWhenSplineOutputIsInvalid() {
		double[] scores = new double[]{Double.NaN, 9, 8, 7, 6, 5};
		boolean[] targets = new boolean[]{true, false, true, false, true, false};
		double[] q = QValues.tdc(scores, targets, true);
		PepEstimator.Result result = PepEstimator.tdcQvalsToPep(scores, targets, q, true, true);
		assertTrue(result.usedFallback());
		for (double pep : result.pepValues()) {
			assertTrue(Double.isFinite(pep));
		}
	}

	@Test
	void rejectsLengthMismatchesAndSupportsEmptyInputs() {
		assertThrows(IllegalArgumentException.class, () -> PepEstimator.tdcToPep(new double[]{1.0}, new boolean[0], true, true));
		assertThrows(
			IllegalArgumentException.class,
			() -> PepEstimator.tdcQvalsToPep(new double[]{1.0}, new boolean[]{true}, new double[0], true, true)
		);

		PepEstimator.Result emptyPep = PepEstimator.tdcToPep(new double[0], new boolean[0], false, false);
		PepEstimator.Result emptyQPep = PepEstimator.tdcQvalsToPep(new double[0], new boolean[0], new double[0], false, false);
		assertTrue(emptyPep.pepValues().length == 0);
		assertTrue(emptyQPep.pepValues().length == 0);
	}

	@Test
	void supportsPavaOnlyPathAndAllDecoyQDerivedPath() {
		double[] scores = new double[]{9, 8, 7, 6, 5};
		boolean[] targets = new boolean[]{true, false, true, false, true};
		PepEstimator.Result pavaOnly = PepEstimator.tdcToPep(scores, targets, false, false);
		assertFalse(pavaOnly.usedFallback());
		for (double pep : pavaOnly.pepValues()) {
			assertTrue(pep >= 0.0 && pep <= 1.0);
		}

		boolean[] allDecoys = new boolean[]{false, false, false};
		double[] q = new double[]{0.1, 0.5, 1.0};
		PepEstimator.Result allDecoyResult = PepEstimator.tdcQvalsToPep(new double[]{3, 2, 1}, allDecoys, q, false, false);
		for (double pep : allDecoyResult.pepValues()) {
			assertTrue(pep == 1.0);
		}
	}
}
