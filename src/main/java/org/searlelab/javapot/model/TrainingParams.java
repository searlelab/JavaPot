package org.searlelab.javapot.model;

import org.searlelab.javapot.stats.ConfidenceMode;

/**
 * TrainingParams holds the per-fold settings for Percolator model training.
 * It captures FDR, iteration count, optional direction override, and fold seed.
 */
public record TrainingParams(
	double trainFdr,
	int maxIter,
	String direction,
	long seed,
	ConfidenceMode confidenceMode
) {
}
