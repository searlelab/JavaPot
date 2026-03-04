package org.searlelab.javapot.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import org.searlelab.javapot.data.ColumnGroups;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.util.DeterministicRandom;
import org.searlelab.javapot.util.StableIntSort;

/**
 * FoldSplitter partitions PSM indices into deterministic cross-validation folds.
 * It assigns File+Scan spectrum groups to folds using Percolator-style random selection with fold capacities,
 * then repairs label-empty folds when feasible and shuffles rows within each fold deterministically.
 */
public final class FoldSplitter {
	private static final String DEFAULT_INPUT_FILE_ID = "input.pin";

	private FoldSplitter() {
	}

	/**
	 * Splits dataset rows into deterministic folds while keeping File+Scan groups intact.
	 */
	public static int[][] split(PsmDataset dataset, int folds, DeterministicRandom rng) {
		return split(dataset, folds, rng, DEFAULT_INPUT_FILE_ID);
	}

	/**
	 * Splits dataset rows into deterministic folds while keeping File+Scan groups intact.
	 */
	public static int[][] split(PsmDataset dataset, int folds, DeterministicRandom rng, String inputFileName) {
		if (folds < 2) {
			throw new IllegalArgumentException("folds must be >= 2");
		}
		if (dataset.size() == 0) {
			return new int[folds][0];
		}

		String resolvedInputFileId = normalizeInputFileId(inputFileName);
		GroupBuildResult build = buildGroups(dataset, resolvedInputFileId);
		Group[] groups = build.groups();
		int[] groupOrder = orderGroups(groups);

		int[] remainingCapacity = initialFoldCapacities(dataset.size(), folds);
		int[] groupToFold = assignGroups(groupOrder, groups, remainingCapacity, folds, rng);

		repairLabelEmptyFolds(groupToFold, groups, folds);

		int[][] out = toFoldRows(groupToFold, groups, folds);
		for (int[] foldRows : out) {
			rng.shuffle(foldRows);
		}
		return out;
	}

	private static GroupBuildResult buildGroups(PsmDataset dataset, String inputFileId) {
		ColumnGroups columns = dataset.columnGroups();
		String scanColumn = columns.optionalColumns().scan();
		String fileColumn = columns.optionalColumns().filename();
		Map<String, MutableGroup> groups = new LinkedHashMap<>();
		CRC32 crc32 = new CRC32();

		for (int row = 0; row < dataset.size(); row++) {
			String fileId = resolveFileId(dataset, row, fileColumn, inputFileId);
			String scan = resolveScanValue(dataset, row, scanColumn);
			String key = fileId + "\u0001" + scan;
			MutableGroup mutable = groups.get(key);
			if (mutable == null) {
				long hash = hashKey(key, crc32);
				mutable = new MutableGroup(key, hash);
				groups.put(key, mutable);
			}
			mutable.rows.add(row);
			if (dataset.targetAt(row)) {
				mutable.targets++;
			} else {
				mutable.decoys++;
			}
		}

		Group[] out = new Group[groups.size()];
		int i = 0;
		for (MutableGroup mutable : groups.values()) {
			out[i++] = mutable.freeze();
		}
		return new GroupBuildResult(out);
	}

	private static String normalizeInputFileId(String inputFileName) {
		if (inputFileName == null || inputFileName.isBlank()) {
			return DEFAULT_INPUT_FILE_ID;
		}
		return inputFileName;
	}

