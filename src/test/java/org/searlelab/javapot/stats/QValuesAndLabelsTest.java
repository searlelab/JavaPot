package org.searlelab.javapot.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QValuesAndLabelsTest {
	@Test
	void computesTdcQvaluesLikeMokapotReferenceCase() {
		double[] scores = new double[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
		boolean[] targets = new boolean[]{true, true, true, true, true, false, false, false, false, false};
		double[] q = QValues.tdc(scores, targets, true);
		double[] expected = new double[]{0.2, 0.2, 0.2, 0.2, 0.2, 0.4, 0.6, 0.8, 1.0, 1.0};
		assertArrayEquals(expected, q, 1e-8);
	}

	@Test
	void supportsSkippingDecoysPlusOneForTrainingLikePercolator() {
		double[] scores = new double[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
		boolean[] targets = new boolean[]{true, true, true, true, true, false, false, false, false, false};
		double[] q = QValues.tdc(scores, targets, true, true);
		double[] expected = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
		assertArrayEquals(expected, q, 1e-8);
	}

	@Test
	void handlesNoDecoysDeterministicallyForTrainingAndEvaluationModes() {
		double[] scores = new double[]{9, 8, 7};
		boolean[] targets = new boolean[]{true, true, true};
		double[] evalQ = QValues.tdc(scores, targets, true, false);
		double[] trainQ = QValues.tdc(scores, targets, true, true);
		assertArrayEquals(new double[]{1.0, 1.0, 1.0}, evalQ, 1e-12);
		assertArrayEquals(new double[]{0.0, 0.0, 0.0}, trainQ, 1e-12);
	}

	@Test
	void updatesLabelsWithOneZeroMinusOneSemantics() {
		double[] scores = new double[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
		boolean[] targets = new boolean[]{true, true, true, true, true, false, false, false, false, false};
		int[] labels = LabelUpdater.updateLabels(scores, targets, 0.3, true);
		int positives = 0;
		int zeros = 0;
		int negatives = 0;
		for (int label : labels) {
			if (label == 1) {
				positives++;
			} else if (label == 0) {
				zeros++;
			} else if (label == -1) {
				negatives++;
			}
		}
		assertEquals(5, positives);
		assertEquals(0, zeros);
		assertEquals(5, negatives);
	}

	@Test
	void calibratesScoresFromTargetMinAndDecoyMedian() {
		double[] scores = new double[]{3, 2, 1, 0};
		boolean[] targets = new boolean[]{true, true, false, false};
		double[] calibrated = ScoreCalibrator.calibrate(scores, targets, 1.0);
		assertArrayEquals(new double[]{2.0 / 3.0, 0.0, -2.0 / 3.0, -4.0 / 3.0}, calibrated, 1e-9);
	}

	@Test
	void calibratesWithoutNaNWhenTargetAndDecoyAnchorCoincide() {
		double[] scores = new double[]{5.0, 5.0};
		boolean[] targets = new boolean[]{true, false};
		double[] calibrated = ScoreCalibrator.calibrate(scores, targets, 1.0);
		for (double value : calibrated) {
			assertTrue(Double.isFinite(value));
		}
	}

	@Test
	void calibratesWithoutThrowingWhenNoDecoysArePresent() {
		double[] scores = new double[]{3.0, 2.0, 1.0};
		boolean[] targets = new boolean[]{true, true, true};
		double[] calibrated = ScoreCalibrator.calibrate(scores, targets, 1.0);
		for (double value : calibrated) {
			assertTrue(Double.isFinite(value));
		}
	}

	@Test
	void supportsDeterministicMixmaxLabelUpdates() {
		double[] scores = new double[]{10, 9, 8, 7, 6, 5, 4, 3};
		boolean[] targets = new boolean[]{true, false, true, false, true, false, true, false};
		int[] labelsA = LabelUpdater.updateLabels(scores, targets, 0.2, true, ConfidenceMode.MIXMAX, 42L, true);
		int[] labelsB = LabelUpdater.updateLabels(scores, targets, 0.2, true, ConfidenceMode.MIXMAX, 42L, true);
		assertArrayEquals(labelsA, labelsB);
	}
}
