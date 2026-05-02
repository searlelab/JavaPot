package org.searlelab.javapot.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

/**
 * JavaPotRunResult exposes in-memory confidence results and metadata from a JavaPot run.
 */
public final class JavaPotRunResult {
	private final ArrayList<JavaPotPeptide> peptides;
	private final ArrayList<JavaPotPeptide> psms;
	private final Double psmPi0;
	private final Double peptidePi0;
	private final ArrayList<Path> writtenFiles;

	public JavaPotRunResult(
		ArrayList<JavaPotPeptide> peptides,
		ArrayList<JavaPotPeptide> psms,
		Double psmPi0,
		Double peptidePi0,
		ArrayList<Path> writtenFiles
	) {
		this.peptides = peptides;
		this.psms = psms;
		this.psmPi0 = psmPi0;
		this.peptidePi0 = peptidePi0;
		this.writtenFiles = writtenFiles;
	}

	public ArrayList<JavaPotPeptide> peptides() {
		return peptides;
	}

	public ArrayList<JavaPotPeptide> psms() {
		return psms;
	}

	public Double psmPi0() {
		return psmPi0;
	}

	public Double peptidePi0() {
		return peptidePi0;
	}

	public ArrayList<Path> writtenFiles() {
		return writtenFiles;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof JavaPotRunResult)) {
			return false;
		}
		JavaPotRunResult other = (JavaPotRunResult) obj;
		return Objects.equals(peptides, other.peptides)
			&& Objects.equals(psms, other.psms)
			&& Objects.equals(psmPi0, other.psmPi0)
			&& Objects.equals(peptidePi0, other.peptidePi0)
			&& Objects.equals(writtenFiles, other.writtenFiles);
	}

	@Override
	public int hashCode() {
		return Objects.hash(peptides, psms, psmPi0, peptidePi0, writtenFiles);
	}

	@Override
	public String toString() {
		return "JavaPotRunResult[" +
			"peptides=" + peptides + ", " +
			"psms=" + psms + ", " +
			"psmPi0=" + psmPi0 + ", " +
			"peptidePi0=" + peptidePi0 + ", " +
			"writtenFiles=" + writtenFiles +
			"]";
	}
}
