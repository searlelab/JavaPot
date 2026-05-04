package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.searlelab.javapot.data.ColumnGroups;
import org.searlelab.javapot.data.OptionalColumns;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.util.DeterministicRandom;

class FoldSplitterTest {
	@Test
	void splitIsDeterministicAndKeepsFileScanGroupsTogether() {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins");
		String[][] rows = new String[][]{
			{"a", "1", "100", "500.0", "1.0", "P1", "Prot"},
			{"b", "-1", "100", "500.0", "0.0", "P2", "Prot"},
			{"c", "1", "101", "500.1", "2.0", "P3", "Prot"},
			{"d", "-1", "102", "500.2", "3.0", "P4", "Prot"},
			{"e", "1", "103", "500.3", "4.0", "P5", "Prot"},
			{"f", "-1", "103", "500.3", "5.0", "P6", "Prot"}
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
		PsmDataset ds = new PsmDataset(groups, headers, rows);

		int[][] a = FoldSplitter.split(ds, 3, new DeterministicRandom(7), "input_a.pin");
		int[][] b = FoldSplitter.split(ds, 3, new DeterministicRandom(7), "input_a.pin");
		assertEquals(a.length, b.length);
		for (int i = 0; i < a.length; i++) {
			assertArrayEquals(a[i], b[i]);
		}

		Map<String, Integer> fileScanToFold = new HashMap<>();
		for (int fi = 0; fi < a.length; fi++) {
			for (int rowIdx : a[fi]) {
				String key = "input_a.pin|" + ds.valueAt(rowIdx, "ScanNr");
				if (!fileScanToFold.containsKey(key)) {
					fileScanToFold.put(key, fi);
				} else {
					assertTrue(fileScanToFold.get(key) == fi, "File+Scan group split across folds");
				}
			}
		}
	}

	@Test
	void splitUsesExplicitFilenameWithScanAsGroupingKey() {
		List<String> headers = List.of("SpecId", "Filename", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins");
		String[][] rows = new String[][]{
			{"a", "f1.raw", "1", "100", "500.0", "1.0", "P1", "Prot"},
			{"b", "f1.raw", "-1", "100", "500.0", "0.0", "P2", "Prot"},
			{"c", "f2.raw", "1", "100", "500.1", "2.0", "P3", "Prot"},
			{"d", "f2.raw", "-1", "101", "500.2", "3.0", "P4", "Prot"}
		};
		ColumnGroups groups = new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("Filename", "ScanNr", "ExpMass"),
			List.of("feat"),
			List.of(),
			new OptionalColumns("SpecId", "Filename", "ScanNr", null, null, "ExpMass", null, null, "Proteins")
		);
		PsmDataset ds = new PsmDataset(groups, headers, rows);

		int[][] folds = FoldSplitter.split(ds, 2, new DeterministicRandom(3), "ignored.pin");
		Map<String, Integer> fileScanToFold = new HashMap<>();
		for (int fi = 0; fi < folds.length; fi++) {
			for (int rowIdx : folds[fi]) {
				String key = ds.valueAt(rowIdx, "Filename") + "|" + ds.valueAt(rowIdx, "ScanNr");
				if (!fileScanToFold.containsKey(key)) {
					fileScanToFold.put(key, fi);
				} else {
					assertEquals(fileScanToFold.get(key).intValue(), fi, "File+Scan group split across folds");
				}
			}
		}
	}

	@Test
	void splitRepairsLabelEmptyFoldsWhenFeasible() {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins");
		String[][] rows = new String[][]{
			{"t1", "1", "100", "500.0", "10.0", "PT1", "Prot"},
			{"t2", "1", "101", "500.1", "9.0", "PT2", "Prot"},
			{"t3", "1", "102", "500.2", "8.0", "PT3", "Prot"},
			{"t4", "1", "103", "500.3", "7.0", "PT4", "Prot"},
			{"t5", "1", "104", "500.4", "6.0", "PT5", "Prot"},
			{"t6", "1", "105", "500.5", "5.0", "PT6", "Prot"},
			{"d1", "-1", "200", "600.0", "4.0", "PD1", "Prot"},
			{"d2", "-1", "201", "600.1", "3.0", "PD2", "Prot"},
			{"d3", "-1", "202", "600.2", "2.0", "PD3", "Prot"}
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
		PsmDataset ds = new PsmDataset(groups, headers, rows);

		int[][] folds = FoldSplitter.split(ds, 3, new DeterministicRandom(11), "rt_sorted.pin");
		for (int fi = 0; fi < folds.length; fi++) {
			int targets = 0;
			int decoys = 0;
			for (int rowIdx : folds[fi]) {
				if (ds.targetAt(rowIdx)) {
					targets++;
				} else {
					decoys++;
				}
			}
			assertTrue(targets > 0, "Fold " + fi + " has no targets");
			assertTrue(decoys > 0, "Fold " + fi + " has no decoys");
		}
	}

	@Test
	void splitGroupedKeepsDuplicatePrecursorsTogether() {
		PsmDataset ds = datasetWithEntityColumns();

		int[][] folds = FoldSplitter.splitGrouped(ds, 3, new DeterministicRandom(5), "grouped.pin");

		assertRowsShareFold(folds, 0, 1);
	}

	@Test
	void splitGroupedPrefersPrecursorOverModifiedPeptideAndPeptide() {
		PsmDataset ds = datasetWithEntityColumns();

		int[][] folds = FoldSplitter.splitGrouped(ds, 3, new DeterministicRandom(5), "grouped.pin");

		assertRowsShareFold(folds, 0, 1);
	}

	@Test
	void splitGroupedFallsBackToModifiedPeptideThenPeptide() {
		PsmDataset modified = datasetWithoutPrecursor();
		PsmDataset peptide = datasetWithoutPrecursorOrModifiedPeptide();

		int[][] modifiedFolds = FoldSplitter.splitGrouped(modified, 3, new DeterministicRandom(5), "modified.pin");
		int[][] peptideFolds = FoldSplitter.splitGrouped(peptide, 3, new DeterministicRandom(5), "peptide.pin");

		assertRowsShareFold(modifiedFolds, 0, 1);
		assertRowsShareFold(peptideFolds, 0, 1);
	}

	private static void assertRowsShareFold(int[][] folds, int leftRow, int rightRow) {
		int leftFold = findFold(folds, leftRow);
		int rightFold = findFold(folds, rightRow);
		assertEquals(leftFold, rightFold);
	}

	private static int findFold(int[][] folds, int row) {
		for (int fi = 0; fi < folds.length; fi++) {
			for (int idx : folds[fi]) {
				if (idx == row) {
					return fi;
				}
			}
		}
		throw new AssertionError("Row not found in folds: " + row);
	}

	private static PsmDataset datasetWithEntityColumns() {
		List<String> headers = List.of(
			"SpecId", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins", "modifiedPeptide", "precursor"
		);
		String[][] rows = new String[][]{
			{"a", "1", "100", "500.0", "1.0", "PEP_A", "Prot", "MOD_A", "PREC_SHARED"},
			{"b", "-1", "101", "500.1", "0.0", "PEP_B", "Prot", "MOD_B", "PREC_SHARED"},
			{"c", "1", "102", "500.2", "2.0", "PEP_C", "Prot", "MOD_C", "PREC_C"},
			{"d", "-1", "103", "500.3", "3.0", "PEP_D", "Prot", "MOD_D", "PREC_D"},
			{"e", "1", "104", "500.4", "4.0", "PEP_E", "Prot", "MOD_E", "PREC_E"},
			{"f", "-1", "105", "500.5", "5.0", "PEP_F", "Prot", "MOD_F", "PREC_F"}
		};
		return dataset(headers, rows, List.of("modifiedPeptide", "precursor"));
	}

	private static PsmDataset datasetWithoutPrecursor() {
		List<String> headers = List.of(
			"SpecId", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins", "modifiedPeptide"
		);
		String[][] rows = new String[][]{
			{"a", "1", "100", "500.0", "1.0", "PEP_A", "Prot", "MOD_SHARED"},
			{"b", "-1", "101", "500.1", "0.0", "PEP_B", "Prot", "MOD_SHARED"},
			{"c", "1", "102", "500.2", "2.0", "PEP_C", "Prot", "MOD_C"},
			{"d", "-1", "103", "500.3", "3.0", "PEP_D", "Prot", "MOD_D"},
			{"e", "1", "104", "500.4", "4.0", "PEP_E", "Prot", "MOD_E"},
			{"f", "-1", "105", "500.5", "5.0", "PEP_F", "Prot", "MOD_F"}
		};
		return dataset(headers, rows, List.of("modifiedPeptide"));
	}

	private static PsmDataset datasetWithoutPrecursorOrModifiedPeptide() {
		List<String> headers = List.of("SpecId", "Label", "ScanNr", "ExpMass", "feat", "Peptide", "Proteins");
		String[][] rows = new String[][]{
			{"a", "1", "100", "500.0", "1.0", "PEP_SHARED", "Prot"},
			{"b", "-1", "101", "500.1", "0.0", "PEP_SHARED", "Prot"},
			{"c", "1", "102", "500.2", "2.0", "PEP_C", "Prot"},
			{"d", "-1", "103", "500.3", "3.0", "PEP_D", "Prot"},
			{"e", "1", "104", "500.4", "4.0", "PEP_E", "Prot"},
			{"f", "-1", "105", "500.5", "5.0", "PEP_F", "Prot"}
		};
		return dataset(headers, rows, List.of());
	}

	private static PsmDataset dataset(List<String> headers, String[][] rows, List<String> extraColumns) {
		ColumnGroups groups = new ColumnGroups(
			headers,
			"Label",
			"Peptide",
			List.of("ScanNr", "ExpMass"),
			List.of("feat"),
			extraColumns,
			new OptionalColumns("SpecId", null, "ScanNr", null, null, "ExpMass", null, null, "Proteins")
		);
		return new PsmDataset(groups, headers, rows);
	}
}
