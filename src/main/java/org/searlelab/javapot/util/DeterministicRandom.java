package org.searlelab.javapot.util;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * DeterministicRandom wraps {@link SplittableRandom} with helpers used throughout training.
 * It centralizes reproducible shuffling, permutation, and sampling behavior.
 */
public final class DeterministicRandom {
	private final SplittableRandom random;

	/**
	 * Creates a deterministic random source from a fixed seed.
	 */
	public DeterministicRandom(long seed) {
		this.random = new SplittableRandom(seed);
	}

	/**
	 * Wraps an existing {@link SplittableRandom} instance.
	 */
	public DeterministicRandom(SplittableRandom random) {
		this.random = random;
	}

	/**
	 * Returns an integer in the requested half-open range.
	 */
	public int nextInt(int originInclusive, int boundExclusive) {
		return random.nextInt(originInclusive, boundExclusive);
	}

	/**
	 * Returns the next random long value.
	 */
	public long nextLong() {
		return random.nextLong();
	}

	/**
	 * Returns the next random double in [0,1).
	 */
	public double nextDouble() {
		return random.nextDouble();
	}

	/**
	 * Returns a shuffled permutation of integers [0, n).
	 */
	public int[] permutation(int n) {
		int[] arr = new int[n];
		for (int i = 0; i < n; i++) {
			arr[i] = i;
		}
		shuffle(arr);
		return arr;
	}

	/**
	 * Shuffles an int array in place using Fisher-Yates.
	 */
	public void shuffle(int[] arr) {
		for (int i = arr.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int tmp = arr[i];
			arr[i] = arr[j];
			arr[j] = tmp;
		}
	}

	/**
	 * Samples k unique values without replacement from the input array.
	 */
	public int[] choiceWithoutReplacement(int[] input, int k) {
		if (k >= input.length) {
			return Arrays.copyOf(input, input.length);
		}
		int[] copy = Arrays.copyOf(input, input.length);
		for (int i = 0; i < k; i++) {
			int j = i + random.nextInt(copy.length - i);
			int tmp = copy[i];
			copy[i] = copy[j];
			copy[j] = tmp;
		}
		return Arrays.copyOf(copy, k);
	}

	/**
	 * Splits into an independent deterministic random stream.
	 */
	public SplittableRandom split() {
		return random.split();
	}
}
