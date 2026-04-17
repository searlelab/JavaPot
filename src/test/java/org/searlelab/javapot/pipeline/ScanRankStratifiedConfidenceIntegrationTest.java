package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.cli.OutputFormat;
import org.searlelab.javapot.io.ModelIO;
import org.searlelab.javapot.model.LinearSvmModel;
import org.searlelab.javapot.model.PercolatorFoldModel;

class ScanRankStratifiedConfidenceIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void stratifiesTdcPsmConfidenceByScanRankWithoutChangingPeptideOutputs() throws Exception {
		Path baselinePin = tempDir.resolve("baseline/input.pin");
		Path allOnesPin = tempDir.resolve("all_ones/input.pin");
		Path stratifiedPin = tempDir.resolve("stratified/input.pin");
		Path modelFile = tempDir.resolve("scan_rank.model.tsv");
		Files.createDirectories(baselinePin.getParent());
		Files.createDirectories(allOnesPin.getParent());
		Files.createDirectories(stratifiedPin.getParent());
		writePin(baselinePin, false, false);
		writePin(allOnesPin, true, true);
		writePin(stratifiedPin, true, false);
		writeModelFile(modelFile);

		JavaPotRunResult baseline = runPin(baselinePin, tempDir.resolve("baseline_out"), modelFile);
		JavaPotRunResult allOnes = runPin(allOnesPin, tempDir.resolve("all_ones_out"), modelFile);
		JavaPotRunResult stratified = runPin(stratifiedPin, tempDir.resolve("stratified_out"), modelFile);

		Path baselinePsm = tempDir.resolve("baseline_out/input.psms.tsv");
		Path baselinePeptide = tempDir.resolve("baseline_out/input.peptides.tsv");
		Path allOnesPsm = tempDir.resolve("all_ones_out/input.psms.tsv");
		Path allOnesPeptide = tempDir.resolve("all_ones_out/input.peptides.tsv");
		Path stratifiedPsm = tempDir.resolve("stratified_out/input.psms.tsv");
		Path stratifiedPeptide = tempDir.resolve("stratified_out/input.peptides.tsv");

		assertEquals(-1L, Files.mismatch(baselinePsm, allOnesPsm), "scan_rank=1 should not change PSM output");
		assertEquals(-1L, Files.mismatch(baselinePeptide, allOnesPeptide), "scan_rank=1 should not change peptide output");
		assertEquals(-1L, Files.mismatch(baselinePeptide, stratifiedPeptide), "scan_rank should not change peptide output");
		assertNotEquals(-1L, Files.mismatch(baselinePsm, stratifiedPsm), "Stratified scan_rank should change PSM output");

		Map<String, Double> baselinePsmQ = readQValues(baselinePsm);
		Map<String, Double> stratifiedPsmQ = readQValues(stratifiedPsm);
		assertEquals(0.75, baselinePsmQ.get("s102_r1"), 1e-12);
		assertEquals(0.75, baselinePsmQ.get("s103_r2"), 1e-12);
		assertEquals(0.75, baselinePsmQ.get("s104_r2"), 1e-12);
		assertEquals(0.75, baselinePsmQ.get("s105_r2"), 1e-12);
		assertEquals(1.0, stratifiedPsmQ.get("s102_r1"), 1e-12);
		assertEquals(1.0 / 3.0, stratifiedPsmQ.get("s103_r2"), 1e-12);
		assertEquals(1.0 / 3.0, stratifiedPsmQ.get("s104_r2"), 1e-12);
		assertEquals(1.0 / 3.0, stratifiedPsmQ.get("s105_r2"), 1e-12);

		assertEquals(4, countTargetsAtThreshold(baseline.psms(), 0.75), "Flat baseline should retain all target pools at q<=0.75");
		assertEquals(4, countTargetsAtThreshold(allOnes.psms(), 0.75), "scan_rank=1 should match baseline detections");
		assertEquals(3, countTargetsAtThreshold(stratified.psms(), 0.5), "Stratified scan_rank should accept only rank>=2 targets at q<=0.5");
		assertEquals(0, countTargetsAtThreshold(baseline.psms(), 0.5), "Flat baseline should reject all targets at q<=0.5");

