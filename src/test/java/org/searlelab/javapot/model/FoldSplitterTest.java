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
	void splitIsDeterministicAndKeepsSpectrumGroupsTogether() {
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

		int[][] a = FoldSplitter.split(ds, 3, new DeterministicRandom(7));
		int[][] b = FoldSplitter.split(ds, 3, new DeterministicRandom(7));
		assertEquals(a.length, b.length);
		for (int i = 0; i < a.length; i++) {
			assertArrayEquals(a[i], b[i]);
		}

		Map<String, Integer> groupToFold = new HashMap<>();
		for (int fi = 0; fi < a.length; fi++) {
			for (int rowIdx : a[fi]) {
				String key = String.join("|", ds.spectrumValuesAt(rowIdx));
				if (!groupToFold.containsKey(key)) {
					groupToFold.put(key, fi);
				} else {
					assertTrue(groupToFold.get(key) == fi, "Spectrum group split across folds");
				}
			}
		}
	}
}
