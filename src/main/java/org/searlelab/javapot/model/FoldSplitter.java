package org.searlelab.javapot.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

import gnu.trove.map.hash.THashMap;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.util.DeterministicRandom;
import org.searlelab.javapot.util.StableIntSort;

/**
 * FoldSplitter partitions PSM indices into deterministic cross-validation folds.
 * It hashes spectrum-group identifiers to keep related PSMs together, then balances fold sizes and shuffles within folds.
 */
public final class FoldSplitter {
	private static final byte[] OPEN_TUPLE = "(".getBytes(StandardCharsets.UTF_8);
	private static final byte[] CLOSE_TUPLE = ")".getBytes(StandardCharsets.UTF_8);
	private static final byte[] COMMA = ",".getBytes(StandardCharsets.UTF_8);
	private static final byte[] COMMA_SPACE = ", ".getBytes(StandardCharsets.UTF_8);

	private FoldSplitter() {
	}

	/**
	 * Splits dataset rows into deterministic folds while keeping spectrum groups intact.
	 */
	public static int[][] split(PsmDataset dataset, int folds, DeterministicRandom rng) {
		int n = dataset.size();
		long[] hashes = new long[n];
		int[] spectrumColIdx = dataset.spectrumColIndices();
		THashMap<String, HashScalar> scalarCache = new THashMap<>();
		CRC32 crc32 = new CRC32();
		for (int i = 0; i < n; i++) {
			hashes[i] = hashSpectrum(dataset, i, spectrumColIdx, scalarCache, crc32);
		}

		int[] sortedIdx = StableIntSort.sortIndices(n, (a, b) -> Long.compare(hashes[a], hashes[b]));

		long[] sortedHashes = new long[n];
		for (int i = 0; i < n; i++) {
			sortedHashes[i] = hashes[sortedIdx[i]];
		}

		int[] idxStartUnique = uniqueStartIndices(sortedHashes);
		int foldSize = n / folds;
		int remainder = n % folds;
		int[] startSplitIndices = new int[folds - 1];
		int start = 0;
		for (int i = 0; i < folds - 1; i++) {
			int end = start + foldSize + (i < remainder ? 1 : 0);
			startSplitIndices[i] = end;
			start = end;
		}

		int[] idxSplit = new int[folds - 1];
		for (int i = 0; i < startSplitIndices.length; i++) {
			idxSplit[i] = searchSortedFirstGreaterOrEqual(idxStartUnique, startSplitIndices[i]);
		}

		List<int[]> chunks = splitByPositions(sortedIdx, idxSplit);
		for (int[] foldIdx : chunks) {
			rng.shuffle(foldIdx);
		}
		return chunks.toArray(int[][]::new);
	}

	private static long hashSpectrum(
		PsmDataset dataset,
		int rowIdx,
		int[] spectrumColIdx,
		THashMap<String, HashScalar> scalarCache,
		CRC32 crc32
	) {
		crc32.reset();
		crc32.update(OPEN_TUPLE, 0, OPEN_TUPLE.length);
		for (int i = 0; i < spectrumColIdx.length; i++) {
			if (i > 0) {
				crc32.update(COMMA_SPACE, 0, COMMA_SPACE.length);
			}
			String raw = dataset.rawValueAt(rowIdx, spectrumColIdx[i]);
			HashScalar scalar = scalarCache.get(raw);
			if (scalar == null) {
				scalar = toHashScalar(raw);
				scalarCache.put(raw, scalar);
			}
			crc32.update(scalar.utf8(), 0, scalar.utf8().length);
		}
		if (spectrumColIdx.length == 1) {
			crc32.update(COMMA, 0, COMMA.length);
		}
		crc32.update(CLOSE_TUPLE, 0, CLOSE_TUPLE.length);
		return crc32.getValue();
	}

	private static HashScalar toHashScalar(String value) {
		String text = toPythonScalar(value);
		return new HashScalar(text.getBytes(StandardCharsets.UTF_8));
	}

	private static String toPythonScalar(String value) {
		String v = value.trim();
		try {
			double parsed = Double.parseDouble(v);
			return Double.toString(parsed);
		} catch (NumberFormatException ignored) {
			// no-op
		}
		return "'" + v.replace("'", "\\'") + "'";
	}

	private static int[] uniqueStartIndices(long[] sorted) {
		if (sorted.length == 0) {
			return new int[0];
		}
		int[] tmp = new int[sorted.length];
		int c = 0;
		tmp[c++] = 0;
		for (int i = 1; i < sorted.length; i++) {
			if (sorted[i] != sorted[i - 1]) {
				tmp[c++] = i;
			}
		}
		return Arrays.copyOf(tmp, c);
	}

	private static int searchSortedFirstGreaterOrEqual(int[] arr, int target) {
		int lo = 0;
		int hi = arr.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (arr[mid] < target) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		if (lo >= arr.length) {
			return arr.length;
		}
		return arr[lo];
	}

	private static List<int[]> splitByPositions(int[] values, int[] splitPositions) {
		List<int[]> out = new ArrayList<>();
		int start = 0;
		for (int pos : splitPositions) {
			out.add(Arrays.copyOfRange(values, start, pos));
			start = pos;
		}
		out.add(Arrays.copyOfRange(values, start, values.length));
		return out;
	}

	/**
	 * HashScalar caches the UTF-8 representation of a normalized spectrum scalar.
	 */
	private record HashScalar(byte[] utf8) {
	}
}
