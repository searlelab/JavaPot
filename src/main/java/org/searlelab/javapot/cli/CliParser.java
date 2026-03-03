package org.searlelab.javapot.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CliParser parses the supported JavaPot command-line options into a validated {@link CliConfig}.
 * It also enforces argument constraints such as exactly one PIN input and allowed numeric ranges.
 */
public final class CliParser {
	private CliParser() {
	}

	/**
	 * Parses command-line arguments into a validated runtime configuration.
	 */
	public static CliConfig parse(String[] args) {
		Path destDir = Path.of(".");
		Integer maxWorkers = null;
		OutputFormat outputFormat = OutputFormat.PERCOLATOR;
		double trainFdr = CliConfig.DEFAULT_FDR;
		double testFdr = CliConfig.DEFAULT_FDR;
		int maxIter = CliConfig.DEFAULT_MAX_ITER;
		long seed = CliConfig.DEFAULT_SEED;
		String direction = null;
		Integer subsetMaxTrain = null;
		boolean saveModels = false;
		List<Path> loadModels = new ArrayList<>();
		int folds = CliConfig.DEFAULT_FOLDS;
		List<String> positional = new ArrayList<>();

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "-h", "--help" -> {
					printHelp();
					System.exit(0);
				}
				case "-d", "--dest_dir" -> {
					destDir = Path.of(requireValue(args, ++i, arg));
				}
				case "-w", "--max_workers" -> {
					maxWorkers = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "--output_format" -> {
					outputFormat = OutputFormat.parse(requireValue(args, ++i, arg));
				}
				case "--train_fdr" -> {
					trainFdr = parseDouble(requireValue(args, ++i, arg), arg);
				}
				case "--test_fdr" -> {
					testFdr = parseDouble(requireValue(args, ++i, arg), arg);
				}
				case "--max_iter" -> {
					maxIter = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "--seed" -> {
					seed = parseLong(requireValue(args, ++i, arg), arg);
				}
				case "--direction" -> {
					direction = requireValue(args, ++i, arg);
				}
				case "--subset_max_train" -> {
					subsetMaxTrain = parseInt(requireValue(args, ++i, arg), arg);
				}
				case "--save_models" -> {
					saveModels = true;
				}
				case "--load_models" -> {
					if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
						throw new IllegalArgumentException("--load_models requires at least one model path");
					}
					while (i + 1 < args.length && !args[i + 1].startsWith("-")) {
						loadModels.add(Path.of(args[++i]));
					}
				}
				case "--folds" -> {
					folds = parseInt(requireValue(args, ++i, arg), arg);
				}
				default -> {
					if (arg.startsWith("-")) {
						throw new IllegalArgumentException("Unknown option: " + arg);
					}
					positional.add(arg);
				}
			}
		}

		if (positional.size() != 1) {
			throw new IllegalArgumentException("Exactly one PIN input file is required.");
		}

		Path pinFile = Path.of(positional.get(0));
		String lower = pinFile.toString().toLowerCase();
		if (lower.endsWith(".xml") || lower.endsWith(".pepxml")) {
			throw new IllegalArgumentException("XML/PepXML input is not supported. Provide a single PIN file.");
		}

		if (maxWorkers != null && maxWorkers < 1) {
			throw new IllegalArgumentException("--max_workers must be >= 1");
		}
		if (folds < 2) {
			throw new IllegalArgumentException("--folds must be >= 2");
		}
		if (maxIter < 1) {
			throw new IllegalArgumentException("--max_iter must be >= 1");
		}
		if (subsetMaxTrain != null && subsetMaxTrain < 1) {
			throw new IllegalArgumentException("--subset_max_train must be >= 1");
		}
		if (trainFdr <= 0 || trainFdr >= 1) {
			throw new IllegalArgumentException("--train_fdr must be in (0,1)");
		}
		if (testFdr <= 0 || testFdr >= 1) {
			throw new IllegalArgumentException("--test_fdr must be in (0,1)");
		}
		int resolvedMaxWorkers = maxWorkers != null ? maxWorkers : folds;

		return new CliConfig(
			pinFile,
			destDir,
			resolvedMaxWorkers,
			outputFormat,
			trainFdr,
			testFdr,
			maxIter,
			seed,
			direction,
			subsetMaxTrain,
			saveModels,
			List.copyOf(loadModels),
			folds
		);
	}

	/**
	 * Prints supported CLI options and exits guidance text.
	 */
	public static void printHelp() {
		String help = """
			Usage: javapot [options] <pin_file>
			Options:
			  -d DEST_DIR, --dest_dir DEST_DIR
			                        The directory in which to write the result files. Defaults to the current working directory
			  -w MAX_WORKERS, --max_workers MAX_WORKERS
			                        The number of processes to use for model training. Defaults to --folds when omitted. Note that using more than one worker will result in garbled logging messages.
			  --output_format OUTPUT_FORMAT
			                        Output TSV schema to write: percolator (default) or mokapot.
			  --train_fdr TRAIN_FDR
			                        The maximum false discovery rate at which to consider a target PSM as a positive example during model training.
			  --test_fdr TEST_FDR   The false-discovery rate threshold at which to evaluate the learned models.
			  --max_iter MAX_ITER   The number of iterations to use for training.
			  --seed SEED           An integer to use as the random seed.
			  --direction DIRECTION
			                        The name of the feature to use as the initial direction for ranking PSMs.
			  --subset_max_train SUBSET_MAX_TRAIN
			                        Maximum number of PSMs to use during the training of each of the cross validation folds in the model.
			  --save_models         Save the models learned by javapot as Java serialized model objects.
			  --load_models LOAD_MODELS [LOAD_MODELS ...]
			                        Load previously saved models and skip model training. Number of models must match --folds.
			  --folds FOLDS         Number of cross-validation folds. Default: 3.
		""";
		System.out.println(help);
	}

	private static String requireValue(String[] args, int idx, String opt) {
		if (idx >= args.length) {
			throw new IllegalArgumentException("Missing value for option " + opt);
		}
		return args[idx];
	}

	private static int parseInt(String value, String opt) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid integer for " + opt + ": " + value, e);
		}
	}

	private static long parseLong(String value, String opt) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid integer for " + opt + ": " + value, e);
		}
	}

	private static double parseDouble(String value, String opt) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid float for " + opt + ": " + value, e);
		}
	}
}
