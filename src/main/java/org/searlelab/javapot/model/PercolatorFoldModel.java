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

	public double[] predict(double[][] rawFeatures) {
		StandardScaler scaler = StandardScaler.from(means, scales);
		double[][] norm = scaler.transform(rawFeatures);
		return svm.decisionFunction(norm);
	}

	public String[] featureNames() {
		return Arrays.copyOf(featureNames, featureNames.length);
	}

	public LinearSvmModel svm() {
		return svm;
	}

	public String bestFeature() {
		return bestFeature;
	}

	public int bestFeaturePass() {
		return bestFeaturePass;
	}

	public boolean bestFeatureDescending() {
		return bestFeatureDescending;
	}

	public int fold() {
		return fold;
	}
}
