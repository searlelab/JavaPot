package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.searlelab.javapot.testutil.TestCompat.writeString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.searlelab.javapot.cli.CliParser;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.io.ModelIO;
import org.searlelab.javapot.io.PinFileParser;
import org.searlelab.javapot.model.FoldSplitter;
import org.searlelab.javapot.model.PercolatorFoldModel;
import org.searlelab.javapot.model.PercolatorTrainer;
import org.searlelab.javapot.model.TrainingParams;
import org.searlelab.javapot.stats.ConfidenceMode;
import org.searlelab.javapot.stats.ScoreCalibrator;
import org.searlelab.javapot.util.DeterministicRandom;

class SubsetTrainingIntegrationTest {
	@TempDir
	Path tempDir;

	@Test
	void activeSubsetTrainsOnSeededSampleAndScoresAllPsms() throws Exception {
		verifyActiveSubset(false);
		verifyActiveSubset(true);
	}

	private void verifyActiveSubset(boolean grouped) throws Exception {
		Path pin = writePin();
		PsmDataset dataset = PinFileParser.read(pin);
		JavaPotOptions first = options(pin, "first", 17L, 1, "40", grouped);
		JavaPotOptions repeat = options(pin, "repeat", 17L, 3, "40", grouped);
		JavaPotOptions different = options(pin, "different", 29L, 1, "40", grouped);
		JavaPotRunResult result = JavaPotApi.run(first);
		JavaPotRunResult repeated = JavaPotApi.run(repeat);
		JavaPotApi.run(different);

		assertEquals(result.psms(), repeated.psms());
		assertEquals(result.peptides(), repeated.peptides());
		assertEquals(Files.readString(first.saveModelFile()), Files.readString(repeat.saveModelFile()));
		assertFalse(Files.readString(first.saveModelFile()).equals(Files.readString(different.saveModelFile())));

		List<PercolatorFoldModel> models = ModelIO.loadModels(first.saveModelFile());
		assertEquals(first.folds(), models.size(), "Training must succeed instead of falling back");
		DeterministicRandom rng = new DeterministicRandom(first.seed());
		int[][] folds = grouped
			? FoldSplitter.splitGrouped(dataset, first.folds(), rng, pin.getFileName().toString())
			: FoldSplitter.split(dataset, first.folds(), rng, pin.getFileName().toString());
		Map<String, JavaPotPeptide> scored = result.psms().stream()
			.collect(Collectors.toMap(JavaPotPeptide::psmId, row -> row));
		assertEquals(dataset.size(), scored.size());
		for (int fold = 0; fold < folds.length; fold++) {
			boolean[] heldOut = new boolean[dataset.size()];
			for (int row : folds[fold]) {
				heldOut[row] = true;
			}
			int[] candidates = IntStream.range(0, dataset.size()).filter(row -> !heldOut[row]).toArray();
			assertTrue(candidates.length > first.subsetMaxTrain());
			int[] selected = rng.choiceWithoutReplacement(candidates, first.subsetMaxTrain());
			TrainingParams params = new TrainingParams(first.trainFdr(), first.maxIter(), first.direction(),
				rng.nextLong(), ConfidenceMode.MIXMAX, true);
			PercolatorFoldModel expected = PercolatorTrainer.trainFold(dataset, selected, fold + 1, params).model();
			// Compare effective models because persistence converts normalized weights to raw units.
			assertArrayEquals(expected.predict(dataset.rawFeatures()), models.get(fold).predict(dataset.rawFeatures()), 1e-10);
			double[][] testRows = new double[folds[fold].length][];
			boolean[] targets = new boolean[testRows.length];
			for (int i = 0; i < testRows.length; i++) {
				testRows[i] = dataset.rawFeatures()[folds[fold][i]];
				targets[i] = dataset.targetAt(folds[fold][i]);
			}
			double[] predictions = models.get(fold).predict(testRows);
			if (!grouped) {
				predictions = ScoreCalibrator.calibrate(predictions, targets, first.testFdr());
			}
			for (int i = 0; i < testRows.length; i++) {
				int row = folds[fold][i];
				JavaPotPeptide psm = scored.get("psm" + row);
				assertEquals(predictions[i], psm.score(), 1e-10);
				assertTrue(Double.isFinite(psm.qValue()));
				assertTrue(Double.isFinite(psm.pep()));
			}
		}
	}

	@Test
	void zeroAndOmittedCapMatchUncappedTraining() throws Exception {
		Path pin = writePin();
		JavaPotOptions omitted = options(pin, "omitted", 17L, 1, null, false);
		JavaPotOptions zero = options(pin, "zero", 17L, 1, "0", false);
		JavaPotOptions oversized = options(pin, "oversized", 17L, 1, "1000", false);
		JavaPotRunResult baseline = JavaPotApi.run(omitted);
		assertEquals(baseline.psms(), JavaPotApi.run(zero).psms());
		assertEquals(baseline.psms(), JavaPotApi.run(oversized).psms());
		assertEquals(Files.readString(omitted.saveModelFile()), Files.readString(zero.saveModelFile()));
		assertEquals(Files.readString(omitted.saveModelFile()), Files.readString(oversized.saveModelFile()));
	}

	@Test
	void apiDefaultsToPrimitiveZeroAndRejectsNegativeCap() {
		Path pin = tempDir.resolve("input.pin");
		JavaPotOptions defaults = new JavaPotOptions(pin, 0.5, 0.5, null, null, false);
		assertEquals(0, defaults.subsetMaxTrain());
		assertThrows(IllegalArgumentException.class, () -> new JavaPotOptions(pin, 0.5, 0.5, -1, null, null, false));
	}

	private JavaPotOptions options(Path pin, String name, long seed, int workers, String cap, boolean grouped) {
		List<String> args = new ArrayList<>(List.of(
			pin.toString(), "--dest_dir", tempDir.resolve(name).toString(), "--quiet",
			"--train_fdr", "0.5", "--test_fdr", "0.5", "--max_iter", "2",
			"--seed", Long.toString(seed), "--max_workers", Integer.toString(workers),
			"--mixmax", "--write_model_files"
		));
		if (cap != null) {
			args.addAll(List.of("--subset-max-train", cap));
		}
		if (grouped) {
			args.add("--grouped_crossfit");
		}
		return CliParser.parse(args.toArray(new String[0]));
	}

	private Path writePin() throws Exception {
		Path pin = tempDir.resolve("subset.pin");
		StringBuilder contents = new StringBuilder("SpecId\tLabel\tScanNr\tExpMass\tfeatA\tfeatB\tPeptide\tProteins\n");
		for (int row = 0; row < 120; row++) {
			boolean target = row % 3 != 0;
			contents.append("psm").append(row).append('\t').append(target ? 1 : -1).append('\t')
				.append(row).append("\t500\t").append((target ? 1000 : 0) + row)
				.append('\t').append(row * row).append("\tPEPTIDE_").append(row).append("\tPROT\n");
		}
		writeString(pin, contents.toString());
		return pin;
	}
}
