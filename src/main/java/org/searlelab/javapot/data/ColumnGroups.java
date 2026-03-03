package org.searlelab.javapot.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ColumnGroups captures inferred PIN column roles, including metadata, spectrum keys, and model features.
 * It validates required columns and provides deterministic feature selection based on Mokapot-style rules.
 */
public final class ColumnGroups {
	private final List<String> columns;
	private final String targetColumn;
	private final String peptideColumn;
	private final List<String> spectrumColumns;
	private final List<String> featureColumns;
	private final List<String> extraConfidenceLevelColumns;
	private final OptionalColumns optionalColumns;

	public ColumnGroups(
		List<String> columns,
		String targetColumn,
		String peptideColumn,
		List<String> spectrumColumns,
		List<String> featureColumns,
		List<String> extraConfidenceLevelColumns,
		OptionalColumns optionalColumns
	) {
		this.columns = List.copyOf(columns);
		this.targetColumn = targetColumn;
		this.peptideColumn = peptideColumn;
		this.spectrumColumns = List.copyOf(spectrumColumns);
		this.featureColumns = List.copyOf(featureColumns);
		this.extraConfidenceLevelColumns = List.copyOf(extraConfidenceLevelColumns);
		this.optionalColumns = optionalColumns;
		validate();
	}

	private void validate() {
		Set<String> all = new HashSet<>(columns);
		if (!all.contains(targetColumn)) {
			throw new IllegalArgumentException("Target column '" + targetColumn + "' not found");
		}
		if (!all.contains(peptideColumn)) {
			throw new IllegalArgumentException("Peptide column '" + peptideColumn + "' not found");
		}
		if (featureColumns.contains(targetColumn)) {
			throw new IllegalArgumentException("Target column cannot be a feature column");
		}
		for (String col : spectrumColumns) {
			if (!all.contains(col)) {
				throw new IllegalArgumentException("Spectrum column '" + col + "' not found");
			}
		}
		Set<String> seen = new HashSet<>();
		for (String feat : featureColumns) {
			if (!all.contains(feat)) {
				throw new IllegalArgumentException("Feature column '" + feat + "' not found");
			}
			if (!seen.add(feat)) {
				throw new IllegalArgumentException("Duplicate feature column: " + feat);
			}
		}
		if (optionalColumns != null) {
			validateOptional(optionalColumns.id());
			validateOptional(optionalColumns.filename());
			validateOptional(optionalColumns.scan());
			validateOptional(optionalColumns.calcmass());
			validateOptional(optionalColumns.expmass());
			validateOptional(optionalColumns.rt());
			validateOptional(optionalColumns.charge());
			validateOptional(optionalColumns.protein());
		}
	}

	private void validateOptional(String col) {
		if (col != null && !columns.contains(col)) {
			throw new IllegalArgumentException("Optional column '" + col + "' not found");
		}
	}

