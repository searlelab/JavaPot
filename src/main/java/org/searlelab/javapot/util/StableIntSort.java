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

	public static int[] sortIndices(int size, IntComparator comparator) {
		if (size < 0) {
			throw new IllegalArgumentException("size must be >= 0");
		}
		int[] values = new int[size];
		for (int i = 0; i < size; i++) {
			values[i] = i;
		}
		if (size < 2) {
			return values;
		}

		int[] scratch = new int[size];
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
		return values;
	}

	private static void merge(
		int[] src,
		int[] dst,
		int left,
		int mid,
		int right,
		IntComparator comparator
	) {
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
