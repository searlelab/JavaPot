package org.searlelab.javapot.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
			boolean traditional = isTraditionalPin(pinFile);
			ParsedFile parsed = parse(pinFile, traditional);
			ColumnGroups initial = ColumnGroups.inferFromColnames(parsed.headers());
			List<String> filteredFeatures = dropMissingFeatureColumns(parsed, initial.featureColumns());
			ColumnGroups finalGroups = initial.withFeatureColumns(filteredFeatures);
			String[][] rows = parsed.rows().toArray(String[][]::new);
			return new PsmDataset(finalGroups, parsed.headers(), rows);
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse PIN file: " + pinFile, e);
		}
	}

	private static boolean isTraditionalPin(Path pinFile) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(pinFile)) {
			String headerLine = nextDataLine(reader);
			if (headerLine == null) {
				throw new IllegalArgumentException("PIN file is empty: " + pinFile);
			}
			String[] header = splitTab(headerLine);
			if (header.length == 1) {
				throw new IllegalArgumentException("File is not a tab-delimited PIN file: " + pinFile);
			}
			String last = header[header.length - 1].toLowerCase(Locale.ROOT);
			if (!last.startsWith("protein")) {
				return false;
			}
			int fields = header.length;
			String line;
			while ((line = reader.readLine()) != null) {
				if (shouldSkip(line)) {
					continue;
				}
				String[] local = splitTab(line);
				if (local.length < fields) {
					throw new IllegalArgumentException(
						"Invalid PIN row with fewer columns than header. expected=" + fields + " found=" + local.length
					);
				}
				if (local.length > fields) {
					return true;
				}
			}
			return false;
		}
	}

	private static ParsedFile parse(Path pinFile, boolean traditional) throws IOException {
		List<String> headers = null;
		List<String[]> rows = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(pinFile)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (shouldSkip(line)) {
					continue;
				}
				if (headers == null) {
					headers = List.of(splitTab(line));
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
				}
				rows.add(parts);
			}
		}
		if (headers == null) {
			throw new IllegalArgumentException("PIN file did not contain a header row: " + pinFile);
		}
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("PIN file did not contain any data rows: " + pinFile);
		}
		return new ParsedFile(headers, rows);
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

	private static List<String> dropMissingFeatureColumns(ParsedFile parsed, List<String> features) {
		Set<String> drop = new HashSet<>();
		for (String feature : features) {
			int idx = parsed.headers().indexOf(feature);
			for (String[] row : parsed.rows()) {
				String value = row[idx];
				if (value == null || value.isBlank() || isNaToken(value)) {
					drop.add(feature);
					break;
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (String feature : features) {
			if (!drop.contains(feature)) {
				out.add(feature);
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

	private static String nextDataLine(BufferedReader reader) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			if (!shouldSkip(line)) {
				return line;
			}
		}
		return null;
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
		return line.split("\\t", -1);
	}

	/**
	 * ParsedFile stores raw header and row values before column-role inference.
	 */
	private record ParsedFile(List<String> headers, List<String[]> rows) {
	}
}
