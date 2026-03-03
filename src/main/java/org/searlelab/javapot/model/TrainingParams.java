package org.searlelab.javapot.model;

/**
 * TrainingParams holds the per-fold settings for Percolator model training.
 * It captures FDR, iteration count, optional direction override, and fold seed.
 */
public record TrainingParams(
	double trainFdr,
	int maxIter,
	String direction,
	long seed
) {
}
