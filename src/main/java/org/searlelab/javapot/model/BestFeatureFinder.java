package org.searlelab.javapot.model;

import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.stats.LabelUpdater;

/**
 * BestFeatureFinder selects the strongest single feature as an initial ranking direction.
 * It evaluates both ascending and descending order and returns the option with the most positives at the target FDR.
 */
public final class BestFeatureFinder {
	private BestFeatureFinder() {
	}

	public static BestFeatureResult findBestFeature(PsmDataset dataset, double evalFdr) {
		String bestName = null;
		int bestPositives = -1;
		int[] bestLabels = null;
		boolean bestDesc = true;

		for (String feature : dataset.columnGroups().featureColumns()) {
			double[] values = dataset.featureColumn(feature);
			for (boolean desc : new boolean[]{true, false}) {
				int[] labels = LabelUpdater.updateLabels(values, dataset.targets(), evalFdr, desc);
				int positives = countPositives(labels);
				if (positives > bestPositives) {
					bestPositives = positives;
					bestName = feature;
					bestLabels = labels;
					bestDesc = desc;
				}
			}
		}

		if (bestPositives <= 0 || bestLabels == null || bestName == null) {
			throw new RuntimeException("No PSMs found below eval_fdr for any feature.");
		}

		return new BestFeatureResult(bestName, bestPositives, bestLabels, bestDesc);
	}

	public static BestFeatureResult findDirectional(PsmDataset dataset, String feature, double evalFdr) {
		double[] values = dataset.featureColumn(feature);
		int[] descLabels = LabelUpdater.updateLabels(values, dataset.targets(), evalFdr, true);
		int[] ascLabels = LabelUpdater.updateLabels(values, dataset.targets(), evalFdr, false);
		int descPos = countPositives(descLabels);
		int ascPos = countPositives(ascLabels);
		if (descPos >= ascPos) {
			return new BestFeatureResult(feature, descPos, descLabels, true);
		}
		return new BestFeatureResult(feature, ascPos, ascLabels, false);
	}

	public static int countPositives(int[] labels) {
		int c = 0;
		for (int v : labels) {
			if (v == 1) {
				c++;
			}
		}
		return c;
	}
}
