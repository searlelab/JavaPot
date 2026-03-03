package org.searlelab.javapot.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * TsvWriter writes tab-delimited output tables with a header row.
 * It is used for deterministic PSM and peptide confidence report generation.
 */
public final class TsvWriter {
	private TsvWriter() {
	}

	public static void write(Path path, List<String> header, List<String[]> rows) {
		try (BufferedWriter writer = Files.newBufferedWriter(path)) {
			writer.write(String.join("\t", header));
			writer.newLine();
			for (String[] row : rows) {
				writer.write(String.join("\t", row));
				writer.newLine();
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed writing TSV: " + path, e);
		}
	}
}
