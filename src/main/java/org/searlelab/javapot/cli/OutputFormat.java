package org.searlelab.javapot.cli;

import java.util.Locale;

/**
 * OutputFormat controls the TSV schema used for JavaPot confidence reports.
 * The default is percolator-style headers, with optional mokapot-style column names.
 */
public enum OutputFormat {
	PERCOLATOR,
	MOKAPOT;

	/**
	 * Parses user-provided output format names from CLI input.
	 */
	public static OutputFormat parse(String value) {
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		switch (normalized) {
			case "PERCOLATOR":
				return PERCOLATOR;
			case "MOKAPOT":
				return MOKAPOT;
			default:
				throw new IllegalArgumentException(
					"Invalid value for --output_format: " + value + ". Expected one of: percolator, mokapot"
				);
		}
	}
}
