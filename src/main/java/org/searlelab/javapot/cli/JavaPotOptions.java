package org.searlelab.javapot.cli;

import java.nio.file.Path;

/**
 * JavaPotOptions is the immutable runtime configuration for a single JavaPot execution.
 * It stores parsed option values together with project defaults for omitted flags.
 */
public record JavaPotOptions(
	Path pinFile,
	Path destDir,
	int maxWorkers,
	OutputFormat outputFormat,
	boolean quiet,
	double trainFdr,
	double testFdr,
	int maxIter,
	long seed,
	String direction,
	Integer subsetMaxTrain,
	Path saveModelFile,
	boolean writePsmFiles,
	boolean writeDecoyFiles,
	Path resultsPeptides,
	Path decoyResultsPeptides,
	Path resultsPsms,
	Path decoyResultsPsms,
	Path loadModelFile,
	int folds,
	boolean mixmax
) {
	public static final double DEFAULT_FDR = 0.01;
	public static final int DEFAULT_MAX_ITER = 10;
	public static final long DEFAULT_SEED = 1L;
	public static final int DEFAULT_FOLDS = 3;

	public JavaPotOptions(
		Path pinFile,
		double trainFdr,
		double testFdr,
		Path resultsPeptides,
		Path decoyResultsPeptides,
		Path saveModelFile,
		Path loadModelFile,
		boolean mixmax
	) {
		this(
			pinFile,
			defaultOutputDir(pinFile),
			DEFAULT_FOLDS,
			OutputFormat.PERCOLATOR,
			false,
			trainFdr,
			testFdr,
			DEFAULT_MAX_ITER,
			DEFAULT_SEED,
			null,
			null,
			saveModelFile,
			false,
			false,
			resultsPeptides,
			decoyResultsPeptides,
			null,
			null,
			loadModelFile,
			DEFAULT_FOLDS,
			mixmax
		);
	}

	public JavaPotOptions(
		Path pinFile,
		double trainFdr,
		double testFdr,
		Path resultsPeptides,
		Path decoyResultsPeptides,
		boolean mixmax
	) {
		this(pinFile, trainFdr, testFdr, resultsPeptides, decoyResultsPeptides, null, null, mixmax);
	}

	private static Path defaultOutputDir(Path pinFile) {
		Path absolutePin = pinFile.toAbsolutePath().normalize();
		Path parent = absolutePin.getParent();
		if (parent != null) {
			return parent;
		}
		return Path.of(".").toAbsolutePath().normalize();
	}
}
