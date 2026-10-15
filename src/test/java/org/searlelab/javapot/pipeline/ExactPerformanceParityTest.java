package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExactPerformanceParityTest {
	@TempDir
	Path directory;

	@Test
	void simultaneousApiCallsKeepIndependentFoldStorage() throws Exception {
		String expected = Files.readString(Path.of("src/test/resources/data/performance-baseline.sha256")).trim();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<String> first = pool.submit(() -> ExactParitySnapshot.capture(directory.resolve("first")));
			Future<String> second = pool.submit(() -> ExactParitySnapshot.capture(directory.resolve("second")));
			assertEquals(expected, first.get());
			assertEquals(expected, second.get());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void preservesBaselineModelsScoresFoldsAndOutputBits() throws Exception {
		String expected = Files.readString(Path.of("src/test/resources/data/performance-baseline.sha256")).trim();
		assertEquals(expected, ExactParitySnapshot.capture(directory.resolve("ordinary")));
		String previous = System.getProperty("javapot.benchmark.diagnostics");
		try {
			System.setProperty("javapot.benchmark.diagnostics", "true");
			assertEquals(expected, ExactParitySnapshot.capture(directory.resolve("diagnostic")));
		} finally {
			if (previous == null) {
				System.clearProperty("javapot.benchmark.diagnostics");
			} else {
				System.setProperty("javapot.benchmark.diagnostics", previous);
			}
		}
	}
}
