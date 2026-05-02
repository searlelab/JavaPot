package org.searlelab.javapot.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * JavaPotOptions is the immutable runtime configuration for a single JavaPot execution.
 * It stores parsed option values together with project defaults for omitted flags.
 */
public final class JavaPotOptions {
	public static final double DEFAULT_FDR = 0.01;
	public static final int DEFAULT_MAX_ITER = 10;
	public static final long DEFAULT_SEED = 1L;
	public static final int DEFAULT_FOLDS = 3;
	public static final int DEFAULT_MAX_RETRIES = 1;

	private final Path pinFile;
	private final Path destDir;
	private final int maxWorkers;
	private final OutputFormat outputFormat;
	private final boolean quiet;
	private final double trainFdr;
	private final double testFdr;
	private final int maxIter;
	private final long seed;
	private final String direction;
	private final Integer subsetMaxTrain;
	private final Path saveModelFile;
	private final boolean writePsmFiles;
	private final boolean writeDecoyFiles;
	private final Path resultsPeptides;
	private final Path decoyResultsPeptides;
	private final Path resultsPsms;
	private final Path decoyResultsPsms;
	private final Path loadModelFile;
	private final int folds;
	private final int maxRetries;
	private final boolean mixmax;

	public JavaPotOptions(
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
		int maxRetries,
		boolean mixmax
	) {
		this.pinFile = pinFile;
		this.destDir = destDir;
		this.maxWorkers = maxWorkers;
		this.outputFormat = outputFormat;
		this.quiet = quiet;
		this.trainFdr = trainFdr;
		this.testFdr = testFdr;
		this.maxIter = maxIter;
		this.seed = seed;
		this.direction = direction;
		this.subsetMaxTrain = subsetMaxTrain;
		this.saveModelFile = saveModelFile;
		this.writePsmFiles = writePsmFiles;
		this.writeDecoyFiles = writeDecoyFiles;
		this.resultsPeptides = resultsPeptides;
		this.decoyResultsPeptides = decoyResultsPeptides;
		this.resultsPsms = resultsPsms;
		this.decoyResultsPsms = decoyResultsPsms;
		this.loadModelFile = loadModelFile;
		this.folds = folds;
		this.maxRetries = maxRetries;
		this.mixmax = mixmax;
	}

