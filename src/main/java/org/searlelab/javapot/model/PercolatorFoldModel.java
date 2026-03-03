package org.searlelab.javapot.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * PercolatorFoldModel stores everything needed to score one held-out fold.
 * It contains scaler parameters, the trained linear SVM, and best-feature metadata for fallback checks.
 */
public final class PercolatorFoldModel implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private final String[] featureNames;
	private final double[] means;
	private final double[] scales;
	private final LinearSvmModel svm;
	private final String bestFeature;
	private final int bestFeaturePass;
	private final boolean bestFeatureDescending;
	private final int fold;

	/**
	 * Creates a fold model with scaler, SVM, and best-feature fallback metadata.
	 */
	public PercolatorFoldModel(
		String[] featureNames,
		double[] means,
		double[] scales,
		LinearSvmModel svm,
		String bestFeature,
		int bestFeaturePass,
		boolean bestFeatureDescending,
		int fold
	) {
		this.featureNames = Arrays.copyOf(featureNames, featureNames.length);
		this.means = Arrays.copyOf(means, means.length);
		this.scales = Arrays.copyOf(scales, scales.length);
		this.svm = svm;
		this.bestFeature = bestFeature;
		this.bestFeaturePass = bestFeaturePass;
		this.bestFeatureDescending = bestFeatureDescending;
		this.fold = fold;
	}

	/**
	 * Scores rows after applying fold-specific standardization.
	 */
	public double[] predict(double[][] rawFeatures) {
		StandardScaler scaler = StandardScaler.from(means, scales);
		double[][] norm = scaler.transform(rawFeatures);
		return svm.decisionFunction(norm);
	}

	/**
	 * Returns feature names expected by this fold model.
	 */
	public String[] featureNames() {
		return Arrays.copyOf(featureNames, featureNames.length);
	}

	/**
	 * Returns the fitted linear SVM model.
	 */
	public LinearSvmModel svm() {
		return svm;
	}

	/**
	 * Returns the best single feature used for fallback comparisons.
	 */
	public String bestFeature() {
		return bestFeature;
	}

	/**
	 * Returns how many targets passed at train FDR for the best feature.
	 */
	public int bestFeaturePass() {
		return bestFeaturePass;
	}

	/**
	 * Returns whether the best feature is scored in descending order.
	 */
	public boolean bestFeatureDescending() {
		return bestFeatureDescending;
	}

	/**
	 * Returns the 1-based fold index this model was trained on.
	 */
	public int fold() {
		return fold;
	}
}
