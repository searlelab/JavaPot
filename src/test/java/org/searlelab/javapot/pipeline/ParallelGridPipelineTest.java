package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParallelGridPipelineTest {
	@TempDir
	Path directory;

	@Test
	void allBudgetsPreserveFrozenModelsAndOrderedApiOutput() throws Exception {
		String expected = Files.readString(Path.of("src/test/resources/data/performance-baseline.sha256")).trim();
		for (int workers : new int[]{1, 2, 3, 6, 12, 24, 32}) {
			assertEquals(expected, ExactParitySnapshot.capture(directory.resolve("workers-" + workers), true, workers));
		}
	}

	@Test
	void independentInvocationsDoNotShareWorkersOrStorage() throws Exception {
		String expected = Files.readString(Path.of("src/test/resources/data/performance-baseline.sha256")).trim();
		ExecutorService callers = Executors.newFixedThreadPool(2);
		try {
			Future<String> first = callers.submit(() -> ExactParitySnapshot.capture(directory.resolve("first"), true, 6));
			Future<String> second = callers.submit(() -> ExactParitySnapshot.capture(directory.resolve("second"), true, 12));
			assertEquals(expected, first.get());
			assertEquals(expected, second.get());
		} finally {
			callers.shutdownNow();
			callers.awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}
