package org.searlelab.javapot.model;

import java.util.Objects;

/**
 * FoldTrainingOutput bundles the trained fold model with its best-feature baseline metadata.
 * JavaPot uses this object to preserve fallback context across downstream scoring steps.
 */
public final class FoldTrainingOutput {
	private final PercolatorFoldModel model;
	private final String bestFeature;
	private final int bestFeaturePass;
	private final boolean bestFeatureDesc;

	public FoldTrainingOutput(PercolatorFoldModel model, String bestFeature, int bestFeaturePass, boolean bestFeatureDesc) {
		this.model = model;
		this.bestFeature = bestFeature;
		this.bestFeaturePass = bestFeaturePass;
		this.bestFeatureDesc = bestFeatureDesc;
	}

	public PercolatorFoldModel model() {
		return model;
	}

	public String bestFeature() {
		return bestFeature;
	}

	public int bestFeaturePass() {
		return bestFeaturePass;
	}

	public boolean bestFeatureDesc() {
		return bestFeatureDesc;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof FoldTrainingOutput)) {
			return false;
		}
		FoldTrainingOutput other = (FoldTrainingOutput) obj;
		return bestFeaturePass == other.bestFeaturePass
			&& bestFeatureDesc == other.bestFeatureDesc
			&& Objects.equals(model, other.model)
			&& Objects.equals(bestFeature, other.bestFeature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(model, bestFeature, bestFeaturePass, bestFeatureDesc);
	}

	@Override
	public String toString() {
		return "FoldTrainingOutput[" +
			"model=" + model + ", " +
			"bestFeature=" + bestFeature + ", " +
			"bestFeaturePass=" + bestFeaturePass + ", " +
			"bestFeatureDesc=" + bestFeatureDesc +
			"]";
	}
}
