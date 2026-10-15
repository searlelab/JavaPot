package org.searlelab.javapot.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class QValuesExactnessTest {
	@Test
	void reusableWorkspaceOverwritesScratchAndDoesNotOwnReturnedResults() {
		QValues.TdcWorkspace workspace = new QValues.TdcWorkspace(6);
		double[] firstScores = {1, 1, -0.0, 0, Double.NaN, -2};
		boolean[] labels = {true, false, true, false, true, false};
		double[] retained = QValues.tdc(firstScores, labels, true, false, workspace);
		double[] expectedRetained = retained.clone();
		for (int trial = 0; trial < 12; trial++) {
			double[] scores = trial % 2 == 0 ? new double[]{5, 5, 5, 5, 5, 5} : firstScores;
			boolean[] targets = trial % 3 == 0 ? new boolean[]{true, true, true, true, true, true} : labels;
			boolean descending = (trial & 1) == 0;
			boolean skip = (trial & 2) == 0;
			double[] expected = QValuesReference.tdc(scores, targets, descending, skip);
			double[] actual = QValues.tdc(scores, targets, descending, skip, workspace);
			for (int row = 0; row < actual.length; row++) {
				assertEquals(Double.doubleToRawLongBits(expected[row]), Double.doubleToRawLongBits(actual[row]));
				assertEquals(Double.doubleToRawLongBits(expectedRetained[row]), Double.doubleToRawLongBits(retained[row]));
			}
		}
		assertThrows(IllegalArgumentException.class, () -> QValues.tdc(new double[2], new boolean[2], true, false, workspace));
	}

	@Test
	void preservesFrozenImplementationBitsOnTiesAndBoundaries() {
		verify(new double[]{5, 5, 5, 5, 5}, new boolean[]{true, false, true, true, false});
		verify(new double[]{Double.NaN, Double.NaN, Double.NaN}, new boolean[]{false, true, false});
		double[] values = {Double.NEGATIVE_INFINITY, -1, -0.0, 0.0, 1, Double.POSITIVE_INFINITY,
			Double.NaN, Double.longBitsToDouble(0x7ff8000000000012L)};
		for (int length = 0; length <= 5; length++) {
			int combinations = 1 << (2 * length);
			for (int code = 0; code < combinations; code++) {
				double[] scores = new double[length];
				boolean[] targets = new boolean[length];
				for (int row = 0; row < length; row++) {
					scores[row] = values[(code >> (2 * row)) & 3];
					targets[row] = ((code >> (2 * row + 1)) & 1) == 0;
				}
				verify(scores, targets);
			}
		}
		Random random = new Random(91);
		for (int trial = 0; trial < 300; trial++) {
			int size = random.nextInt(250);
			double[] scores = new double[size];
			boolean[] targets = new boolean[size];
			for (int row = 0; row < size; row++) {
				scores[row] = values[random.nextInt(values.length)];
				targets[row] = trial % 3 == 0 || (trial % 3 == 1 && random.nextBoolean());
			}
			verify(scores, targets);
		}
		assertThrows(IllegalArgumentException.class, () -> QValues.tdc(new double[2], new boolean[1], true));
	}

	@Test
	void repeatedConcurrentCallsHaveIndependentStorage() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			List<Callable<Void>> tasks = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				tasks.add(() -> {
					verify(new double[]{1, 1, -0.0, 0, Double.NaN, -2}, new boolean[]{true, false, true, false, true, false});
					return null;
				});
			}
			for (Future<Void> future : pool.invokeAll(tasks)) {
				future.get();
			}
		} finally {
			pool.shutdownNow();
		}
	}

	private static void verify(double[] scores, boolean[] targets) {
		double[] original = scores.clone();
		boolean[] originalTargets = targets.clone();
		QValues.TdcWorkspace workspace = new QValues.TdcWorkspace(scores.length);
		for (boolean descending : new boolean[]{false, true}) {
			for (boolean skip : new boolean[]{false, true}) {
				double[] expected = QValuesReference.tdc(scores, targets, descending, skip);
				double[] actual = QValues.tdc(scores, targets, descending, skip);
				double[] reused = QValues.tdc(scores, targets, descending, skip, workspace);
				assertEquals(expected.length, actual.length);
				for (int row = 0; row < scores.length; row++) {
					assertEquals(Double.doubleToRawLongBits(expected[row]), Double.doubleToRawLongBits(actual[row]), "q-value row " + row);
					assertEquals(Double.doubleToRawLongBits(expected[row]), Double.doubleToRawLongBits(reused[row]), "workspace q-value row " + row);
					assertEquals(Double.doubleToRawLongBits(original[row]), Double.doubleToRawLongBits(scores[row]), "input score row " + row);
					assertEquals(originalTargets[row], targets[row], "input label row " + row);
				}
			}
		}
	}
}
