package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.javapot.data.ColumnGroups;
import org.searlelab.javapot.data.OptionalColumns;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.model.LinearSvmModel;
import org.searlelab.javapot.model.PercolatorFoldModel;

class GroupedCrossfitScoringTest {
	@Test
	void rawPredictionUsesHeldOutFoldModelOnly() {
		PsmDataset dataset = dataset();
		int[][] folds = new int[][]{
			new int[]{0, 2},
			new int[]{1, 3}
		};
		List<PercolatorFoldModel> models = List.of(
			model(10.0, 1),
			model(20.0, 2)
		);

		double[] scores = JavaPotRunner.predictRawScores(dataset, folds, models);

		assertArrayEquals(new double[]{11.0, 22.0, 13.0, 24.0}, scores, 1e-12);
	}

	@Test
	void rawPredictionDoesNotApplyValidationLabelScoreCalibration() {
		PsmDataset dataset = dataset();
		int[][] folds = new int[][]{
			new int[]{0, 1, 2, 3}
		};
		List<PercolatorFoldModel> models = List.of(negativeModel(5.0, 1));

		double[] scores = JavaPotRunner.predictRawScores(dataset, folds, models);

		assertArrayEquals(new double[]{4.0, 3.0, 2.0, 1.0}, scores, 1e-12);
	}

	private static PercolatorFoldModel model(double bias, int fold) {
		return new PercolatorFoldModel(
			new String[]{"feat"},
			new double[]{0.0},
			new double[]{1.0},
			new LinearSvmModel(new double[]{1.0}, bias, 1.0, 1.0),
			"feat",
			1,
			true,
			fold
		);
	}

	private static PercolatorFoldModel negativeModel(double bias, int fold) {
		return new PercolatorFoldModel(
			new String[]{"feat"},
			new double[]{0.0},
			new double[]{1.0},
			new LinearSvmModel(new double[]{-1.0}, bias, 1.0, 1.0),
			"feat",
			1,
			true,
			fold
		);
	}

	private static PsmDataset dataset() {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins");
		String[][] rows = new String[][]{
			{"a", "1", "100", "500.0", "1.0", "PEP_A", "Prot"},
			{"b", "-1", "101", "500.1", "2.0", "PEP_B", "Prot"},
			{"c", "1", "102", "500.2", "3.0", "PEP_C", "Prot"},
			{"d", "-1", "103", "500.3", "4.0", "PEP_D", "Prot"}
		};
		ColumnGroups groups = new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr", "ExpMass"),
			List.of("feat"),
			List.of(),
			new OptionalColumns("SpecId", null, "ScanNr", null, null, "ExpMass", null, null, "Proteins")
		);
		return new PsmDataset(groups, headers, rows);
	}
}
