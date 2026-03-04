package org.searlelab.javapot.pipeline;

/**
 * JavaPotPeptide stores confidence statistics for one retained peptide/PSM entry.
 */
public record JavaPotPeptide(
	double score,
	double qValue,
	double pep,
	boolean isDecoy,
	String psmId,
	String peptideSequence
) {
}
