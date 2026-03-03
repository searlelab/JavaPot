package org.searlelab.javapot.cli;

import java.nio.file.Path;
import java.util.List;

/**
 * CliConfig is the immutable runtime configuration for a single JavaPot execution.
 * It stores parsed option values together with project defaults for omitted flags.
 */
public record CliConfig(
	Path pinFile,
	Path destDir,
	int maxWorkers,
	double trainFdr,
	double testFdr,
	int maxIter,
	long seed,
	String direction,
	Integer subsetMaxTrain,
	boolean saveModels,
	List<Path> loadModels,
	int folds
) {
	public static final double DEFAULT_FDR = 0.01;
	public static final int DEFAULT_MAX_ITER = 10;
	public static final long DEFAULT_SEED = 1L;
	public static final int DEFAULT_FOLDS = 3;
}
