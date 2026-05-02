package org.searlelab.javapot.pipeline;

import java.util.Objects;

/**
 * JavaPotPeptide stores confidence statistics for one retained peptide/PSM entry.
 */
public final class JavaPotPeptide {
	private final double score;
	private final double qValue;
	private final double pep;
	private final boolean isDecoy;
	private final String psmId;
	private final String peptideSequence;

	public JavaPotPeptide(
		double score,
		double qValue,
		double pep,
		boolean isDecoy,
		String psmId,
		String peptideSequence
	) {
		this.score = score;
		this.qValue = qValue;
		this.pep = pep;
		this.isDecoy = isDecoy;
		this.psmId = psmId;
		this.peptideSequence = peptideSequence;
	}

	public double score() {
		return score;
	}

	public double qValue() {
		return qValue;
	}

	public double pep() {
		return pep;
	}

	public boolean isDecoy() {
		return isDecoy;
	}

	public String psmId() {
		return psmId;
	}

	public String peptideSequence() {
		return peptideSequence;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof JavaPotPeptide)) {
			return false;
		}
		JavaPotPeptide other = (JavaPotPeptide) obj;
		return Double.compare(score, other.score) == 0
			&& Double.compare(qValue, other.qValue) == 0
			&& Double.compare(pep, other.pep) == 0
			&& isDecoy == other.isDecoy
			&& Objects.equals(psmId, other.psmId)
			&& Objects.equals(peptideSequence, other.peptideSequence);
	}

	@Override
	public int hashCode() {
		return Objects.hash(score, qValue, pep, isDecoy, psmId, peptideSequence);
	}

	@Override
	public String toString() {
		return "JavaPotPeptide[" +
			"score=" + score + ", " +
			"qValue=" + qValue + ", " +
			"pep=" + pep + ", " +
			"isDecoy=" + isDecoy + ", " +
			"psmId=" + psmId + ", " +
			"peptideSequence=" + peptideSequence +
			"]";
	}
}
