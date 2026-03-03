package org.searlelab.javapot.model;

/**
 * BestFeatureIsBetterException marks a training path where the iterative model fails to beat the best single feature.
 * Callers can use this signal to trigger deterministic fallback behavior.
 */
public final class BestFeatureIsBetterException extends RuntimeException {
	public BestFeatureIsBetterException(String message) {
		super(message);
	}
}
