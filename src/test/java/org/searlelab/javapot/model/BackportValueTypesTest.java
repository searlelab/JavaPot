package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.searlelab.javapot.stats.ConfidenceMode;

class BackportValueTypesTest {
	@Test
	void classWeightPairImplementsValueSemantics() {
		ClassWeightPair pair = new ClassWeightPair(0.1, 10.0);
		ClassWeightPair same = new ClassWeightPair(0.1, 10.0);
		ClassWeightPair different = new ClassWeightPair(1.0, 10.0);

		assertEquals(0.1, pair.negative());
		assertEquals(10.0, pair.positive());
		assertEquals(pair, same);
		assertEquals(pair.hashCode(), same.hashCode());
		assertNotEquals(pair, different);
		assertTrue(pair.toString().contains("negative=0.1"));
	}

	@Test
	void bestFeatureResultPreservesShallowArrayValueSemantics() {
		int[] labels = new int[]{1, -1, 1};
		BestFeatureResult result = new BestFeatureResult("featA", 2, labels, true);
		BestFeatureResult same = new BestFeatureResult("featA", 2, labels, true);
		BestFeatureResult differentArray = new BestFeatureResult("featA", 2, new int[]{1, -1, 1}, true);

		assertEquals("featA", result.name());
		assertEquals(2, result.positives());
		assertArrayEquals(labels, result.labels());
		assertTrue(result.descending());
		assertEquals(result, same);
		assertNotEquals(result, differentArray);
		assertTrue(result.toString().contains("positives=2"));
	}

	@Test
	void trainingParamsImplementsAccessorsAndEquality() {
		TrainingParams params = new TrainingParams(0.01, 10, "featB", 7L, ConfidenceMode.MIXMAX, true);
		TrainingParams same = new TrainingParams(0.01, 10, "featB", 7L, ConfidenceMode.MIXMAX, true);
		TrainingParams different = new TrainingParams(0.02, 10, "featB", 7L, ConfidenceMode.MIXMAX, true);

		assertEquals(0.01, params.trainFdr());
		assertEquals(10, params.maxIter());
		assertEquals("featB", params.direction());
		assertEquals(7L, params.seed());
		assertEquals(ConfidenceMode.MIXMAX, params.confidenceMode());
		assertTrue(params.quiet());
		assertEquals(params, same);
		assertEquals(params.hashCode(), same.hashCode());
		assertNotEquals(params, different);
		assertTrue(params.toString().contains("confidenceMode=MIXMAX"));
	}

	@Test
	void foldTrainingOutputUsesUnderlyingModelEquality() {
		PercolatorFoldModel model = new PercolatorFoldModel(
			new String[]{"featA"},
			new double[]{1.0},
			new double[]{2.0},
			new LinearSvmModel(new double[]{0.5}, 0.25, 1.0, 1.0),
			"featA",
			9,
			true,
			1
		);
		FoldTrainingOutput output = new FoldTrainingOutput(model, "featA", 9, true);
		FoldTrainingOutput same = new FoldTrainingOutput(model, "featA", 9, true);

		assertEquals(model, output.model());
		assertEquals("featA", output.bestFeature());
		assertEquals(9, output.bestFeaturePass());
		assertTrue(output.bestFeatureDesc());
		assertEquals(output, same);
		assertEquals(output.hashCode(), same.hashCode());
		assertTrue(output.toString().contains("bestFeaturePass=9"));
	}
}
