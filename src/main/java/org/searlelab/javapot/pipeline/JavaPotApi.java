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
}
