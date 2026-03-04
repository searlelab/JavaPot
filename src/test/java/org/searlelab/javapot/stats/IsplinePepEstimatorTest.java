package org.searlelab.javapot.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class IsplinePepEstimatorTest {
	@Test
	void fitXyValidatesInputAndHandlesEmptyData() {
		assertThrows(
			IllegalArgumentException.class,
			() -> IsplinePepEstimator.fitXY(new double[]{1.0}, new double[]{1.0, 2.0}, 0.0, 1.0)
		);

		double[] empty = IsplinePepEstimator.fitXY(new double[0], new double[0], 0.0, 1.0);
		assertNotNull(empty);
		assertEquals(0, empty.length);
	}

	@Test
	void fitYAndFitXyProduceBoundedFiniteValues() {
		double[] y = new double[]{0.0, 1.0, 0.0, 1.0, 0.0};
		double[] fitY = IsplinePepEstimator.fitY(y, 0.0, 1.0);
		assertEquals(y.length, fitY.length);
		for (double v : fitY) {
			assertTrue(Double.isFinite(v));
			assertTrue(v >= 0.0 && v <= 1.0);
		}

		double[] x = new double[]{10, 8, 6, 4, 2};
		double[] fitXy = IsplinePepEstimator.fitXY(x, y, 0.1, 0.9);
		assertEquals(y.length, fitXy.length);
		for (double v : fitXy) {
			assertTrue(Double.isFinite(v));
			assertTrue(v >= 0.1 && v <= 0.9);
		}
	}

	@Test
	void privateHelpersHandleEdgeBranches() throws Exception {
		Method cubic = IsplinePepEstimator.class.getDeclaredMethod("cubicISpline", double.class, double.class, double.class);
		cubic.setAccessible(true);
		assertEquals(0.0, (double) cubic.invoke(null, -1.0, 0.0, 1.0), 1e-12);
		assertEquals(0.0, (double) cubic.invoke(null, 0.0, 2.0, 1.0), 1e-12);
		assertEquals(1.0, (double) cubic.invoke(null, 2.0, 0.0, 1.0), 1e-12);
		double middle = (double) cubic.invoke(null, 0.5, 0.0, 1.0);
		assertTrue(middle > 0.0 && middle < 1.0);

		Method clamp = IsplinePepEstimator.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
		clamp.setAccessible(true);
		assertEquals(0.0, (double) clamp.invoke(null, -1.0, 0.0, 1.0), 1e-12);
		assertEquals(1.0, (double) clamp.invoke(null, 2.0, 0.0, 1.0), 1e-12);
		assertEquals(0.5, (double) clamp.invoke(null, 0.5, 0.0, 1.0), 1e-12);

		Method binData = IsplinePepEstimator.class.getDeclaredMethod("binData", double[].class, double[].class, int.class);
		binData.setAccessible(true);
		Object emptyBinned = binData.invoke(null, new double[0], new double[0], 0);
		assertNotNull(emptyBinned);

		Method fitSpline = IsplinePepEstimator.class.getDeclaredMethod(
			"fitSpline",
			emptyBinned.getClass(),
			double[].class,
			double.class
		);
		fitSpline.setAccessible(true);
		Object nullSpline = fitSpline.invoke(null, emptyBinned, new double[]{0.0}, 1e-6);
		assertNull(nullSpline);

		Method solve = IsplinePepEstimator.class.getDeclaredMethod("solveLinearSystem", double[][].class, double[].class);
		solve.setAccessible(true);
		Object singular = solve.invoke(
			null,
			new double[][]{{0.0, 0.0}, {0.0, 0.0}},
			new double[]{0.0, 0.0}
		);
		assertNull(singular);

		Object pivoted = solve.invoke(
			null,
			new double[][]{{0.0, 1.0}, {1.0, 1.0}},
			new double[]{1.0, 2.0}
		);
		assertNotNull(pivoted);
	}
}
