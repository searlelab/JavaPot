package org.searlelab.javapot.cli;

import org.searlelab.javapot.pipeline.JavaPotRunner;

/**
 * JavaPotCli is the command-line entrypoint for JavaPot.
 * It parses CLI arguments, runs the pipeline, and reports fatal errors to stderr.
 */
public final class JavaPotCli {
	private JavaPotCli() {
	}

	public static void main(String[] args) {
		try {
			CliConfig config = CliParser.parse(args);
			JavaPotRunner.run(config);
		} catch (Exception e) {
			System.err.println("[ERROR] " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
