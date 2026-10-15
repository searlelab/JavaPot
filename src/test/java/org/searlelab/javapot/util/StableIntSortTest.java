package org.searlelab.javapot.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;

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

	@Test
	void scratchReusePreservesFrozenStableOrdering() {
		Random random = new Random(391);
		int[] scratch = new int[1025];
		for (int size : new int[]{0, 1, 2, 3, 7, 8, 9, 31, 32, 33, 1023, 1024, 1025}) {
			int[] keys = new int[size];
			for (int trial = 0; trial < 8; trial++) {
				for (int i = 0; i < size; i++) {
					keys[i] = trial == 0 ? i : trial == 1 ? -i : trial == 2 ? 0 : random.nextInt(12);
				}
				int[] expected = StableIntSortReference.sortIndices(size, (a, b) -> Integer.compare(keys[a], keys[b]));
				int[] actual = StableIntSort.sortIndices(size, (a, b) -> Integer.compare(keys[a], keys[b]), scratch);
				assertArrayEquals(expected, actual);
				StableIntSort.sortIndices(size, (a, b) -> Integer.compare(keys[b], keys[a]), scratch);
				assertArrayEquals(expected, actual, "A subsequent sort must not change the previous result");
			}
		}
		assertThrows(IllegalArgumentException.class, () -> StableIntSort.sortIndices(2, Integer::compare, new int[1]));
	}
}
