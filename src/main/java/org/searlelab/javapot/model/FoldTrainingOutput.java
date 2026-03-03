package org.searlelab.javapot.model;

/**
 * FoldTrainingOutput bundles the trained fold model with its best-feature baseline metadata.
 * JavaPot uses this object to preserve fallback context across downstream scoring steps.
 */
public record FoldTrainingOutput(PercolatorFoldModel model, String bestFeature, int bestFeaturePass, boolean bestFeatureDesc) {
}
