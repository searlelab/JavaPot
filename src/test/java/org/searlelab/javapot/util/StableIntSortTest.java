package org.searlelab.javapot.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StableIntSortTest {
	@Test
	void sortIndicesIsStableForTies() {
		int[] keys = new int[]{2, 1, 1, 3, 1};
		int[] sorted = StableIntSort.sortIndices(keys.length, (a, b) -> Integer.compare(keys[a], keys[b]));
		assertArrayEquals(new int[]{1, 2, 4, 0, 3}, sorted);
	}

	@Test
	void sortIndicesSupportsDescendingOrder() {
		int[] keys = new int[]{2, 1, 4, 3};
		int[] sorted = StableIntSort.sortIndices(keys.length, (a, b) -> Integer.compare(keys[b], keys[a]));
		assertArrayEquals(new int[]{2, 3, 0, 1}, sorted);
	}

	@Test
	void sortIndicesValidatesSize() {
		assertThrows(IllegalArgumentException.class, () -> StableIntSort.sortIndices(-1, Integer::compare));
	}
}
