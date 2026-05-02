package org.searlelab.javapot.model;

import java.util.Objects;

/**
 * ClassWeightPair stores the negative and positive class weights applied during SVM training.
 * Instances are produced by grid search and embedded in fitted models.
 */
public final class ClassWeightPair {
	private final double negative;
	private final double positive;

	public ClassWeightPair(double negative, double positive) {
		this.negative = negative;
		this.positive = positive;
	}

	public double negative() {
		return negative;
	}

	public double positive() {
		return positive;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ClassWeightPair)) {
			return false;
		}
		ClassWeightPair other = (ClassWeightPair) obj;
		return Double.compare(negative, other.negative) == 0
			&& Double.compare(positive, other.positive) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(negative, positive);
	}

	@Override
	public String toString() {
		return "ClassWeightPair[negative=" + negative + ", positive=" + positive + "]";
	}
}
