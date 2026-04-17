package org.searlelab.javapot.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PsmDataset is the in-memory representation of a parsed PIN table.
 * It exposes typed access to targets, features, peptide strings, and spectrum-group columns used by training and scoring.
 */
public final class PsmDataset {
	private final ColumnGroups columnGroups;
	private final List<String> headers;
	private final String[][] rows;
	private final boolean[] targets;
	private final double[][] features;
	private final String[] featureNames;
	private final int[] spectrumColIndices;
	private final int[] psmGroups;
	private final boolean hasNonTrivialPsmGroup;
	private final Map<String, Integer> featureIndex;
	private final Map<String, Integer> columnIndex;

	/**
	 * Builds a typed dataset view from parsed header and row values.
	 */
	public PsmDataset(ColumnGroups columnGroups, List<String> headers, String[][] rows) {
		this.columnGroups = columnGroups;
		this.headers = List.copyOf(headers);
		this.rows = rows;
		this.columnIndex = buildIndex(headers);
		this.targets = parseTargets();
		this.featureNames = columnGroups.featureColumns().toArray(String[]::new);
		this.spectrumColIndices = parseSpectrumColIndices();
		this.psmGroups = parsePsmGroups();
		this.hasNonTrivialPsmGroup = detectNonTrivialPsmGroup(psmGroups);
		this.featureIndex = buildIndex(columnGroups.featureColumns());
		this.features = parseFeatures();
	}

	private static Map<String, Integer> buildIndex(List<String> names) {
		Map<String, Integer> out = new HashMap<>();
		for (int i = 0; i < names.size(); i++) {
			out.put(names.get(i), i);
		}
		return out;
	}

	private boolean[] parseTargets() {
		int idx = colIndex(columnGroups.targetColumn());
		boolean[] out = new boolean[rows.length];
		for (int i = 0; i < rows.length; i++) {
			out[i] = TargetConverter.toBoolean(rows[i][idx]);
		}
		return out;
	}

	private double[][] parseFeatures() {
		double[][] out = new double[rows.length][featureNames.length];
		for (int i = 0; i < rows.length; i++) {
			for (int j = 0; j < featureNames.length; j++) {
				String value = rows[i][colIndex(featureNames[j])];
				if (value == null || value.isBlank()) {
					throw new IllegalArgumentException("Missing value in feature '" + featureNames[j] + "' at row " + i);
				}
				out[i][j] = Double.parseDouble(value);
			}
		}
		return out;
	}

