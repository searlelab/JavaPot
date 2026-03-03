package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class StandardScalerTest {
	@Test
	void scalesColumnsToZeroMeanAndUnitVariance() {
		double[][] x = new double[][]{
			{1, 10},
			{2, 20},
			{3, 30},
			{4, 40}
		};
		StandardScaler scaler = new StandardScaler();
		double[][] z = scaler.fitTransform(x);
		assertArrayEquals(new double[]{-1.3416407865, -1.3416407865}, z[0], 1e-6);
		assertArrayEquals(new double[]{1.3416407865, 1.3416407865}, z[3], 1e-6);
	}
}