	public static ColumnGroups inferFromColnames(List<String> columns) {
		String specid;
		String first = columns.get(0);
		if (first.toLowerCase(Locale.ROOT).contains("id")) {
			specid = first;
		} else {
			specid = findRequiredColumn("specid", columns);
		}
		String peptide = findRequiredColumnOrAlias("peptide", columns, "sequence");
		String proteins = findRequiredColumn("proteins", columns);
		String label = findRequiredColumn("label", columns);
		String scan = findRequiredColumn("scannr", columns);

		List<String> nonfeat = new ArrayList<>();
		nonfeat.add(specid);
		nonfeat.add(scan);
		nonfeat.add(peptide);
		nonfeat.add(proteins);
		nonfeat.add(label);

		List<String> modifiedPeptides = findColumns("modifiedpeptide", columns);
		List<String> precursors = findColumns("precursor", columns);
		List<String> peptideGroups = findColumns("peptidegroup", columns);
		List<String> extra = new ArrayList<>();
		extra.addAll(modifiedPeptides);
		extra.addAll(precursors);
		extra.addAll(peptideGroups);
		nonfeat.addAll(extra);

		String filename = findOptionalColumn(null, columns, "filename");
		String calcmass = findOptionalColumn(null, columns, "calcmass");
		String expmass = findOptionalColumn(null, columns, "expmass");
		String retTime = findOptionalColumn(null, columns, "ret_time");
		String charge = findOptionalColumn(null, columns, "charge_column");

		List<String> spectra = new ArrayList<>();
		if (filename != null) {
			spectra.add(filename);
		}
		if (scan != null) {
			spectra.add(scan);
		}
		if (retTime != null) {
			spectra.add(retTime);
		}
		if (expmass != null) {
			spectra.add(expmass);
		}
		if (spectra.isEmpty()) {
			throw new IllegalArgumentException("No spectrum columns were detected from PIN file");
		}

		List<String> altCharge = new ArrayList<>();
		for (String col : columns) {
			if (col.toLowerCase(Locale.ROOT).startsWith("charge")) {
				altCharge.add(col);
			}
		}
		if (charge != null && altCharge.size() > 1) {
			nonfeat.add(charge);
		}

		if (filename != null) {
			nonfeat.add(filename);
		}
		if (calcmass != null) {
			nonfeat.add(calcmass);
		}
		if (expmass != null) {
			nonfeat.add(expmass);
		}
		if (retTime != null) {
			nonfeat.add(retTime);
		}

		Set<String> nonfeatSet = new HashSet<>(nonfeat);
		List<String> features = new ArrayList<>();
		for (String col : columns) {
			if (!nonfeatSet.contains(col)) {
				features.add(col);
			}
		}

		OptionalColumns optional = new OptionalColumns(
			specid,
			filename,
			scan,
			calcmass,
			expmass,
			retTime,
			charge,
			proteins
		);

		return new ColumnGroups(columns, label, peptide, spectra, features, extra, optional);
	}

	private static List<String> findColumns(String col, List<String> columns) {
		List<String> out = new ArrayList<>();
		for (String c : columns) {
			if (c.equalsIgnoreCase(col)) {
				out.add(c);
			}
		}
		return out;
	}

	private static String findRequiredColumn(String col, List<String> columns) {
		String found = null;
		for (String c : columns) {
			if (c.equalsIgnoreCase(col)) {
				if (found != null) {
					throw new IllegalArgumentException("Column '" + col + "' should be unique");
				}
				found = c;
			}
		}
		if (found == null) {
			throw new IllegalArgumentException("Required column '" + col + "' was not found");
		}
		return found;
	}

	private static String findRequiredColumnOrAlias(String preferred, List<String> columns, String alias) {
		try {
			return findRequiredColumn(preferred, columns);
		} catch (IllegalArgumentException ignored) {
			return findRequiredColumn(alias, columns);
		}
	}

	private static String findOptionalColumn(String col, List<String> columns, String def) {
		if (col != null) {
			for (String c : columns) {
				if (c.equals(col)) {
					return c;
				}
			}
			throw new IllegalArgumentException("Specified optional column not found: " + col);
		}
		for (String c : columns) {
			if (c.equalsIgnoreCase(def)) {
				return c;
			}
		}
		return null;
	}

	public ColumnGroups withFeatureColumns(List<String> newFeatures) {
		return new ColumnGroups(columns, targetColumn, peptideColumn, spectrumColumns, newFeatures, extraConfidenceLevelColumns, optionalColumns);
	}

	public List<String> columns() {
		return columns;
	}

	public String targetColumn() {
		return targetColumn;
	}

	public String peptideColumn() {
		return peptideColumn;
	}

	public List<String> spectrumColumns() {
		return spectrumColumns;
	}

	public List<String> featureColumns() {
		return featureColumns;
	}

	public List<String> extraConfidenceLevelColumns() {
		return extraConfidenceLevelColumns;
	}

	public OptionalColumns optionalColumns() {
		return optionalColumns;
	}

	@Override
	public String toString() {
		return "ColumnGroups{" +
			"targetColumn='" + targetColumn + '\'' +
			", peptideColumn='" + peptideColumn + '\'' +
			", spectrumColumns=" + spectrumColumns +
			", featureColumns=" + featureColumns +
			", extraConfidenceLevelColumns=" + extraConfidenceLevelColumns +
			", optionalColumns=" + optionalColumns +
			'}';
	}

	public static List<String> uniquePreserveOrder(List<String> columns) {
		return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(columns)));
	}
}
