package org.searlelab.javapot.pipeline;

import org.searlelab.javapot.cli.JavaPotOptions;

/**
 * JavaPotApi provides a stable programmatic entrypoint for embedding JavaPot.
 */
public final class JavaPotApi {
	private JavaPotApi() {
	}

	public static JavaPotRunResult run(JavaPotOptions options) {
		return JavaPotRunner.runForResult(options);
	}

	/**
	 * Compatibility alias for run, which now uses bounded parallel cost-grid training.
	 * The positive maxWorkers option is the total budget shared by fold workers and
	 * grid helpers for this call.
	 *
	 * @param options configuration for this invocation
	 * @return ordered results from the completed run
	 */
	public static JavaPotRunResult runWithParallelGrid(JavaPotOptions options) {
		return run(options);
	}
}
