package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeComparisonIntegrationTest {
	private static final Path MOKAPOT_SOURCE = Path.of("/Users/searle.brian/Documents/projects/mokapot");
	private static final Path PIN_FILE = Path.of("/Users/searle.brian/Documents/projects/mokapot/data/10k_psms_test.pin");

	@TempDir
	Path tempDir;

	@Test
	void javaPotRuntimeIsComparableToMokapotSourceMainline() throws Exception {
		Assumptions.assumeTrue(
			Boolean.getBoolean("javapot.run.runtime_comparison"),
			"Set -Djavapot.run.runtime_comparison=true to run runtime comparison benchmark"
		);
		Assumptions.assumeTrue(Files.exists(PIN_FILE), "PIN test file not available");
		Assumptions.assumeTrue(Files.isDirectory(MOKAPOT_SOURCE), "mokapot source tree not available");

		int runs = Integer.getInteger("javapot.runtime.runs", 5);
		Assumptions.assumeTrue(runs > 0, "javapot.runtime.runs must be > 0");

		Path classpath = Path.of("target/classes").toAbsolutePath();
		String javaBase = "java -cp " + classpath + " org.searlelab.javapot.cli.JavaPotCli " +
			PIN_FILE + " --max_workers 1 --seed 1";
		String mokapotBase = "MPLCONFIGDIR=/tmp/mplcache PYTHONPATH=" + MOKAPOT_SOURCE +
			" conda run -n mokapot110 python -m mokapot.mokapot " + PIN_FILE + " --max_workers 1";

		// Warm-up run for each command.
		runTimedCommand(javaBase + " --dest_dir " + tempDir.resolve("java_warm"), Path.of("."));
		runTimedCommand(mokapotBase + " --dest_dir " + tempDir.resolve("mokapot_warm"), MOKAPOT_SOURCE);

		double[] javaTimes = new double[runs];
		double[] mokapotTimes = new double[runs];

		for (int i = 0; i < runs; i++) {
			javaTimes[i] = runTimedCommand(
				javaBase + " --dest_dir " + tempDir.resolve("java_" + i),
				Path.of(".")
			);
			mokapotTimes[i] = runTimedCommand(
				mokapotBase + " --dest_dir " + tempDir.resolve("mokapot_" + i),
				MOKAPOT_SOURCE
			);
		}

		Stats javaStats = Stats.from(javaTimes);
		Stats mokapotStats = Stats.from(mokapotTimes);
		double speedRatio = mokapotStats.meanSeconds / javaStats.meanSeconds;

		System.out.println("[BENCH] JavaPot runtime summary: " + javaStats);
		System.out.println("[BENCH] mokapot source runtime summary: " + mokapotStats);
		System.out.println("[BENCH] Speed ratio (mokapot/java): " + speedRatio + "x");

		assertTrue(
			javaStats.meanSeconds <= mokapotStats.meanSeconds * 1.10,
			"JavaPot should be similar or faster; observed mean java=" + javaStats.meanSeconds +
				"s vs mokapot=" + mokapotStats.meanSeconds + "s"
		);
	}

	private static double runTimedCommand(String command, Path cwd) throws IOException, InterruptedException {
		long start = System.nanoTime();
		int exit = runShell(command, cwd);
		assertTrue(exit == 0, "Command failed with exit=" + exit + ": " + command);
		long elapsed = System.nanoTime() - start;
		return elapsed / 1_000_000_000.0;
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

	private record Stats(double meanSeconds, double medianSeconds, double minSeconds, double maxSeconds, double stdSeconds) {
		private static Stats from(double[] values) {
			double[] sorted = Arrays.copyOf(values, values.length);
			Arrays.sort(sorted);

			double sum = 0.0;
			for (double value : sorted) {
				sum += value;
			}
			double mean = sum / sorted.length;
			double median = (sorted.length % 2 == 1)
				? sorted[sorted.length / 2]
				: (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
			double min = sorted[0];
			double max = sorted[sorted.length - 1];

			double variance = 0.0;
			for (double value : sorted) {
				double d = value - mean;
				variance += d * d;
			}
			double std = Math.sqrt(variance / sorted.length);

			return new Stats(mean, median, min, max, std);
		}
	}
}
