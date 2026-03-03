package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.JavaPotCli;

class ParityIntegrationTest {
	private static final Path MOKAPOT_SOURCE = Path.of("/Users/searle.brian/Documents/projects/mokapot");
	private static final Path PIN_FILE = Path.of("/Users/searle.brian/Documents/projects/mokapot/data/10k_psms_test.pin");

	@TempDir
	Path tempDir;

	@Test
	void comparesAgainstMokapotSourceMainline() throws Exception {
		Assumptions.assumeTrue(Files.exists(PIN_FILE), "PIN test file not available");
		Assumptions.assumeTrue(Files.isDirectory(MOKAPOT_SOURCE), "mokapot source tree not available");

		Path javaOut = tempDir.resolve("java");
		Path sourceOut = tempDir.resolve("source");
		Files.createDirectories(javaOut);
		Files.createDirectories(sourceOut);

		JavaPotCli.main(new String[]{
			PIN_FILE.toString(),
			"--dest_dir", javaOut.toString(),
			"--max_workers", "1",
			"--seed", "1"
		});

		String sourceCmd = "MPLCONFIGDIR=/tmp/mplcache " +
			"PYTHONPATH=" + MOKAPOT_SOURCE + " " +
			"conda run -n mokapot110 python -m mokapot.mokapot " +
			PIN_FILE + " --dest_dir " + sourceOut + " --max_workers 1";
		int exit = runShell(sourceCmd, MOKAPOT_SOURCE);
		Assumptions.assumeTrue(exit == 0, "Source mainline mokapot command failed");

		Counts javaCounts = readCounts(javaOut.resolve("targets.psms.tsv"), javaOut.resolve("targets.peptides.tsv"));
		Counts sourceCounts = readCounts(sourceOut.resolve("targets.psms.tsv"), sourceOut.resolve("targets.peptides.tsv"));

		int psmDiff = Math.abs(javaCounts.psmAtFdr() - sourceCounts.psmAtFdr());
		int pepDiff = Math.abs(javaCounts.peptideAtFdr() - sourceCounts.peptideAtFdr());
		double peptideRelativeDiff = relativeDiff(javaCounts.peptideAtFdr(), sourceCounts.peptideAtFdr());

		Set<String> javaPeptides = readPeptidesAtThreshold(javaOut.resolve("targets.peptides.tsv"), 0.01);
		Set<String> sourcePeptides = readPeptidesAtThreshold(sourceOut.resolve("targets.peptides.tsv"), 0.01);
		double overlapInSource = overlapFraction(sourcePeptides, javaPeptides);
		double overlapInJava = overlapFraction(javaPeptides, sourcePeptides);
		double overlap = Math.min(overlapInSource, overlapInJava);

		assertTrue(pepDiff <= 1, "Peptide q<=0.01 difference too high: " + pepDiff);
		assertTrue(psmDiff <= 10, "PSM q<=0.01 difference too high: " + psmDiff);
		assertTrue(
			peptideRelativeDiff < 0.05,
			"Peptide q<=0.01 relative difference should be <5%, observed=" + peptideRelativeDiff
		);
		assertTrue(
			overlap >= 0.90,
			"Peptide overlap at q<=0.01 should be >=90%, observed min overlap=" + overlap +
				" (source=" + overlapInSource + ", java=" + overlapInJava + ")"
		);
	}

	@Test
	void optionalComparisonToInstalledMokapotCli() throws Exception {
		Assumptions.assumeTrue(
			Boolean.getBoolean("javapot.run.installed_mokapot_comparison"),
			"Set -Djavapot.run.installed_mokapot_comparison=true to run installed CLI comparison"
		);
		Assumptions.assumeTrue(Files.exists(PIN_FILE), "PIN test file not available");

		Path javaOut = tempDir.resolve("java_cli");
		Path cliOut = tempDir.resolve("mokapot_cli");
		Files.createDirectories(javaOut);
		Files.createDirectories(cliOut);

		JavaPotCli.main(new String[]{
			PIN_FILE.toString(),
			"--dest_dir", javaOut.toString(),
			"--max_workers", "1",
			"--seed", "1"
		});

		String cliCmd = "MPLCONFIGDIR=/tmp/mplcache conda run -n mokapot110 mokapot " +
			PIN_FILE + " --dest_dir " + cliOut;
		int exit = runShell(cliCmd, PIN_FILE.getParent());
		Assumptions.assumeTrue(exit == 0, "Installed mokapot CLI command failed");

		Path cliPsm = cliOut.resolve("mokapot.psms.txt");
		Path cliPep = cliOut.resolve("mokapot.peptides.txt");
		Assumptions.assumeTrue(Files.exists(cliPsm) && Files.exists(cliPep), "mokapot CLI outputs not found");

		Counts javaCounts = readCounts(javaOut.resolve("targets.psms.tsv"), javaOut.resolve("targets.peptides.tsv"));
		Counts cliCounts = readCounts(cliPsm, cliPep);

		assertTrue(Math.abs(javaCounts.peptideAtFdr() - cliCounts.peptideAtFdr()) <= 5);
	}

	private static int runShell(String command, Path cwd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder("/bin/zsh", "-lc", command);
		pb.directory(cwd.toFile());
		pb.redirectErrorStream(true);
		Process process = pb.start();
		try (BufferedReader reader = process.inputReader()) {
			while (reader.readLine() != null) {
				// discard output in test, only exit code matters
			}
		}
		return process.waitFor();
	}

	private static Counts readCounts(Path psmFile, Path pepFile) throws IOException {
		int psmAt = countAtThreshold(psmFile, 0.01);
		int pepAt = countAtThreshold(pepFile, 0.01);
		return new Counts(psmAt, pepAt);
	}

	private static int countAtThreshold(Path file, double threshold) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return 0;
			}
			String[] cols = header.split("\\t");
			int qIdx = -1;
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].equals("mokapot_qvalue") || cols[i].equals("mokapot q-value")) {
					qIdx = i;
					break;
				}
			}
			if (qIdx < 0) {
				throw new IllegalStateException("q-value column not found in " + file);
			}

			int count = 0;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				double q = Double.parseDouble(parts[qIdx]);
				if (q <= threshold) {
					count++;
				}
			}
			return count;
		}
	}

	private static Set<String> readPeptidesAtThreshold(Path file, double threshold) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String header = reader.readLine();
			if (header == null) {
				return Set.of();
			}
			String[] cols = header.split("\\t");
			int qIdx = -1;
			int pepIdx = -1;
			for (int i = 0; i < cols.length; i++) {
				if (cols[i].equals("mokapot_qvalue") || cols[i].equals("mokapot q-value")) {
					qIdx = i;
				} else if (cols[i].equalsIgnoreCase("Peptide")) {
					pepIdx = i;
				}
			}
			if (qIdx < 0 || pepIdx < 0) {
				throw new IllegalStateException("Required columns not found in " + file);
			}

			Set<String> peptides = new HashSet<>();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\\t", -1);
				double q = Double.parseDouble(parts[qIdx]);
				if (q <= threshold) {
					peptides.add(parts[pepIdx]);
				}
			}
			return peptides;
		}
	}

	private static double relativeDiff(int observed, int reference) {
		return Math.abs(observed - reference) / Math.max(1.0, reference);
	}

	private static double overlapFraction(Set<String> reference, Set<String> observed) {
		if (reference.isEmpty()) {
			return 1.0;
		}
		int shared = 0;
		for (String peptide : reference) {
			if (observed.contains(peptide)) {
				shared++;
			}
		}
		return shared / (double) reference.size();
	}

	private record Counts(int psmAtFdr, int peptideAtFdr) {
	}
}
