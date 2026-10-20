package org.searlelab.javapot.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ParallelGridSearchTest {
	@Test
	void allPairBitsAndSelectedWeightsMatchFrozenSerialReference() {
		ExecutorService helpers = Executors.newFixedThreadPool(3);
		try {
			for (int size : new int[]{3, 4, 9, 37}) {
				double[][] x = new double[size][3];
				int[] y = new int[size];
				for (int i = 0; i < size; i++) {
					x[i] = new double[]{(i % 5)-2.0, (i*i % 13)-6.0, i == 0 ? -0.0 : 0.0};
					y[i] = i % 3 == 0 ? 0 : 1;
				}
				for (long seed : new long[]{1, 17, 123}) {
					double[] expected = GridSearchReference.scores(x, y, seed);
					assertArrayEquals(bits(expected), bits(ClassWeightGridSearch.scores(x, y, seed, helpers)));
					assertEquals(GridSearchReference.select(x, y, seed), ClassWeightGridSearch.select(x, y, seed, helpers));
				}
			}
			double[][] same = {{0}, {0}, {0}, {0}, {0}, {0}};
			int[] labels = {1, 0, 1, 0, 1, 0};
			assertEquals(GridSearchReference.select(same, labels, 1), ClassWeightGridSearch.select(same, labels, 1, helpers));
			assertEquals(GridSearchReference.select(new double[][]{{1}}, new int[]{1}, 1),
				ClassWeightGridSearch.select(new double[][]{{1}}, new int[]{1}, 1, helpers));
		} finally {
			helpers.shutdownNow();
		}
	}

	@Test
	@Timeout(10)
	void callerParticipationStaysWithinGlobalBudgetsWithoutNestedDeadlock() throws Exception {
		for (int foldCount : new int[]{2, 3, 5}) {
			for (int budget : new int[]{1, 2, 3, 6, 12, 24}) {
				int outerCount = Math.min(foldCount, budget);
				int expectedActive = Math.min(budget, 9 * foldCount);
				ExecutorService outer = Executors.newFixedThreadPool(outerCount);
				ExecutorService helpers = budget > outerCount ? Executors.newFixedThreadPool(budget-outerCount) : null;
				AtomicInteger active = new AtomicInteger();
				AtomicInteger peak = new AtomicInteger();
				AtomicInteger calls = new AtomicInteger();
				CountDownLatch saturated = new CountDownLatch(expectedActive);
				try {
					List<Future<double[]>> futures = new ArrayList<>();
					for (int fold = 0; fold < foldCount; fold++) {
						futures.add(outer.submit(() -> {
							Thread owner = Thread.currentThread();
							AtomicInteger callerPairs = new AtomicInteger();
							double[] scores = ClassWeightGridSearch.evaluatePairs(ordinal -> {
								if (Thread.currentThread() == owner) {
									callerPairs.incrementAndGet();
								}
								int current = active.incrementAndGet();
								peak.accumulateAndGet(current, Math::max);
								saturated.countDown();
								try {
									assertTrue(saturated.await(5, TimeUnit.SECONDS), "Training budget was not available");
									calls.incrementAndGet();
									return ordinal;
								} catch (InterruptedException failure) {
									Thread.currentThread().interrupt();
									throw new CancellationException("Test interrupted");
								} finally {
									active.decrementAndGet();
								}
							}, helpers);
							assertTrue(callerPairs.get() >= 1, "Each fold worker must evaluate a pair");
							return scores;
						}));
					}
					for (Future<double[]> future : futures) {
						assertArrayEquals(new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, future.get());
					}
					assertEquals(9 * foldCount, calls.get());
					assertEquals(expectedActive, peak.get());
				} finally {
					outer.shutdownNow();
					if (helpers != null) {
						helpers.shutdownNow();
						assertTrue(helpers.awaitTermination(5, TimeUnit.SECONDS));
					}
					assertTrue(outer.awaitTermination(5, TimeUnit.SECONDS));
				}
			}
		}
	}

	@Test
	void nonfiniteAndIrregularInputsMatchTheFrozenReference() throws Exception {
		ExecutorService helpers = Executors.newFixedThreadPool(3);
		try {
			for (double[][] x : new double[][][]{
				{{Double.NaN}, {1}, {-1}, {Double.POSITIVE_INFINITY}, {Double.NEGATIVE_INFINITY}, {-0.0}},
				{{1}, {2, 3}, {-1}, {-2, 4}, {3}, {-3}},
				{{1, 2}, {3}, {-1, 2}, {-3, 4}, {5, 6}, {-5, 6}},
				{{1}, null, {-1}, {-2}, {3}, {-3}}
			}) {
				int[] labels = {1, 1, 0, 0, 1, 0};
				RuntimeException referenceFailure = null;
				double[] expected = null;
				try {
					expected = GridSearchReference.scores(x, labels, 17);
				} catch (RuntimeException failure) {
					referenceFailure = failure;
				}
				if (referenceFailure == null) {
					assertArrayEquals(bits(expected), bits(ClassWeightGridSearch.scores(x, labels, 17, helpers)));
				} else {
					RuntimeException actual = assertThrows(RuntimeException.class,
						() -> ClassWeightGridSearch.scores(x, labels, 17, helpers));
					assertEquals(referenceFailure.getClass(), actual.getClass());
				}
			}
		} finally {
			helpers.shutdownNow();
			assertTrue(helpers.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void publicSerialSelectionRetainsItsExistingInterruptedCallerBehavior() {
		double[][] x = {{1}, {-1}, {2}, {-2}, {3}, {-3}};
		int[] y = {1, 0, 1, 0, 1, 0};
		try {
			Thread.currentThread().interrupt();
			assertEquals(GridSearchReference.select(x, y, 17), ClassWeightGridSearch.select(x, y, 17));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	@Timeout(10)
	void failuresUsePairOrderRatherThanCompletionOrder() throws Exception {
		ExecutorService helpers = Executors.newFixedThreadPool(3);
		CountDownLatch laterFailure = new CountDownLatch(1);
		List<Integer> failureOrder = new ArrayList<>();
		try {
			IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> ClassWeightGridSearch.evaluatePairs(ordinal -> {
					if (ordinal == 7) {
						failureOrder.add(7);
						laterFailure.countDown();
						throw new IllegalArgumentException("pair 7");
					}
					if (ordinal == 2) {
						try {
							assertTrue(laterFailure.await(5, TimeUnit.SECONDS));
						} catch (InterruptedException failure) {
							Thread.currentThread().interrupt();
							throw new CancellationException("Test interrupted");
						}
						failureOrder.add(2);
						throw new IllegalArgumentException("pair 2");
					}
					return ordinal;
				}, helpers));
			assertEquals(List.of(7, 2), failureOrder);
			assertEquals("pair 2", error.getMessage());
		} finally {
			helpers.shutdownNow();
			assertTrue(helpers.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void interruptionPreservesFlagAndCancelsPendingPairs() throws Exception {
		ExecutorService helpers = Executors.newSingleThreadExecutor();
		CountDownLatch release = new CountDownLatch(1);
		helpers.submit(() -> {
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		try {
			Thread.currentThread().interrupt();
			assertThrows(CancellationException.class, () -> ClassWeightGridSearch.evaluatePairs(i -> i, helpers));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
			release.countDown();
			helpers.shutdownNow();
			assertTrue(helpers.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	@Timeout(10)
	void cancellationDuringActiveEvaluationDrainsRunningAndQueuedPairs() throws Exception {
		ExecutorService helpers = Executors.newFixedThreadPool(3);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch blocked = new CountDownLatch(1);
		AtomicInteger active = new AtomicInteger();
		AtomicBoolean callerInterrupted = new AtomicBoolean();
		try {
			Future<?> invocation = caller.submit(() -> {
				try {
					ClassWeightGridSearch.evaluatePairs(ordinal -> {
						active.incrementAndGet();
						entered.countDown();
						try {
							blocked.await();
							return ordinal;
						} catch (InterruptedException failure) {
							Thread.currentThread().interrupt();
							throw new CancellationException("Evaluation interrupted");
						} finally {
							active.decrementAndGet();
						}
					}, helpers);
				} finally {
					callerInterrupted.set(Thread.currentThread().isInterrupted());
				}
			});
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			assertTrue(invocation.cancel(true));
			caller.shutdown();
			assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS));
			helpers.shutdownNow();
			assertTrue(helpers.awaitTermination(5, TimeUnit.SECONDS));
			assertEquals(0, active.get());
			assertTrue(callerInterrupted.get());
		} finally {
			blocked.countDown();
			caller.shutdownNow();
			helpers.shutdownNow();
		}
	}

	private static long[] bits(double[] values) {
		long[] out = new long[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = Double.doubleToRawLongBits(values[i]);
		}
		return out;
	}
}
