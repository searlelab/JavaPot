package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.searlelab.javapot.cli.CliParser;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.io.PinFileParser;
import org.searlelab.javapot.model.FoldSplitter;
import org.searlelab.javapot.util.DeterministicRandom;

class ParallelGridRecoveryTest {
	@TempDir
	Path directory;

	@Test
	@Timeout(20)
	void noLabelRecoveryPreservesFallbackAndStopsAttemptWorkers() throws Exception {
		Path pin = directory.resolve("single_target_sparse.pin");
		StringBuilder text = new StringBuilder("SpecId\tLabel\tScanNr\tExpMass\tfeat\tPeptide\tProteins\n");
		text.append("target\t1\t0\t500\t10\tTARGET\tP\n");
		for (int i = 1; i <= 6; i++) {
			text.append("d").append(i).append("\t-1\t").append(i).append("\t500\t0\tDECOY").append(i).append("\tD\n");
		}
		Files.writeString(pin, text);
		for (int workers : new int[]{6, 12, 24}) {
			Path out = directory.resolve("fallback-" + workers);
			JavaPotOptions options = CliParser.parse(new String[]{pin.toString(), "--dest_dir", out.toString(), "--quiet",
				"--max_workers", Integer.toString(workers), "--max_iter", "3", "--seed", "17", "--write_model_files"});
			Set<Long> before = poolThreads();
			JavaPotRunResult result = JavaPotApi.run(options);
			for (JavaPotPeptide row : result.psms()) {
				assertEquals(1.0, row.qValue());
				assertEquals(1.0, row.pep());
				assertTrue(Double.isFinite(row.score()));
			}
			assertFalse(Files.exists(options.saveModelFile()));
			assertNoNewPoolThreads(before);
		}
	}

	@Test
	@Timeout(20)
	void malformedFoldPreservesCauseAndDrainsAllWorkers() throws Exception {
		Fixture fixture = fixture();
		fixture.dataset.rawFeatures()[3] = new double[0];
		Set<Long> before = poolThreads();
		InvocationTargetException failure = assertThrows(InvocationTargetException.class,
			() -> trainingMethod().invoke(null, fixture.dataset, fixture.folds, fixture.options, fixture.rng, true));
		assertEquals("Training failed", failure.getCause().getMessage());
		assertTrue(failure.getCause().getCause() instanceof IndexOutOfBoundsException);
		assertNoNewPoolThreads(before);
	}

