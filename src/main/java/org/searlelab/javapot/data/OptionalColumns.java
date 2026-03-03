package org.searlelab.javapot.data;

/**
 * OptionalColumns holds non-feature PIN metadata column names when present.
 * These names are propagated for grouping, output projection, and confidence reporting.
 */
public record OptionalColumns(
	String id,
	String filename,
	String scan,
	String calcmass,
	String expmass,
	String rt,
	String charge,
	String protein
) {
}
