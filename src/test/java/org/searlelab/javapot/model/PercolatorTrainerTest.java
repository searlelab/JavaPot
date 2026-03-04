package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.searlelab.javapot.data.ColumnGroups;
import org.searlelab.javapot.data.OptionalColumns;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.stats.ConfidenceMode;

class PercolatorTrainerTest {
	@Test
	void tdcTrainingUsesSkipDecoysPlusOneByDefault() {
		PsmDataset ds = makeDataset(new String[][]{
			{"id1", "1", "100", "500.0", "4.0", "PEP1", "P1"},
			{"id2", "-1", "101", "500.1", "3.0", "PEP2", "P2"},
			{"id3", "1", "102", "500.2", "2.0", "PEP3", "P3"},
			{"id4", "-1", "103", "500.3", "1.0", "PEP4", "P4"}
		});
		TrainingParams params = new TrainingParams(0.5, 1, "feat", 7L, ConfidenceMode.TDC);

		FoldTrainingOutput out = PercolatorTrainer.trainFold(ds, new int[]{0, 1, 2, 3}, 1, params);
		assertTrue(out.bestFeaturePass() > 0, "Expected positive training labels under TDC with skipDecoysPlusOne default");
	}

	private static PsmDataset makeDataset(String[][] rows) {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins");
		ColumnGroups groups = new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr", "ExpMass"),
			List.of("feat"),
			List.of(),
			new OptionalColumns("SpecId", null, "ScanNr", null, "ExpMass", null, null, "Proteins")
		);
		return new PsmDataset(groups, headers, rows);
	}
}
