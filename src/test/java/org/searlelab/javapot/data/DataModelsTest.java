package org.searlelab.javapot.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DataModelsTest {
	@Test
	void targetConverterHandlesSupportedValuesAndRejectsInvalid() {
		assertTrue(TargetConverter.toBoolean("true"));
		assertTrue(TargetConverter.toBoolean("1"));
		assertFalse(TargetConverter.toBoolean("false"));
		assertFalse(TargetConverter.toBoolean("0"));
		assertFalse(TargetConverter.toBoolean("-1"));

		assertThrows(IllegalArgumentException.class, () -> TargetConverter.toBoolean("2"));
		assertThrows(IllegalArgumentException.class, () -> TargetConverter.toBoolean("abc"));
		assertThrows(IllegalArgumentException.class, () -> TargetConverter.toBoolean(null));
	}

	@Test
	void inferFromColnamesBuildsExpectedGroups() {
		List<String> cols = List.of(
			"SpecId", "Label", "ScanNr", "FileName", "ExpMass", "CalcMass", "ret_time",
			"charge_column", "charge_2", "Peptide", "Proteins", "modifiedPeptide",
			"Precursor", "PeptideGroup", "feat1"
		);

		ColumnGroups groups = ColumnGroups.inferFromColnames(cols);
		assertEquals("Label", groups.targetColumn());
		assertEquals("Peptide", groups.peptideColumn());
		assertEquals(cols, groups.columns());
		assertEquals(List.of("FileName", "ScanNr", "ret_time", "ExpMass"), groups.spectrumColumns());
		assertEquals(List.of("modifiedPeptide", "Precursor", "PeptideGroup"), groups.extraConfidenceLevelColumns());
		assertEquals("SpecId", groups.optionalColumns().id());
		assertEquals("Proteins", groups.optionalColumns().protein());
		assertTrue(groups.featureColumns().contains("feat1"));
		assertTrue(groups.featureColumns().contains("charge_2"));
		assertFalse(groups.featureColumns().contains("Label"));
		assertTrue(groups.toString().contains("targetColumn='Label'"));
	}

	@Test
	void inferFromColnamesAcceptsSequenceAliasForPeptide() {
		List<String> cols = List.of(
			"id", "Label", "ScanNr", "ExpMass", "feat1", "sequence", "Proteins"
		);

		ColumnGroups groups = ColumnGroups.inferFromColnames(cols);
		assertEquals("sequence", groups.peptideColumn());
		assertEquals("id", groups.optionalColumns().id());
		assertTrue(groups.featureColumns().contains("feat1"));
		assertFalse(groups.featureColumns().contains("sequence"));
	}

	@Test
	void constructorValidationRejectsInconsistentColumns() {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "featA", "Peptide", "Proteins");
		OptionalColumns optional = new OptionalColumns("SpecId", null, "ScanNr", null, "ExpMass", null, null, "Proteins");

		assertThrows(IllegalArgumentException.class, () -> new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr"),
			List.of("featA", "featA"),
			List.of(),
			optional
		));

		assertThrows(IllegalArgumentException.class, () -> new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr"),
			List.of("featA"),
			List.of(),
			new OptionalColumns("SpecId", null, "NoScan", null, "ExpMass", null, null, "Proteins")
		));
	}

	@Test
	void psmDatasetAccessorsAndCopiesBehaveAsExpected() {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "featA", "featB", "Peptide", "Proteins");
		String[][] rows = new String[][]{
			{"id1", "1", "100", "500.0", "10.0", "0.5", "PEP", "P1"},
			{"id2", "-1", "101", "501.0", "1.0", "2.5", "PEQ", "P2"},
			{"id3", "0", "102", "502.0", "2.0", "3.5", "PER", "P3"}
		};
		ColumnGroups groups = new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr", "ExpMass"),
			List.of("featA", "featB"),
			List.of(),
			new OptionalColumns("SpecId", null, "ScanNr", null, "ExpMass", null, null, "Proteins")
		);

		PsmDataset ds = new PsmDataset(groups, headers, rows);
		assertEquals(3, ds.size());
		assertEquals(2, ds.featureCount());
		assertArrayEquals(new String[]{"featA", "featB"}, ds.featureNames());
		assertArrayEquals(new boolean[]{true, false, false}, ds.targets());
		assertEquals("PEQ", ds.peptideAt(1));
		assertArrayEquals(new String[]{"101", "501.0"}, ds.spectrumValuesAt(1));
		assertEquals("P3", ds.valueAt(2, "Proteins"));
		assertArrayEquals(new double[]{10.0, 1.0, 2.0}, ds.featureColumn("featA"));
		assertArrayEquals(new boolean[]{true, false, false}, ds.rawTargets());
		assertEquals(groups, ds.columnGroups());
		assertEquals(headers, ds.headers());
		assertEquals("id2", ds.rawValueAt(1, ds.colIndex("SpecId")));
		assertArrayEquals(new int[]{2, 3}, ds.spectrumColIndices());

		double[][] copied = ds.features();
		copied[0][0] = -999.0;
		assertEquals(10.0, ds.rawFeatures()[0][0], 1e-12);
		String[][] rowsCopy = ds.rows();
		rowsCopy[0][0] = "mutated";
		assertEquals("id1", ds.rawValueAt(0, ds.colIndex("SpecId")));

		ColumnGroups oneFeature = groups.withFeatureColumns(List.of("featB"));
		PsmDataset reduced = ds.withColumnGroups(oneFeature);
		assertArrayEquals(new String[]{"featB"}, reduced.featureNames());
		assertEquals(1, reduced.featureCount());

		assertThrows(IllegalArgumentException.class, () -> ds.featureColumn("does_not_exist"));
		assertThrows(IllegalArgumentException.class, () -> ds.colIndex("missing_col"));
	}

	@Test
	void psmDatasetRejectsMissingOrBlankFeatureCells() {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "featA", "Peptide", "Proteins");
		String[][] rows = new String[][]{
			{"id1", "1", "100", "500.0", "", "PEP", "P1"}
		};
		ColumnGroups groups = new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr"),
			List.of("featA"),
			List.of(),
			new OptionalColumns("SpecId", null, "ScanNr", null, "ExpMass", null, null, "Proteins")
		);
		assertThrows(IllegalArgumentException.class, () -> new PsmDataset(groups, headers, rows));
	}

	@Test
	void uniquePreserveOrderDropsDuplicates() {
		List<String> unique = ColumnGroups.uniquePreserveOrder(List.of("A", "B", "A", "C", "B"));
		assertEquals(List.of("A", "B", "C"), unique);
	}
}
