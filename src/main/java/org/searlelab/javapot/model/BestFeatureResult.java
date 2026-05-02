package org.searlelab.javapot.model;

import java.util.Objects;

/**
 * BestFeatureResult captures the winning feature-direction choice and its derived labels.
 * It is used to initialize fold training and for best-feature fallback decisions.
 */
public final class BestFeatureResult {
	private final String name;
	private final int positives;
	private final int[] labels;
	private final boolean descending;

	public BestFeatureResult(String name, int positives, int[] labels, boolean descending) {
		this.name = name;
		this.positives = positives;
		this.labels = labels;
		this.descending = descending;
	}

	public String name() {
		return name;
	}

	public int positives() {
		return positives;
	}

	public int[] labels() {
		return labels;
	}

	public boolean descending() {
		return descending;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof BestFeatureResult)) {
			return false;
		}
		BestFeatureResult other = (BestFeatureResult) obj;
		return positives == other.positives
			&& descending == other.descending
			&& Objects.equals(name, other.name)
			&& Objects.equals(labels, other.labels);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, positives, labels, descending);
	}

	@Override
	public String toString() {
		return "BestFeatureResult[" +
			"name=" + name + ", " +
			"positives=" + positives + ", " +
			"labels=" + labels + ", " +
			"descending=" + descending +
			"]";
	}
}