	private int[] parsePsmGroups() {
		String psmGroupColumn = columnGroups.optionalColumns().psmGroup();
		if (psmGroupColumn == null) {
			return null;
		}
		int idx = colIndex(psmGroupColumn);
		int[] out = new int[rows.length];
		for (int i = 0; i < rows.length; i++) {
			String value = rows[i][idx];
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException("Missing value in psm_group at row " + i);
			}
			try {
				double parsed = Double.parseDouble(value);
				if (!Double.isFinite(parsed)) {
					throw new NumberFormatException("Non-finite value");
				}
				out[i] = (int) Math.round(parsed);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid psm_group '" + value + "' at row " + i, e);
			}
			if (out[i] < 1) {
				throw new IllegalArgumentException("psm_group must be >= 1 at row " + i + ": " + out[i]);
			}
		}
		return out;
	}

	private static boolean detectNonTrivialPsmGroup(int[] psmGroups) {
		if (psmGroups == null) {
			return false;
		}
		Set<Integer> distinct = new HashSet<>();
		for (int psmGroup : psmGroups) {
			distinct.add(psmGroup);
			if (distinct.size() > 1) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the number of PSM rows in this dataset.
	 */
	public int size() {
		return rows.length;
	}

	/**
	 * Returns the number of modeled feature columns.
	 */
	public int featureCount() {
		return featureNames.length;
	}

	/**
	 * Returns feature names in model column order.
	 */
	public String[] featureNames() {
		return Arrays.copyOf(featureNames, featureNames.length);
	}

	/**
	 * Returns inferred column grouping metadata.
	 */
	public ColumnGroups columnGroups() {
		return columnGroups;
	}

	/**
	 * Returns a defensive copy of target/decoy labels.
	 */
	public boolean[] targets() {
		return Arrays.copyOf(targets, targets.length);
	}

	/**
	 * Returns the target/decoy label for one row.
	 */
	public boolean targetAt(int idx) {
		return targets[idx];
	}

	/**
	 * Returns a deep copy of the feature matrix.
	 */
	public double[][] features() {
		double[][] copy = new double[features.length][];
		for (int i = 0; i < features.length; i++) {
			copy[i] = Arrays.copyOf(features[i], features[i].length);
		}
		return copy;
	}

	/**
	 * Returns the internal feature matrix without copying.
	 */
	public double[][] rawFeatures() {
		return features;
	}

	/**
	 * Returns the internal target labels without copying.
	 */
	public boolean[] rawTargets() {
		return targets;
	}

	/**
	 * Extracts one feature column by name for all rows.
	 */
	public double[] featureColumn(String name) {
		Integer localIndex = featureIndex.get(name);
		if (localIndex == null) {
			throw new IllegalArgumentException("Feature not found: " + name);
		}
		double[] out = new double[rows.length];
		for (int i = 0; i < rows.length; i++) {
			out[i] = features[i][localIndex];
		}
		return out;
	}

	/**
	 * Returns the peptide string for a row.
	 */
	public String peptideAt(int idx) {
		return rows[idx][colIndex(columnGroups.peptideColumn())];
	}

	/**
	 * Returns spectrum-group key column values for a row.
	 */
	public String[] spectrumValuesAt(int idx) {
		String[] out = new String[spectrumColIndices.length];
		for (int i = 0; i < spectrumColIndices.length; i++) {
			out[i] = rows[idx][spectrumColIndices[i]];
		}
		return out;
	}

	/**
	 * Returns whether the dataset exposes an optional psm_group column.
	 */
	public boolean hasPsmGroupColumn() {
		return psmGroups != null;
	}

	/**
	 * Returns whether at least two distinct psm_group values are present.
	 */
	public boolean hasNonTrivialPsmGroup() {
		return hasNonTrivialPsmGroup;
	}

	/**
	 * Returns the rounded psm_group value for a row, or 1 if the optional column is absent.
	 */
	public int psmGroupAt(int idx) {
		if (psmGroups == null) {
			return 1;
		}
		return psmGroups[idx];
	}

	/**
	 * Returns a raw cell value by row and absolute column index.
	 */
	public String rawValueAt(int row, int columnIdx) {
		return rows[row][columnIdx];
	}

	/**
	 * Returns absolute column indices of spectrum grouping columns.
	 */
	public int[] spectrumColIndices() {
		return Arrays.copyOf(spectrumColIndices, spectrumColIndices.length);
	}

	/**
	 * Returns a cell value by row and column name.
	 */
	public String valueAt(int row, String column) {
		return rows[row][colIndex(column)];
	}

	/**
	 * Resolves a column name to its absolute index.
	 */
	public int colIndex(String column) {
		Integer idx = columnIndex.get(column);
		if (idx == null) {
			throw new IllegalArgumentException("Column not found: " + column);
		}
		return idx;
	}

	/**
	 * Returns a deep copy of the raw PIN rows.
	 */
	public String[][] rows() {
		String[][] copy = new String[rows.length][];
		for (int i = 0; i < rows.length; i++) {
			copy[i] = Arrays.copyOf(rows[i], rows[i].length);
		}
		return copy;
	}

	/**
	 * Returns the original file headers in order.
	 */
	public List<String> headers() {
		return headers;
	}

	/**
	 * Rebuilds this dataset with alternative column-role inference.
	 */
	public PsmDataset withColumnGroups(ColumnGroups newGroups) {
		return new PsmDataset(newGroups, headers, rows);
	}

	private int[] parseSpectrumColIndices() {
		List<String> cols = columnGroups.spectrumColumns();
		int[] out = new int[cols.size()];
		for (int i = 0; i < cols.size(); i++) {
			out[i] = colIndex(cols.get(i));
		}
		return out;
	}
}