	@Test
	@Timeout(20)
	void interruptedInvocationPreservesFlagAndDrainsWorkers() throws Exception {
		Fixture fixture = fixture();
		Set<Long> before = poolThreads();
		try {
			Thread.currentThread().interrupt();
			InvocationTargetException failure = assertThrows(InvocationTargetException.class,
				() -> trainingMethod().invoke(null, fixture.dataset, fixture.folds, fixture.options, fixture.rng, true));
			assertEquals("Training interrupted", failure.getCause().getMessage());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertNoNewPoolThreads(before);
	}

	@Test
	@Timeout(20)
	void defaultApiRejectsInterruptedTrainingWithoutStartingWorkers() throws Exception {
		Fixture fixture = fixture();
		Set<Long> before = poolThreads();
		try {
			Thread.currentThread().interrupt();
			RuntimeException failure = assertThrows(RuntimeException.class, () -> JavaPotApi.run(fixture.options));
			assertEquals("Training interrupted", failure.getMessage());
			assertTrue(failure.getCause() instanceof InterruptedException);
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertNoNewPoolThreads(before);
	}

	@Test
	@Timeout(20)
	@ResourceLock(Resources.SYSTEM_OUT)
	void legacyInterruptedInvocationReturnsWhileFoldWorkersAreBlocked() throws Exception {
		assertLegacyReturnsWhileWorkersAreBlocked(true);
	}

	@Test
	@Timeout(20)
	@ResourceLock(Resources.SYSTEM_OUT)
	void legacyFailedInvocationReturnsWhileOtherFoldWorkersAreBlocked() throws Exception {
		assertLegacyReturnsWhileWorkersAreBlocked(false);
	}

	private void assertLegacyReturnsWhileWorkersAreBlocked(boolean interruptCaller) throws Exception {
		Fixture fixture = fixture();
		JavaPotOptions options = CliParser.parse(new String[]{fixture.options.pinFile().toString(),
			"--train_fdr", "0.5", "--max_workers", "12", "--max_iter", "2"});
		Method method = trainingMethod();
		CountDownLatch workerBlocked = new CountDownLatch(interruptCaller ? 3 : 2);
		CountDownLatch releaseWorkers = new CountDownLatch(1);
		CountDownLatch callerReturned = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		AtomicBoolean callerInterrupted = new AtomicBoolean();
		Set<Thread> workers = new HashSet<>();
		RuntimeException foldFailure = new IllegalArgumentException("Injected fold failure");
		PrintStream original = System.out;
		try (PrintStream blockedOutput = new PrintStream(OutputStream.nullOutputStream()) {
			@Override
			public void println(String text) {
				if (!text.startsWith("Analyzing Fold ")) {
					return;
				}
				synchronized (workers) {
					workers.add(Thread.currentThread());
				}
				if (!interruptCaller && text.equals("Analyzing Fold 1...")) {
					throw foldFailure;
				}
				workerBlocked.countDown();
				// Hold the worker independently of cancel(true), just like legacy training.
				boolean interrupted = false;
				while (true) {
					try {
						releaseWorkers.await();
						break;
					} catch (InterruptedException e) {
						interrupted = true;
					}
				}
				if (interrupted) {
					Thread.currentThread().interrupt();
				}
			}
		}) {
			Thread caller = new Thread(() -> {
				if (interruptCaller) {
					Thread.currentThread().interrupt();
				}
				try {
					method.invoke(null, fixture.dataset, fixture.folds, options, fixture.rng, false);
				} catch (InvocationTargetException e) {
					failure.set(e.getCause());
				} catch (Exception e) {
					failure.set(e);
				} finally {
					callerInterrupted.set(Thread.currentThread().isInterrupted());
					callerReturned.countDown();
				}
			}, "legacy-training-caller");
			System.setOut(blockedOutput);
			try {
				caller.start();
				assertTrue(workerBlocked.await(5, TimeUnit.SECONDS), "No fold worker reached the gate");
				assertTrue(callerReturned.await(5, TimeUnit.SECONDS), "Legacy caller waited for a blocked fold worker");
				assertEquals(interruptCaller ? "Training interrupted" : "Training failed", failure.get().getMessage());
				assertEquals(interruptCaller, callerInterrupted.get());
				if (interruptCaller) {
					assertTrue(failure.get().getCause() instanceof InterruptedException);
				} else {
					assertTrue(failure.get().getCause() == foldFailure);
				}
			} finally {
				releaseWorkers.countDown();
				caller.join(5000);
				assertFalse(caller.isAlive(), "Caller did not stop after releasing workers");
				// All workers reached the logging gate before the return assertion.
				Thread[] remaining;
				synchronized (workers) {
					remaining = workers.toArray(new Thread[0]);
				}
				for (Thread worker : remaining) {
					worker.join(5000);
					assertFalse(worker.isAlive(), "Released worker did not terminate");
				}
			}
		} finally {
			System.setOut(original);
		}
	}

	private Fixture fixture() throws Exception {
		Path pin = directory.resolve("errors.pin");
		StringBuilder text = new StringBuilder("SpecId\tLabel\tScanNr\tExpMass\tfeat\tPeptide\tProteins\n");
		for (int row = 0; row < 120; row++) {
			text.append("p").append(row).append('\t').append(row % 3 == 0 ? -1 : 1).append('\t').append(row)
				.append("\t500\t").append(row % 3 == 0 ? 0 : 10).append("\tPEP").append(row).append("\tP\n");
		}
		Files.writeString(pin, text);
		PsmDataset dataset = PinFileParser.read(pin);
		DeterministicRandom rng = new DeterministicRandom(1);
		int[][] folds = FoldSplitter.split(dataset, 3, rng, pin.getFileName().toString());
		JavaPotOptions options = CliParser.parse(new String[]{pin.toString(), "--dest_dir", directory.resolve("out").toString(),
			"--quiet", "--train_fdr", "0.5", "--max_workers", "6", "--max_iter", "2"});
		return new Fixture(dataset, folds, options, rng);
	}

	private static Method trainingMethod() throws Exception {
		Method method = JavaPotRunner.class.getDeclaredMethod("trainModels", PsmDataset.class, int[][].class,
			JavaPotOptions.class, DeterministicRandom.class, boolean.class);
		method.setAccessible(true);
		return method;
	}

	private static Set<Long> poolThreads() {
		Set<Long> ids = new HashSet<>();
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (thread.isAlive() && thread.getName().startsWith("pool-")) {
				ids.add(thread.getId());
			}
		}
		return ids;
	}

	private static void assertNoNewPoolThreads(Set<Long> before) {
		Set<Long> after = poolThreads();
		after.removeAll(before);
		assertTrue(after.isEmpty(), "Invocation left worker threads alive: " + after);
	}

	private static final class Fixture {
		private final PsmDataset dataset;
		private final int[][] folds;
		private final JavaPotOptions options;
		private final DeterministicRandom rng;

		private Fixture(PsmDataset dataset, int[][] folds, JavaPotOptions options, DeterministicRandom rng) {
			this.dataset = dataset;
			this.folds = folds;
			this.options = options;
			this.rng = rng;
		}
	}
}
