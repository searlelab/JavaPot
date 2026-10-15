package org.searlelab.javapot.util;

/**
 * StableIntSort provides deterministic stable sorting of integer index arrays.
 * It is used in hot paths where object boxing from standard sort utilities is undesirable.
 */
public final class StableIntSort {
	private StableIntSort() {
	}

	/**
	 * IntComparator compares two index values during stable sorting.
	 */
	@FunctionalInterface
	public interface IntComparator {
		int compare(int left, int right);
	}

	/**
	 * Returns stable-sorted integer indices [0, size) using the provided comparator.
	 */
	public static int[] sortIndices(int size, IntComparator comparator) {
		if (size < 0) {
			throw new IllegalArgumentException("size must be >= 0");
		}
		return sortIndices(size, comparator, new int[size]);
	}

	/**
	 * Returns stable-sorted indices using caller-owned merge scratch, which may be reused after return.
	 * The result never aliases scratch. Callers must not share scratch between simultaneous sorts.
	 */
	public static int[] sortIndices(int size, IntComparator comparator, int[] scratch) {
		if (size < 0 || scratch.length < size) {
			throw new IllegalArgumentException("size must be >= 0 and scratch must hold size indices");
		}
		int[] values = new int[size];
		sortIndicesInto(values, comparator, scratch);
		return values;
	}

	/**
	 * Initializes and stable-sorts caller-owned indices. Neither array may be shared during the call.
	 */
	public static void sortIndicesInto(int[] values, IntComparator comparator, int[] scratch) {
		int size = values.length;
		if (scratch == values || scratch.length < size) {
			throw new IllegalArgumentException("scratch must be independent and hold size indices");
		}
		for (int i = 0; i < size; i++) {
			values[i] = i;
		}
		if (size < 2) {
			return;
		}

		int[] src = values;
		int[] dst = scratch;
		for (int width = 1; width < size; width <<= 1) {
			for (int left = 0; left < size; left += width << 1) {
				int mid = Math.min(size, left + width);
				int right = Math.min(size, left + (width << 1));
				merge(src, dst, left, mid, right, comparator);
			}
			int[] tmp = src;
			src = dst;
			dst = tmp;
		}
		if (src != values) {
			System.arraycopy(src, 0, values, 0, size);
		}
	}

	private static void merge(
		int[] src,
		int[] dst,
		int left,
		int mid,
		int right,
		IntComparator comparator
	) {
		// Already ordered runs retain their stable order without element-by-element merging.
		if (mid == right || comparator.compare(src[mid - 1], src[mid]) <= 0) {
			System.arraycopy(src, left, dst, left, right - left);
			return;
		}
		// Strictly separated runs in reverse order can be exchanged without changing tie order.
		if (comparator.compare(src[left], src[right - 1]) > 0) {
			System.arraycopy(src, mid, dst, left, right - mid);
			System.arraycopy(src, left, dst, left + right - mid, mid - left);
			return;
		}
		int li = left;
		int ri = mid;
		int out = left;
		while (li < mid && ri < right) {
			int l = src[li];
			int r = src[ri];
			if (comparator.compare(l, r) <= 0) {
				dst[out++] = l;
				li++;
			} else {
				dst[out++] = r;
				ri++;
			}
		}
		while (li < mid) {
			dst[out++] = src[li++];
		}
		while (ri < right) {
			dst[out++] = src[ri++];
		}
	}
}