		assertEquals(countTargetsAtThreshold(baseline.peptides(), 0.5), countTargetsAtThreshold(stratified.peptides(), 0.5));
		assertEquals(countTargetsAtThreshold(baseline.peptides(), 0.75), countTargetsAtThreshold(allOnes.peptides(), 0.75));
	}

	private JavaPotRunResult runPin(Path pin, Path outDir, Path modelFile) {
		try {
			Files.createDirectories(outDir);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		JavaPotOptions config = new JavaPotOptions(
			pin,
			outDir,
			1,
			OutputFormat.PERCOLATOR,
			true,
			0.75,
			0.75,
			3,
			7L,
			"featA",
			null,
			null,
			true,
			false,
			null,
			null,
			null,
			null,
			modelFile,
			3,
			1,
			false
		);
		return JavaPotRunner.runForResult(config);
	}

	private static void writeModelFile(Path modelFile) {
		LinearSvmModel svm = new LinearSvmModel(new double[]{1.0}, 0.0, 1.0, 1.0);
		PercolatorFoldModel foldOne = new PercolatorFoldModel(
			new String[]{"featA"},
			new double[]{0.0},
			new double[]{1.0},
			svm,
			"featA",
			0,
			true,
			1
		);
		PercolatorFoldModel foldTwo = new PercolatorFoldModel(
			new String[]{"featA"},
			new double[]{0.0},
			new double[]{1.0},
			svm,
			"featA",
			0,
			true,
			2
		);
		PercolatorFoldModel foldThree = new PercolatorFoldModel(
			new String[]{"featA"},
			new double[]{0.0},
			new double[]{1.0},
			svm,
			"featA",
			0,
			true,
			3
		);
		ModelIO.saveModels(java.util.List.of(foldOne, foldTwo, foldThree), modelFile);
	}

	private static void writePin(Path file, boolean includeScanRank, boolean allOnesScanRank) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SpecId\tLabel\tScanNr\t");
		if (includeScanRank) {
			sb.append("scan_rank\t");
		}
		sb.append("ExpMass\tfeatA\tPeptide\tProteins\n");
		appendPair(sb, includeScanRank, allOnesScanRank, "s100_r1", -1, 100, 500.0, 80.0, "DECOY_A", "D1", "s100_r2", 1, 2, 1.0, "PEP_A_LOW", "P1");
		appendPair(sb, includeScanRank, allOnesScanRank, "s101_r1", -1, 101, 501.0, 70.0, "DECOY_B", "D2", "s101_r2", 1, 2, 1.0, "PEP_B_LOW", "P2");
		appendPair(sb, includeScanRank, allOnesScanRank, "s102_r1", 1, 102, 502.0, 60.0, "PEP_C", "P3", "s102_r2", -1, 2, 1.0, "DECOY_C_LOW", "D3");
		appendPair(sb, includeScanRank, allOnesScanRank, "s103_r1", -1, 103, 503.0, 1.0, "DECOY_D_LOW", "D4", "s103_r2", 1, 2, 50.0, "PEP_D", "P4");
		appendPair(sb, includeScanRank, allOnesScanRank, "s104_r1", -1, 104, 504.0, 1.0, "DECOY_E_LOW", "D5", "s104_r2", 1, 2, 40.0, "PEP_E", "P5");
		appendPair(sb, includeScanRank, allOnesScanRank, "s105_r1", -1, 105, 505.0, 1.0, "DECOY_F_LOW", "D6", "s105_r2", 1, 2, 30.0, "PEP_F", "P6");
		appendPair(sb, includeScanRank, allOnesScanRank, "s106_r1", 1, 106, 506.0, 1.0, "PEP_G_LOW", "P7", "s106_r2", -1, 2, 20.0, "DECOY_G", "D7");
		appendPair(sb, includeScanRank, allOnesScanRank, "s107_r1", 1, 107, 507.0, 1.0, "PEP_H_LOW", "P8", "s107_r2", -1, 2, 10.0, "DECOY_H", "D8");
		Files.writeString(file, sb.toString());
	}

	private static void appendPair(
		StringBuilder sb,
		boolean includeScanRank,
		boolean allOnesScanRank,
		String specIdA,
		int labelA,
		int scan,
		double expMass,
		double featAValue,
		String peptideA,
		String proteinA,
		String specIdB,
		int labelB,
		int scanRankB,
		double featBValue,
		String peptideB,
		String proteinB
	) {
		appendRow(sb, includeScanRank, allOnesScanRank, specIdA, labelA, scan, 1, expMass, featAValue, peptideA, proteinA);
		appendRow(sb, includeScanRank, allOnesScanRank, specIdB, labelB, scan, scanRankB, expMass, featBValue, peptideB, proteinB);
	}

	private static void appendRow(
		StringBuilder sb,
		boolean includeScanRank,
		boolean allOnesScanRank,
		String specId,
		int label,
		int scan,
		int scanRank,
		double expMass,
		double featValue,
		String peptide,
		String protein
	) {
		sb.append(specId).append('\t')
			.append(label).append('\t')
			.append(scan).append('\t');
		if (includeScanRank) {
			sb.append(allOnesScanRank ? 1 : scanRank).append('\t');
		}
		sb.append(expMass).append('\t')
			.append(featValue).append('\t')
			.append(peptide).append('\t')
			.append(protein).append('\n');
	}

	private static Map<String, Double> readQValues(Path file) throws IOException {
		Map<String, Double> out = new LinkedHashMap<>();
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			assertTrue(header != null && !header.isBlank(), "Missing header: " + file);
			String[] cols = header.split("\\t");
			int idIdx = findColumn(cols, "PSMId");
			int qIdx = findColumn(cols, "q-value");
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				out.put(parts[idIdx], Double.parseDouble(parts[qIdx]));
			}
		}
		return out;
	}

	private static int countTargetsAtThreshold(Iterable<JavaPotPeptide> rows, double threshold) {
		int count = 0;
		for (JavaPotPeptide row : rows) {
			if (!row.isDecoy() && row.qValue() <= threshold) {
				count++;
			}
		}
		return count;
	}

	private static int findColumn(String[] cols, String name) {
		for (int i = 0; i < cols.length; i++) {
			if (cols[i].equals(name)) {
				return i;
			}
		}
		throw new IllegalArgumentException("Column not found: " + name);
	}
}
