package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.javapot.data.ColumnGroups;
import org.searlelab.javapot.data.OptionalColumns;
import org.searlelab.javapot.data.PsmDataset;

class BestFeatureFinderAndModelTypesTest {
	@Test
	void bestFeatureFinderSelectsFeatureAndDirection() {
		PsmDataset ds = makeDataset(new String[][]{
			{"id1", "1", "100", "500.0", "9.0", "0.0", "PEP", "P1"},
			{"id2", "1", "101", "501.0", "8.0", "1.0", "PEQ", "P2"},
			{"id3", "1", "102", "502.0", "7.0", "2.0", "PER", "P3"},
			{"id4", "-1", "103", "503.0", "1.0", "8.0", "PES", "P4"},
			{"id5", "-1", "104", "504.0", "0.0", "9.0", "PET", "P5"},
			{"id6", "-1", "105", "505.0", "-1.0", "10.0", "PEU", "P6"}
		});

		BestFeatureResult best = BestFeatureFinder.findBestFeature(ds, 0.5);
		assertEquals("featGood", best.name());
		assertTrue(best.descending());
		assertEquals(3, best.positives());
		assertEquals(3, BestFeatureFinder.countPositives(best.labels()));
	}

	@Test
	void directionalFinderChoosesAscendingWhenBetter() {
		PsmDataset ds = makeDataset(new String[][]{
			{"id1", "1", "100", "500.0", "9.0", "0.0", "PEP", "P1"},
			{"id2", "1", "101", "501.0", "8.0", "1.0", "PEQ", "P2"},
			{"id3", "1", "102", "502.0", "7.0", "2.0", "PER", "P3"},
			{"id4", "-1", "103", "503.0", "1.0", "8.0", "PES", "P4"},
			{"id5", "-1", "104", "504.0", "0.0", "9.0", "PET", "P5"},
			{"id6", "-1", "105", "505.0", "-1.0", "10.0", "PEU", "P6"}
		});

		BestFeatureResult direction = BestFeatureFinder.findDirectional(ds, "featFlip", 0.5);
		assertFalse(direction.descending());
		assertEquals(3, direction.positives());
	}

	@Test
	void bestFeatureFinderThrowsWhenNoTargetsCanPass() {
		PsmDataset allDecoys = makeDataset(new String[][]{
			{"id1", "-1", "100", "500.0", "9.0", "1.0", "PEP", "P1"},
			{"id2", "-1", "101", "501.0", "8.0", "2.0", "PEQ", "P2"}
		});
		assertThrows(RuntimeException.class, () -> BestFeatureFinder.findBestFeature(allDecoys, 0.01));
	}

	@Test
	void percolatorFoldModelPredictsAndDefensivelyCopiesFeatureNames() {
		LinearSvmModel svm = new LinearSvmModel(new double[]{1.0, 1.0}, 0.0, 1.0, 1.0);
		PercolatorFoldModel model = new PercolatorFoldModel(
			new String[]{"f1", "f2"},
			new double[]{1.0, 2.0},
			new double[]{2.0, 4.0},
			svm,
			"f1",
			12,
			true,
			2
		);

		double[] scores = model.predict(new double[][]{{3.0, 6.0}});
		assertArrayEquals(new double[]{2.0}, scores, 1e-12);
		assertEquals("f1", model.bestFeature());
		assertEquals(12, model.bestFeaturePass());
		assertTrue(model.bestFeatureDescending());
		assertEquals(2, model.fold());
		assertEquals(svm, model.svm());

		String[] names = model.featureNames();
		names[0] = "mutated";
		assertArrayEquals(new String[]{"f1", "f2"}, model.featureNames());
	}

	@Test
	void exceptionAndResultTypesExposeMessagesAndFields() {
		BestFeatureIsBetterException a = new BestFeatureIsBetterException("best feature wins");
		ModelIterationException b = new ModelIterationException("training failed");
		BestFeatureResult result = new BestFeatureResult("feat", 2, new int[]{1, -1}, true);

		assertEquals("best feature wins", a.getMessage());
		assertEquals("training failed", b.getMessage());
		assertEquals("feat", result.name());
		assertEquals(2, result.positives());
		assertArrayEquals(new int[]{1, -1}, result.labels());
		assertTrue(result.descending());
	}

	private static PsmDataset makeDataset(String[][] rows) {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "featGood", "featFlip", "Peptide", "Proteins");
		ColumnGroups groups = new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr", "ExpMass"),
			List.of("featGood", "featFlip"),
			List.of(),
			new OptionalColumns("SpecId", null, "ScanNr", null, "ExpMass", null, null, "Proteins")
		);
		return new PsmDataset(groups, headers, rows);
	}
}
