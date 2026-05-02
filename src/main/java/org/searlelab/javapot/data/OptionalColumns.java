package org.searlelab.javapot.data;

import java.util.Objects;

/**
 * OptionalColumns holds non-feature PIN metadata column names when present.
 * These names are propagated for grouping, output projection, and confidence reporting.
 */
public final class OptionalColumns {
	private final String id;
	private final String filename;
	private final String scan;
	private final String psmGroup;
	private final String calcmass;
	private final String expmass;
	private final String rt;
	private final String charge;
	private final String protein;

	public OptionalColumns(
		String id,
		String filename,
		String scan,
		String psmGroup,
		String calcmass,
		String expmass,
		String rt,
		String charge,
		String protein
	) {
		this.id = id;
		this.filename = filename;
		this.scan = scan;
		this.psmGroup = psmGroup;
		this.calcmass = calcmass;
		this.expmass = expmass;
		this.rt = rt;
		this.charge = charge;
		this.protein = protein;
	}

	public String id() {
		return id;
	}

	public String filename() {
		return filename;
	}

	public String scan() {
		return scan;
	}

	public String psmGroup() {
		return psmGroup;
	}

	public String calcmass() {
		return calcmass;
	}

	public String expmass() {
		return expmass;
	}

	public String rt() {
		return rt;
	}

	public String charge() {
		return charge;
	}

	public String protein() {
		return protein;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof OptionalColumns)) {
			return false;
		}
		OptionalColumns other = (OptionalColumns) obj;
		return Objects.equals(id, other.id)
			&& Objects.equals(filename, other.filename)
			&& Objects.equals(scan, other.scan)
			&& Objects.equals(psmGroup, other.psmGroup)
			&& Objects.equals(calcmass, other.calcmass)
			&& Objects.equals(expmass, other.expmass)
			&& Objects.equals(rt, other.rt)
			&& Objects.equals(charge, other.charge)
			&& Objects.equals(protein, other.protein);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, filename, scan, psmGroup, calcmass, expmass, rt, charge, protein);
	}

	@Override
	public String toString() {
		return "OptionalColumns[" +
			"id=" + id + ", " +
			"filename=" + filename + ", " +
			"scan=" + scan + ", " +
			"psmGroup=" + psmGroup + ", " +
			"calcmass=" + calcmass + ", " +
			"expmass=" + expmass + ", " +
			"rt=" + rt + ", " +
			"charge=" + charge + ", " +
			"protein=" + protein +
			"]";
	}
}
