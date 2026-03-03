package org.searlelab.javapot.util;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * DeterministicRandom wraps {@link SplittableRandom} with helpers used throughout training.
 * It centralizes reproducible shuffling, permutation, and sampling behavior.
 */
public final class DeterministicRandom {
	private final SplittableRandom random;

	public DeterministicRandom(long seed) {
		this.random = new SplittableRandom(seed);
	}

	public DeterministicRandom(SplittableRandom random) {
		this.random = random;
	}

	public int nextInt(int originInclusive, int boundExclusive) {
		return random.nextInt(originInclusive, boundExclusive);
	}

	public long nextLong() {
		return random.nextLong();
	}

	public double nextDouble() {
		return random.nextDouble();
	}

	public int[] permutation(int n) {
		int[] arr = new int[n];
		for (int i = 0; i < n; i++) {
			arr[i] = i;
		}
		shuffle(arr);
		return arr;
	}

	public void shuffle(int[] arr) {
		for (int i = arr.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int tmp = arr[i];
			arr[i] = arr[j];
			arr[j] = tmp;
		}
	}

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

	public SplittableRandom split() {
		return random.split();
	}
}