	public JavaPotOptions(
		Path pinFile,
		double trainFdr,
		double testFdr,
		int subsetMaxTrain,
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
			subsetMaxTrain,
			saveModelFile,
			false,
			false,
			resultsPeptides,
			decoyResultsPeptides,
			null,
			null,
			loadModelFile,
			DEFAULT_FOLDS,
			DEFAULT_MAX_RETRIES,
			mixmax
		);
	}

	public JavaPotOptions(
		Path pinFile,
		double trainFdr,
		double testFdr,
		int subsetMaxTrain,
		Path resultsPeptides,
		Path decoyResultsPeptides,
		boolean mixmax
	) {
		this(pinFile, trainFdr, testFdr, subsetMaxTrain, resultsPeptides, decoyResultsPeptides, null, null, mixmax);
	}

	private static Path defaultOutputDir(Path pinFile) {
		Path absolutePin = pinFile.toAbsolutePath().normalize();
		Path parent = absolutePin.getParent();
		if (parent != null) {
			return parent;
		}
		return Paths.get(".").toAbsolutePath().normalize();
	}

	public Path pinFile() {
		return pinFile;
	}

	public Path destDir() {
		return destDir;
	}

	public int maxWorkers() {
		return maxWorkers;
	}

	public OutputFormat outputFormat() {
		return outputFormat;
	}

	public boolean quiet() {
		return quiet;
	}

	public double trainFdr() {
		return trainFdr;
	}

	public double testFdr() {
		return testFdr;
	}

	public int maxIter() {
		return maxIter;
	}

	public long seed() {
		return seed;
	}

	public String direction() {
		return direction;
	}

	public Integer subsetMaxTrain() {
		return subsetMaxTrain;
	}

	public Path saveModelFile() {
		return saveModelFile;
	}

	public boolean writePsmFiles() {
		return writePsmFiles;
	}

	public boolean writeDecoyFiles() {
		return writeDecoyFiles;
	}

	public Path resultsPeptides() {
		return resultsPeptides;
	}

	public Path decoyResultsPeptides() {
		return decoyResultsPeptides;
	}

	public Path resultsPsms() {
		return resultsPsms;
	}

	public Path decoyResultsPsms() {
		return decoyResultsPsms;
	}

	public Path loadModelFile() {
		return loadModelFile;
	}

	public int folds() {
		return folds;
	}

	public int maxRetries() {
		return maxRetries;
	}

	public boolean mixmax() {
		return mixmax;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof JavaPotOptions)) {
			return false;
		}
		JavaPotOptions other = (JavaPotOptions) obj;
		return maxWorkers == other.maxWorkers
			&& quiet == other.quiet
			&& Double.compare(trainFdr, other.trainFdr) == 0
			&& Double.compare(testFdr, other.testFdr) == 0
			&& maxIter == other.maxIter
			&& seed == other.seed
			&& writePsmFiles == other.writePsmFiles
			&& writeDecoyFiles == other.writeDecoyFiles
			&& folds == other.folds
			&& maxRetries == other.maxRetries
			&& mixmax == other.mixmax
			&& Objects.equals(pinFile, other.pinFile)
			&& Objects.equals(destDir, other.destDir)
			&& outputFormat == other.outputFormat
			&& Objects.equals(direction, other.direction)
			&& Objects.equals(subsetMaxTrain, other.subsetMaxTrain)
			&& Objects.equals(saveModelFile, other.saveModelFile)
			&& Objects.equals(resultsPeptides, other.resultsPeptides)
			&& Objects.equals(decoyResultsPeptides, other.decoyResultsPeptides)
			&& Objects.equals(resultsPsms, other.resultsPsms)
			&& Objects.equals(decoyResultsPsms, other.decoyResultsPsms)
			&& Objects.equals(loadModelFile, other.loadModelFile);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			pinFile,
			destDir,
			maxWorkers,
			outputFormat,
			quiet,
			trainFdr,
			testFdr,
			maxIter,
			seed,
			direction,
			subsetMaxTrain,
			saveModelFile,
			writePsmFiles,
			writeDecoyFiles,
			resultsPeptides,
			decoyResultsPeptides,
			resultsPsms,
			decoyResultsPsms,
			loadModelFile,
			folds,
			maxRetries,
			mixmax
		);
	}

	@Override
	public String toString() {
		return "JavaPotOptions[" +
			"pinFile=" + pinFile + ", " +
			"destDir=" + destDir + ", " +
			"maxWorkers=" + maxWorkers + ", " +
			"outputFormat=" + outputFormat + ", " +
			"quiet=" + quiet + ", " +
			"trainFdr=" + trainFdr + ", " +
			"testFdr=" + testFdr + ", " +
			"maxIter=" + maxIter + ", " +
			"seed=" + seed + ", " +
			"direction=" + direction + ", " +
			"subsetMaxTrain=" + subsetMaxTrain + ", " +
			"saveModelFile=" + saveModelFile + ", " +
			"writePsmFiles=" + writePsmFiles + ", " +
			"writeDecoyFiles=" + writeDecoyFiles + ", " +
			"resultsPeptides=" + resultsPeptides + ", " +
			"decoyResultsPeptides=" + decoyResultsPeptides + ", " +
			"resultsPsms=" + resultsPsms + ", " +
			"decoyResultsPsms=" + decoyResultsPsms + ", " +
			"loadModelFile=" + loadModelFile + ", " +
			"folds=" + folds + ", " +
			"maxRetries=" + maxRetries + ", " +
			"mixmax=" + mixmax +
			"]";
	}
}
