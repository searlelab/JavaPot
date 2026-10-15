package org.searlelab.javapot.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.searlelab.javapot.data.ColumnGroups;
import org.searlelab.javapot.data.PsmDataset;

/**
 * PinFileParser reads a single tab-delimited PIN-style file into a {@link PsmDataset}.
 * It handles traditional ragged protein columns, infers column roles, and drops unusable feature columns.
 */
public final class PinFileParser {
	private PinFileParser() {
	}

	/**
	 * Parses one PIN-like tab-delimited file into an in-memory dataset.
	 */
	public static PsmDataset read(Path pinFile) {
		try {
			ParsedFile parsed = parse(pinFile);
			return PsmDataset.fromTabRows(parsed.columnGroups(), parsed.headers(), parsed.rows());
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse PIN file: " + pinFile, e);
		}
	}

	private static ParsedFile parse(Path pinFile) throws IOException {
		List<String> headers = null;
		boolean traditional = false;
		List<String> rows = new ArrayList<>();
		ColumnGroups initial = null;
		int[] featureColumns = null;
		boolean[] missing = null;
		try (BufferedReader reader = Files.newBufferedReader(pinFile)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (shouldSkip(line)) {
					continue;
				}
				if (headers == null) {
					headers = List.of(splitTab(line));
					if (headers.size() == 1) {
						throw new IllegalArgumentException("File is not a tab-delimited PIN file: " + pinFile);
					}
					traditional = headers.get(headers.size() - 1).toLowerCase(Locale.ROOT).startsWith("protein");
					initial = ColumnGroups.inferFromColnames(headers);
					featureColumns = new int[initial.featureColumns().size()];
					missing = new boolean[featureColumns.length];
					for (int j = 0; j < featureColumns.length; j++) {
						featureColumns[j] = headers.indexOf(initial.featureColumns().get(j));
					}
					continue;
				}
				String[] parts = splitTab(line);
				if (parts.length < headers.size()) {
					throw new IllegalArgumentException(
						"Invalid PIN row with fewer columns than header. expected=" + headers.size() + " found=" + parts.length
					);
				}
				if (parts.length > headers.size()) {
					if (!traditional) {
						throw new IllegalArgumentException("Unexpected ragged row in non-traditional PIN file");
					}
					parts = normalizeTraditionalRow(parts, headers.size());
					line = String.join("\t", parts);
				}
				for (int j = 0; j < featureColumns.length; j++) {
					if (!missing[j]) {
						String value = parts[featureColumns[j]];
						missing[j] = value == null || value.isBlank() || isNaToken(value);
					}
				}
				rows.add(line);
			}
		}
		if (headers == null) {
			throw new IllegalArgumentException("PIN file is empty: " + pinFile);
		}
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("PIN file did not contain any data rows: " + pinFile);
		}
		return new ParsedFile(initial.withFeatureColumns(dropMissingFeatureColumns(initial.featureColumns(), missing)), headers, rows);
	}

	private static String[] normalizeTraditionalRow(String[] parts, int headerSize) {
		String[] out = new String[headerSize];
		System.arraycopy(parts, 0, out, 0, headerSize - 1);
		StringBuilder proteins = new StringBuilder();
		for (int i = headerSize - 1; i < parts.length; i++) {
			if (i > headerSize - 1) {
				proteins.append(':');
			}
			proteins.append(parts[i]);
		}
		out[headerSize - 1] = proteins.toString();
		return out;
	}

	private static List<String> dropMissingFeatureColumns(List<String> features, boolean[] missing) {
		List<String> out = new ArrayList<>();
		for (int j = 0; j < features.size(); j++) {
			if (!missing[j]) {
				out.add(features.get(j));
			}
		}
		if (out.isEmpty()) {
			throw new IllegalArgumentException("No usable feature columns after dropping missing-value features.");
		}
		return out;
	}

	private static boolean isNaToken(String value) {
		String lower = value.trim().toLowerCase(Locale.ROOT);
		return lower.equals("na") || lower.equals("nan") || lower.equals("null");
	}

	private static boolean shouldSkip(String line) {
		String trimmed = line.trim();
		if (trimmed.isEmpty()) {
			return true;
		}
		if (trimmed.startsWith("#")) {
			return true;
		}
		return trimmed.startsWith("DefaultDirection");
	}

	private static String[] splitTab(String line) {
		return line.split("\t", -1);
	}

	/**
	 * ParsedFile stores normalized row text and inferred roles without retaining per-cell tokens.
	 */
	private static final class ParsedFile {
		private final List<String> headers;
		private final List<String> rows;
		private final ColumnGroups columnGroups;

		private ParsedFile(ColumnGroups columnGroups, List<String> headers, List<String> rows) {
			this.columnGroups = columnGroups;
			this.headers = headers;
			this.rows = rows;
		}

		private ColumnGroups columnGroups() {
			return columnGroups;
		}

		private List<String> headers() {
			return headers;
		}

		private List<String> rows() {
			return rows;
		}
	}
}
