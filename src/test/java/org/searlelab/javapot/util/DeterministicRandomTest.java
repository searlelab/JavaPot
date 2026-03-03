package org.searlelab.javapot.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DeterministicRandomTest {
	@Test
	void sameSeedProducesSameSequenceAndPermutation() {
		DeterministicRandom a = new DeterministicRandom(42L);
		DeterministicRandom b = new DeterministicRandom(42L);

		assertEquals(a.nextInt(0, 1000), b.nextInt(0, 1000));
		assertEquals(a.nextLong(), b.nextLong());
		assertEquals(a.nextDouble(), b.nextDouble(), 1e-12);
		assertArrayEquals(a.permutation(12), b.permutation(12));
	}

	@Test
	void shuffleAndChoiceWithoutReplacementAreDeterministic() {
		int[] input = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
		DeterministicRandom a = new DeterministicRandom(7L);
		DeterministicRandom b = new DeterministicRandom(7L);

		int[] shuffledA = input.clone();
		int[] shuffledB = input.clone();
		a.shuffle(shuffledA);
		b.shuffle(shuffledB);
		assertArrayEquals(shuffledA, shuffledB);
		assertNotEquals("0,1,2,3,4,5,6,7", join(shuffledA));

		int[] choiceA = a.choiceWithoutReplacement(input, 4);
		int[] choiceB = b.choiceWithoutReplacement(input, 4);
		assertArrayEquals(choiceA, choiceB);
		assertEquals(4, choiceA.length);
	}

	@Test
	void choiceWithoutReplacementReturnsCopyWhenKTooLarge() {
		int[] input = new int[]{3, 4, 5};
		DeterministicRandom rng = new DeterministicRandom(1L);

		int[] chosen = rng.choiceWithoutReplacement(input, 10);
		assertArrayEquals(input, chosen);
	}

	@Test
	void splitProducesDeterministicChildGenerator() {
		DeterministicRandom a = new DeterministicRandom(99L);
		DeterministicRandom b = new DeterministicRandom(99L);

		long childA = a.split().nextLong();
		long childB = b.split().nextLong();
		assertEquals(childA, childB);
	}

	private static String join(int[] arr) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < arr.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(arr[i]);
		}
		return sb.toString();
	}
}