	private static String resolveFileId(PsmDataset dataset, int row, String fileColumn, String inputFileId) {
		if (fileColumn != null) {
			String value = dataset.valueAt(row, fileColumn);
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return inputFileId;
	}

	private static String resolveScanValue(PsmDataset dataset, int row, String scanColumn) {
		if (scanColumn != null) {
			String value = dataset.valueAt(row, scanColumn);
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		int scanIdx = findScanIndex(dataset);
		if (scanIdx >= 0) {
			return dataset.rawValueAt(row, scanIdx);
		}
		return Integer.toString(row);
	}

	private static int findScanIndex(PsmDataset dataset) {
		List<String> headers = dataset.headers();
		for (int i = 0; i < headers.size(); i++) {
			if (headers.get(i).equalsIgnoreCase("scannr")) {
				return i;
			}
		}
		return -1;
	}

	private static long hashKey(String key, CRC32 crc32) {
		crc32.reset();
		byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
		crc32.update(bytes, 0, bytes.length);
		return crc32.getValue();
	}

	private static int[] orderGroups(Group[] groups) {
		return StableIntSort.sortIndices(groups.length, (a, b) -> {
			int hashCmp = Long.compare(groups[a].hash, groups[b].hash);
			if (hashCmp != 0) {
				return hashCmp;
			}
			return groups[a].key.compareTo(groups[b].key);
		});
	}

	private static int[] initialFoldCapacities(int n, int folds) {
		int[] remain = new int[folds];
		int fold = folds;
		int residual = n;
		while (fold-- > 0) {
			remain[fold] = residual / (fold + 1);
			residual -= remain[fold];
		}
		return remain;
	}

	private static int[] assignGroups(
		int[] groupOrder,
		Group[] groups,
		int[] remainingCapacity,
		int folds,
		DeterministicRandom rng
	) {
		int[] groupToFold = new int[groups.length];
		Arrays.fill(groupToFold, -1);
		for (int groupIdx : groupOrder) {
			int fold = rng.nextInt(0, folds);
			while (remainingCapacity[fold] <= 0) {
				fold = rng.nextInt(0, folds);
			}
			groupToFold[groupIdx] = fold;
			remainingCapacity[fold] -= groups[groupIdx].rows.length;
		}
		return groupToFold;
	}

	private static void repairLabelEmptyFolds(int[] groupToFold, Group[] groups, int folds) {
		FoldStats stats = computeFoldStats(groupToFold, groups, folds);
		boolean changed;
		do {
			changed = repairMissingLabel(groupToFold, groups, folds, true, stats);
			changed |= repairMissingLabel(groupToFold, groups, folds, false, stats);
		} while (changed && (existsLabelEmptyFold(stats.targetsPerFold) || existsLabelEmptyFold(stats.decoysPerFold)));
	}

	private static boolean repairMissingLabel(
		int[] groupToFold,
		Group[] groups,
		int folds,
		boolean desiredTarget,
		FoldStats stats
	) {
		boolean changed = false;
		for (int recipient = 0; recipient < folds; recipient++) {
			int desiredCount = desiredTarget ? stats.targetsPerFold[recipient] : stats.decoysPerFold[recipient];
			if (desiredCount > 0) {
				continue;
			}
			MoveCandidate candidate = selectBestMove(groupToFold, groups, recipient, desiredTarget, stats);
			if (candidate == null) {
				continue;
			}
			moveGroup(groupToFold, groups, candidate.groupIdx, candidate.fromFold, recipient, stats);
			changed = true;
		}
		return changed;
	}

	private static MoveCandidate selectBestMove(
		int[] groupToFold,
		Group[] groups,
		int recipientFold,
		boolean desiredTarget,
		FoldStats stats
	) {
		MoveCandidate best = null;
		for (int groupIdx = 0; groupIdx < groups.length; groupIdx++) {
			int donorFold = groupToFold[groupIdx];
			if (donorFold == recipientFold) {
				continue;
			}
			Group group = groups[groupIdx];
			int desiredInGroup = desiredTarget ? group.targets : group.decoys;
			int oppositeInGroup = desiredTarget ? group.decoys : group.targets;
			if (desiredInGroup <= 0) {
				continue;
			}

			int donorDesired = desiredTarget ? stats.targetsPerFold[donorFold] : stats.decoysPerFold[donorFold];
			if (donorDesired - desiredInGroup < 1) {
				continue;
			}

			int donorOpposite = desiredTarget ? stats.decoysPerFold[donorFold] : stats.targetsPerFold[donorFold];
			boolean emptiesOpposite = donorOpposite > 0 && donorOpposite - oppositeInGroup <= 0;
			int donorSurplus = donorDesired - desiredInGroup;

			MoveCandidate candidate = new MoveCandidate(groupIdx, donorFold, emptiesOpposite, group.rows.length, donorSurplus, group.key);
			if (best == null || candidate.compareTo(best) < 0) {
				best = candidate;
			}
		}
		return best;
	}

	private static void moveGroup(
		int[] groupToFold,
		Group[] groups,
		int groupIdx,
		int fromFold,
		int toFold,
		FoldStats stats
	) {
		Group group = groups[groupIdx];
		groupToFold[groupIdx] = toFold;
		stats.targetsPerFold[fromFold] -= group.targets;
		stats.decoysPerFold[fromFold] -= group.decoys;
		stats.sizesPerFold[fromFold] -= group.rows.length;

		stats.targetsPerFold[toFold] += group.targets;
		stats.decoysPerFold[toFold] += group.decoys;
		stats.sizesPerFold[toFold] += group.rows.length;
	}

	private static boolean existsLabelEmptyFold(int[] counts) {
		for (int count : counts) {
			if (count == 0) {
				return true;
			}
		}
		return false;
	}

	private static FoldStats computeFoldStats(int[] groupToFold, Group[] groups, int folds) {
		int[] targets = new int[folds];
		int[] decoys = new int[folds];
		int[] sizes = new int[folds];
		for (int i = 0; i < groups.length; i++) {
			int fold = groupToFold[i];
			targets[fold] += groups[i].targets;
			decoys[fold] += groups[i].decoys;
			sizes[fold] += groups[i].rows.length;
		}
		return new FoldStats(targets, decoys, sizes);
	}

	private static int[][] toFoldRows(int[] groupToFold, Group[] groups, int folds) {
		List<List<Integer>> rowsByFold = new ArrayList<>(folds);
		for (int i = 0; i < folds; i++) {
			rowsByFold.add(new ArrayList<>());
		}
		for (int groupIdx = 0; groupIdx < groups.length; groupIdx++) {
			List<Integer> foldRows = rowsByFold.get(groupToFold[groupIdx]);
			for (int row : groups[groupIdx].rows) {
				foldRows.add(row);
			}
		}
		int[][] out = new int[folds][];
		for (int i = 0; i < folds; i++) {
			List<Integer> foldRows = rowsByFold.get(i);
			out[i] = new int[foldRows.size()];
			for (int j = 0; j < foldRows.size(); j++) {
				out[i][j] = foldRows.get(j);
			}
		}
		return out;
	}

	private static final class MutableGroup {
		private final String key;
		private final long hash;
		private final List<Integer> rows = new ArrayList<>();
		private int targets;
		private int decoys;

		private MutableGroup(String key, long hash) {
			this.key = key;
			this.hash = hash;
		}

		private Group freeze() {
			int[] rowArr = new int[rows.size()];
			for (int i = 0; i < rows.size(); i++) {
				rowArr[i] = rows.get(i);
			}
			return new Group(key, hash, rowArr, targets, decoys);
		}
	}

	private record Group(String key, long hash, int[] rows, int targets, int decoys) {
	}

	private record GroupBuildResult(Group[] groups) {
	}

	private record FoldStats(int[] targetsPerFold, int[] decoysPerFold, int[] sizesPerFold) {
	}

	private record MoveCandidate(
		int groupIdx,
		int fromFold,
		boolean emptiesOpposite,
		int groupSize,
		int donorSurplus,
		String key
	) implements Comparable<MoveCandidate> {
		@Override
		public int compareTo(MoveCandidate other) {
			int emptiesCmp = Boolean.compare(this.emptiesOpposite, other.emptiesOpposite);
			if (emptiesCmp != 0) {
				return emptiesCmp;
			}
			int sizeCmp = Integer.compare(this.groupSize, other.groupSize);
			if (sizeCmp != 0) {
				return sizeCmp;
			}
			int surplusCmp = -Integer.compare(this.donorSurplus, other.donorSurplus);
			if (surplusCmp != 0) {
				return surplusCmp;
			}
			int keyCmp = this.key.compareTo(other.key);
			if (keyCmp != 0) {
				return keyCmp;
			}
			return Integer.compare(this.groupIdx, other.groupIdx);
		}
	}
}
