package org.searlelab.javapot.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private final Map<String, Integer> featureIndex;
	private final Map<String, Integer> columnIndex;

	public PsmDataset(ColumnGroups columnGroups, List<String> headers, String[][] rows) {
		this.columnGroups = columnGroups;
		this.headers = List.copyOf(headers);
		this.rows = rows;
		this.columnIndex = buildIndex(headers);
		this.targets = parseTargets();
		this.featureNames = columnGroups.featureColumns().toArray(String[]::new);
		this.spectrumColIndices = parseSpectrumColIndices();
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

	public int size() {
		return rows.length;
	}

	public int featureCount() {
		return featureNames.length;
	}

	public String[] featureNames() {
		return Arrays.copyOf(featureNames, featureNames.length);
	}

	public ColumnGroups columnGroups() {
		return columnGroups;
	}

	public boolean[] targets() {
		return Arrays.copyOf(targets, targets.length);
	}

	public boolean targetAt(int idx) {
		return targets[idx];
	}

	public double[][] features() {
		double[][] copy = new double[features.length][];
		for (int i = 0; i < features.length; i++) {
			copy[i] = Arrays.copyOf(features[i], features[i].length);
		}
		return copy;
	}

	public double[][] rawFeatures() {
		return features;
	}

	public boolean[] rawTargets() {
		return targets;
	}

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

	public String peptideAt(int idx) {
		return rows[idx][colIndex(columnGroups.peptideColumn())];
	}

	public String[] spectrumValuesAt(int idx) {
		String[] out = new String[spectrumColIndices.length];
		for (int i = 0; i < spectrumColIndices.length; i++) {
			out[i] = rows[idx][spectrumColIndices[i]];
		}
		return out;
	}

	public String rawValueAt(int row, int columnIdx) {
		return rows[row][columnIdx];
	}

	public int[] spectrumColIndices() {
		return Arrays.copyOf(spectrumColIndices, spectrumColIndices.length);
	}

	public String valueAt(int row, String column) {
		return rows[row][colIndex(column)];
	}

	public int colIndex(String column) {
		Integer idx = columnIndex.get(column);
		if (idx == null) {
			throw new IllegalArgumentException("Column not found: " + column);
		}
		return idx;
	}

	public String[][] rows() {
		String[][] copy = new String[rows.length][];
		for (int i = 0; i < rows.length; i++) {
			copy[i] = Arrays.copyOf(rows[i], rows[i].length);
		}
		return copy;
	}

	public List<String> headers() {
		return headers;
	}

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
