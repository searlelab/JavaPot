package org.searlelab.javapot.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.data.PsmDataset;

class PinFileParserTest {
	@TempDir
	Path tmp;

	@Test
	void parsesBasicPin() throws IOException {
		Path file = tmp.resolve("basic.pin");
		Files.writeString(file, String.join("\n",
			"SpecId\tLabel\tScanNr\tExpMass\tfeatA\tfeatB\tPeptide\tProteins",
			"a\t1\t10\t100.0\t1.0\t2.0\tPEP\tP1",
			"b\t-1\t11\t101.0\t3.0\t4.0\tPEQ\tP2"
		));

		PsmDataset ds = PinFileParser.read(file);
		assertEquals(2, ds.size());
		assertArrayEquals(new String[]{"featA", "featB"}, ds.featureNames());
		assertEquals("SpecId", ds.columnGroups().optionalColumns().id());
		assertEquals("ScanNr", ds.columnGroups().spectrumColumns().get(0));
	}

	@Test
	void parsesAlternateSequenceHeader() throws IOException {
		Path file = tmp.resolve("sequence.features.txt");
		Files.writeString(file, String.join("\n",
			"id\tLabel\tScanNr\tExpMass\tfeatA\tsequence\tProteins",
			"a\t1\t10\t100.0\t1.0\tPEP\tP1",
			"b\t-1\t11\t101.0\t2.0\tPEQ\tP2"
		));

		PsmDataset ds = PinFileParser.read(file);
		assertEquals(2, ds.size());
		assertArrayEquals(new String[]{"featA"}, ds.featureNames());
		assertEquals("sequence", ds.columnGroups().peptideColumn());
		assertEquals("id", ds.columnGroups().optionalColumns().id());
	}

	@Test
	void parsesTraditionalPinWithRaggedProteins() throws IOException {
		Path file = tmp.resolve("traditional.pin");
		Files.writeString(file, String.join("\n",
			"SpecId\tLabel\tScanNr\tfeatA\tPeptide\tProteins",
			"a\t1\t10\t1.0\tPEP\tP1\tP2\tP3",
			"b\t-1\t11\t2.0\tPEQ\tP4"
		));
		PsmDataset ds = PinFileParser.read(file);
		assertEquals("P1:P2:P3", ds.valueAt(0, "Proteins"));
	}

	@Test
	void dropsFeaturesWithMissingValues() throws IOException {
		Path file = tmp.resolve("missing.pin");
		Files.writeString(file, String.join("\n",
			"SpecId\tLabel\tScanNr\tExpMass\tfeatA\tfeatB\tPeptide\tProteins",
			"a\t1\t10\t100.0\t1.0\t\tPEP\tP1",
			"b\t-1\t11\t101.0\t2.0\t3.0\tPEQ\tP2"
		));
		PsmDataset ds = PinFileParser.read(file);
		assertArrayEquals(new String[]{"featA"}, ds.featureNames());
	}

	@Test
	void skipsCommentAndDefaultDirectionLines() throws IOException {
		Path file = tmp.resolve("comments.pin");
		Files.writeString(file, String.join("\n",
			"# header comment",
			"DefaultDirection\tfeatA",
			"SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins",
			"x\t1\t10\t500.0\t3.2\tPEP\tP1"
		));

		PsmDataset ds = PinFileParser.read(file);
		assertEquals(1, ds.size());
		assertArrayEquals(new String[]{"featA"}, ds.featureNames());
	}

	@Test
	void rejectsEmptyAndMalformedPinFiles() throws IOException {
		Path empty = tmp.resolve("empty.pin");
		Files.writeString(empty, "");
		assertThrows(IllegalArgumentException.class, () -> PinFileParser.read(empty));

		Path singleCol = tmp.resolve("singlecol.pin");
		Files.writeString(singleCol, "not_a_tsv_header\nvalue");
		assertThrows(IllegalArgumentException.class, () -> PinFileParser.read(singleCol));

		Path noRows = tmp.resolve("norows.pin");
		Files.writeString(noRows, "SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins\n");
		assertThrows(IllegalArgumentException.class, () -> PinFileParser.read(noRows));
	}

	@Test
	void rejectsRaggedNonTraditionalAndShortRows() throws IOException {
		Path ragged = tmp.resolve("ragged.pin");
		Files.writeString(ragged, String.join("\n",
			"SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tXProteins",
			"a\t1\t10\t500.0\t1.0\tPEP\tP1\tEXTRA"
		));
		assertThrows(IllegalArgumentException.class, () -> PinFileParser.read(ragged));

		Path shortRow = tmp.resolve("short.pin");
		Files.writeString(shortRow, String.join("\n",
			"SpecId\tLabel\tScanNr\tExpMass\tfeatA\tPeptide\tProteins",
			"a\t1\t10\t500.0\t1.0\tPEP"
		));
		assertThrows(IllegalArgumentException.class, () -> PinFileParser.read(shortRow));
	}

	@Test
	void rejectsWhenAllFeaturesAreDropped() throws IOException {
		Path file = tmp.resolve("allmissing.pin");
		Files.writeString(file, String.join("\n",
			"SpecId\tLabel\tScanNr\tExpMass\tfeatA\tfeatB\tPeptide\tProteins",
			"a\t1\t10\t500.0\tNA\tnull\tPEP\tP1",
			"b\t-1\t11\t501.0\tNaN\t \tPEQ\tP2"
		));
		assertThrows(IllegalArgumentException.class, () -> PinFileParser.read(file));
	}
}
