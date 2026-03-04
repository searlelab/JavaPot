package org.searlelab.javapot.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * JavaPotRunResult exposes in-memory confidence results and metadata from a JavaPot run.
 */
public record JavaPotRunResult(
	ArrayList<JavaPotPeptide> peptides,
	ArrayList<JavaPotPeptide> psms,
	Double psmPi0,
	Double peptidePi0,
	ArrayList<Path> writtenFiles
) {
}
