package org.searlelab.javapot.model;

import java.util.Objects;

import org.searlelab.javapot.stats.ConfidenceMode;

/**
 * TrainingParams holds the per-fold settings for Percolator model training.
 * It captures FDR, iteration count, optional direction override, and fold seed.
 */
public final class TrainingParams {
	private final double trainFdr;
	private final int maxIter;
	private final String direction;
	private final long seed;
	private final ConfidenceMode confidenceMode;
	private final boolean quiet;

	public TrainingParams(
		double trainFdr,
		int maxIter,
		String direction,
		long seed,
		ConfidenceMode confidenceMode,
		boolean quiet
	) {
		this.trainFdr = trainFdr;
		this.maxIter = maxIter;
		this.direction = direction;
		this.seed = seed;
		this.confidenceMode = confidenceMode;
		this.quiet = quiet;
	}

	public double trainFdr() {
		return trainFdr;
	}

	public int maxIter() {
		return maxIter;
	}

	public String direction() {
		return direction;
	}

	public long seed() {
		return seed;
	}

	public ConfidenceMode confidenceMode() {
		return confidenceMode;
	}

	public boolean quiet() {
		return quiet;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof TrainingParams)) {
			return false;
		}
		TrainingParams other = (TrainingParams) obj;
		return Double.compare(trainFdr, other.trainFdr) == 0
			&& maxIter == other.maxIter
			&& seed == other.seed
			&& quiet == other.quiet
			&& Objects.equals(direction, other.direction)
			&& confidenceMode == other.confidenceMode;
	}

	@Override
	public int hashCode() {
		return Objects.hash(trainFdr, maxIter, direction, seed, confidenceMode, quiet);
	}

	@Override
	public String toString() {
		return "TrainingParams[" +
			"trainFdr=" + trainFdr + ", " +
			"maxIter=" + maxIter + ", " +
			"direction=" + direction + ", " +
			"seed=" + seed + ", " +
			"confidenceMode=" + confidenceMode + ", " +
			"quiet=" + quiet +
			"]";
	}
}
