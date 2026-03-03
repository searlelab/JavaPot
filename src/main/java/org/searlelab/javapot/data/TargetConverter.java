package org.searlelab.javapot.data;

/**
 * TargetConverter normalizes PIN target labels into boolean target/decoy values.
 * It accepts canonical Percolator encodings and rejects ambiguous values early.
 */
public final class TargetConverter {
	private TargetConverter() {
	}

	public static boolean toBoolean(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Target value is null");
		}
		String trimmed = value.trim();
		if (trimmed.equalsIgnoreCase("true")) {
			return true;
		}
		if (trimmed.equalsIgnoreCase("false")) {
			return false;
		}
		int parsed;
		try {
			parsed = Integer.parseInt(trimmed);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Target value is not convertible to bool/int: " + value, e);
		}

		if (parsed == 1) {
			return true;
		}
		if (parsed == 0 || parsed == -1) {
			return false;
		}
		throw new IllegalArgumentException("Target value must be one of true/false/0/1/-1 but found: " + value);
	}
}
