package org.searlelab.javapot.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.data.PsmDataset;

class PinFileParserDifferentialTest {
	@TempDir
	Path directory;

	@Test
	void preservesRawCellsAndNumericBitsWithLateRaggedAndMissingRows() throws Exception {
		for (boolean ragged : new boolean[]{false, true}) {
			for (boolean missing : new boolean[]{false, true}) {
				StringBuilder pin = new StringBuilder("# comment\nDefaultDirection\tfeatA\n"
					+ "id\tLabel\tScanNr\tpsm_group\tExpMass\tfeatA\tfeatB\tsequence\tProteins\n");
				String[] values = {"-0.0", "+0.000", "1.000e-7", " 3.5 ", "0x1.0p4", "Infinity", "-Infinity"};
				for (int row = 0; row < 40; row++) {
					pin.append("psm").append(row).append('\t').append(row % 2 == 0 ? "1" : "-1").append('\t')
						.append(row / 2).append("\t1.0\t500.00\t").append(values[row % values.length]).append('\t')
						.append(missing && row == 39 ? "NA" : "2.000").append("\tPÉP_β\t");
					if (row != 0) {
						pin.append("P").append(row);
					}
					if (ragged && row == 39) {
						pin.append("\tP_extra\t");
					}
					pin.append('\n');
				}
				Path file = directory.resolve("case.pin");
				Files.writeString(file, pin);
				PsmDataset expected = PinFileParserReference.read(file);
				PsmDataset actual = PinFileParser.read(file);
				compare(expected, actual);
				compare(expected.withColumnGroups(expected.columnGroups().withFeatureColumns(List.of("featA"))),
					actual.withColumnGroups(actual.columnGroups().withFeatureColumns(List.of("featA"))));
				String[][] copy = actual.rows();
				copy[0][0] = "changed";
				assertEquals("psm0", actual.rawValueAt(0, 0));
				assertThrows(ArrayIndexOutOfBoundsException.class, () -> actual.rawValueAt(0, -1));
				assertThrows(ArrayIndexOutOfBoundsException.class, () -> actual.rawValueAt(0, actual.headers().size()));
			}
		}
	}

	private static void compare(PsmDataset expected, PsmDataset actual) {
		assertEquals(expected.headers(), actual.headers());
		assertEquals(expected.columnGroups().toString(), actual.columnGroups().toString());
		assertArrayEquals(expected.featureNames(), actual.featureNames());
		assertArrayEquals(expected.targets(), actual.targets());
		assertEquals(expected.hasNonTrivialPsmGroup(), actual.hasNonTrivialPsmGroup());
		assertEquals(expected.size(), actual.size());
		for (int row = 0; row < expected.size(); row++) {
			assertEquals(expected.psmGroupAt(row), actual.psmGroupAt(row));
			assertEquals(expected.peptideAt(row), actual.peptideAt(row));
			assertArrayEquals(expected.spectrumValuesAt(row), actual.spectrumValuesAt(row));
			assertArrayEquals(expected.rows()[row], actual.rows()[row]);
			for (int col = 0; col < expected.headers().size(); col++) {
				assertEquals(expected.rawValueAt(row, col), actual.rawValueAt(row, col));
			}
			for (int col = 0; col < expected.featureCount(); col++) {
				assertEquals(Double.doubleToRawLongBits(expected.rawFeatures()[row][col]),
					Double.doubleToRawLongBits(actual.rawFeatures()[row][col]));
			}
		}
	}
}
