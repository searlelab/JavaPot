import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;

import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.JavaPotExecutor;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorExecutionData;
import edu.washington.gs.maccoss.encyclopedia.algorithms.percolator.PercolatorPeptide;
import edu.washington.gs.maccoss.encyclopedia.algorithms.scribe.ScribeTwoSearchParameters;
import edu.washington.gs.maccoss.encyclopedia.datastructures.SearchParameters;
import edu.washington.gs.maccoss.encyclopedia.utils.Pair;

/** Compile separately against the existing embedding application; no JavaPot build dependency. */
public final class WrapperReplay {
	public static void main(String[] args) throws Exception {
		Path input = Path.of(args[0]).toRealPath();
		Path output = Path.of(args[1]).toAbsolutePath();
		Files.createDirectory(output);
		HashMap<String, String> map = ScribeTwoSearchParameters.getDefaultParameters();
		map.put("-percolatorThreshold", "0.01");
		map.put("-percolatorTrainingFDR", "0.0");
		SearchParameters parameters = ScribeTwoSearchParameters.parseParameters(map);
		PercolatorExecutionData data = new PercolatorExecutionData(input.toFile(), output.resolve("unused.fasta").toFile(),
			output.resolve("targets.tsv").toFile(), output.resolve("decoys.tsv").toFile(),
			output.resolve("proteins.tsv").toFile(), output.resolve("decoy-proteins.tsv").toFile(), parameters, false);
		Pair<ArrayList<PercolatorPeptide>, Float> result = JavaPotExecutor.executePercolatorTSV(
			parameters.getPercolatorVersionNumber(), data, parameters.getEffectivePercolatorThreshold(), parameters.getAAConstants(), 1);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
			out.writeUTF(String.valueOf(result.y));
			out.writeInt(result.x.size());
			for (PercolatorPeptide peptide : result.x) {
				out.writeUTF(peptide.getPsmID());
				out.writeUTF(peptide.getProteinIDs());
				out.writeUTF(peptide.getPeptideModSeq());
				out.writeUTF(peptide.getPeptideSeq());
				out.writeInt(Float.floatToRawIntBits(peptide.getScore()));
				out.writeInt(Float.floatToRawIntBits(peptide.getQValue()));
				out.writeInt(Float.floatToRawIntBits(peptide.getPosteriorErrorProb()));
				out.writeByte(peptide.getPrecursorCharge());
				out.writeLong(Double.doubleToRawLongBits(peptide.getPrecursorMZ()));
				out.writeInt(Float.floatToRawIntBits(peptide.getRetentionTimeInSec()));
				out.writeBoolean(peptide.isPSMIDDecoy());
			}
		}
		StringBuilder hex = new StringBuilder();
		for (byte value : digest.digest()) {
			hex.append(String.format("%02x", value & 255));
		}
		Files.writeString(output.resolve("returned.sha256"), hex + "\n");
		Files.writeString(output.resolve("summary.txt"), "peptides=" + result.x.size() + "\npi0=" + result.y + "\n");
	}
}
