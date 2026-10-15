package org.searlelab.javapot.pipeline;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.searlelab.javapot.cli.CliParser;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.io.PinFileParser;
import org.searlelab.javapot.model.FoldSplitter;
import org.searlelab.javapot.model.PercolatorFoldModel;
import org.searlelab.javapot.model.PercolatorTrainer;
import org.searlelab.javapot.model.TrainingParams;
import org.searlelab.javapot.stats.ConfidenceMode;
import org.searlelab.javapot.util.DeterministicRandom;

/** Generates the checked-in exact oracle using the frozen baseline JAR, without rounding numeric values. */
public final class ExactParitySnapshot {
	private ExactParitySnapshot() {
	}

	public static void main(String[] args) throws Exception {
		System.out.println(capture(Path.of(args[0])));
	}

	static String capture(Path directory) throws Exception {
		Files.createDirectories(directory);
		Path pin = directory.resolve("exact.pin");
		StringBuilder text = new StringBuilder("SpecId\tLabel\tScanNr\tpsm_group\tExpMass\tfeatA\tfeatB\tPeptide\tProteins\n");
		for (int row = 0; row < 120; row++) {
			boolean target = row % 3 != 0;
			text.append("psm").append(row).append('\t').append(target ? 1 : -1).append('\t').append(row)
				.append('\t').append(row % 2 + 1).append("\t500\t").append((target ? 1000 : 0) + row)
				.append('\t').append(row * row).append("\tPEPTIDE_").append(row).append("\tPROT\n");
		}
		Files.writeString(pin, text);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			PsmDataset dataset = PinFileParser.read(pin);
			for (String feature : dataset.featureNames()) {
				out.writeUTF(feature);
			}
			for (double[] row : dataset.rawFeatures()) {
				writeDoubles(out, row);
			}
			for (boolean grouped : new boolean[]{false, true}) {
				for (boolean mixmax : new boolean[]{false, true}) {
					for (int cap : new int[]{0, 40, 1000}) {
						String name = grouped + "-" + mixmax + "-" + cap;
						List<String> flags = new ArrayList<>(List.of(pin.toString(), "--dest_dir", directory.resolve(name).toString(),
							"--quiet", "--train_fdr", "0.5", "--test_fdr", "0.5", "--max_iter", "2", "--seed", "17",
							"--max_workers", "3", "--subset-max-train", Integer.toString(cap),
							"--write_model_files", "--write_psm_files", "--write_decoy_files"));
						if (grouped) {
							flags.add("--grouped_crossfit");
						}
						if (mixmax) {
							flags.add("--mixmax");
						}
						JavaPotOptions options = CliParser.parse(flags.toArray(new String[0]));
						DeterministicRandom rng = new DeterministicRandom(17);
						int[][] folds = grouped ? FoldSplitter.splitGrouped(dataset, 3, rng, "exact.pin")
							: FoldSplitter.split(dataset, 3, rng, "exact.pin");
						for (int fold = 0; fold < folds.length; fold++) {
							for (int index : folds[fold]) {
								out.writeInt(index);
							}
							boolean[] heldOut = new boolean[dataset.size()];
							for (int index : folds[fold]) {
								heldOut[index] = true;
							}
							int[] train = new int[dataset.size() - folds[fold].length];
							int count = 0;
							for (int row = 0; row < dataset.size(); row++) {
								if (!heldOut[row]) {
									train[count++] = row;
								}
							}
							if (cap > 0 && train.length > cap) {
								train = rng.choiceWithoutReplacement(train, cap);
							}
							out.writeInt(train.length);
							for (int row : train) {
								out.writeInt(row);
							}
							PercolatorFoldModel model = PercolatorTrainer.trainFold(dataset, train, fold + 1,
								new TrainingParams(0.5, 2, null, rng.nextLong(), mixmax ? ConfidenceMode.MIXMAX : ConfidenceMode.TDC, true)).model();
							writeDoubles(out, model.means());
							writeDoubles(out, model.scales());
							writeDoubles(out, model.svm().weights());
							writeDoubles(out, new double[]{model.svm().bias(), model.svm().classWeightNeg(), model.svm().classWeightPos()});
							out.writeUTF(model.bestFeature());
							out.writeInt(model.bestFeaturePass());
							out.writeBoolean(model.bestFeatureDescending());
							writeDoubles(out, model.predict(dataset.rawFeatures()));
						}
						JavaPotRunResult result = JavaPotApi.run(options);
						for (List<JavaPotPeptide> rows : List.of(result.psms(), result.peptides())) {
							out.writeInt(rows.size());
							for (JavaPotPeptide row : rows) {
								out.writeUTF(row.psmId());
								out.writeUTF(row.peptideSequence());
								out.writeBoolean(row.isDecoy());
								writeDoubles(out, new double[]{row.score(), row.qValue(), row.pep()});
							}
						}
						for (Path path : result.writtenFiles()) {
							out.write(Files.readAllBytes(path));
						}
						out.write(Files.readAllBytes(options.saveModelFile()));
					}
				}
			}
		}
		StringBuilder hex = new StringBuilder();
		for (byte value : digest.digest()) {
			hex.append(String.format("%02x", value & 255));
		}
		return hex.toString();
	}

	private static void writeDoubles(DataOutputStream out, double[] values) throws Exception {
		out.writeInt(values.length);
		for (double value : values) {
			out.writeLong(Double.doubleToRawLongBits(value));
		}
	}
}
