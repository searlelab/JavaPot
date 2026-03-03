package org.searlelab.javapot.model;

/**
 * BestFeatureResult captures the winning feature-direction choice and its derived labels.
 * It is used to initialize fold training and for best-feature fallback decisions.
 */
public record BestFeatureResult(String name, int positives, int[] labels, boolean descending) {
}
