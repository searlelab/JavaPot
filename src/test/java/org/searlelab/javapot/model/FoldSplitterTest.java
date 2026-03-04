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
			new OptionalColumns("SpecId", null, "ScanNr", null, "ExpMass", null, null, "Proteins")
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
			new OptionalColumns("SpecId", "Filename", "ScanNr", null, "ExpMass", null, null, "Proteins")
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
			new OptionalColumns("SpecId", null, "ScanNr", null, "ExpMass", null, null, "Proteins")
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
}
