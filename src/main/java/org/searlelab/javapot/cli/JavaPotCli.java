package org.searlelab.javapot.cli;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

import org.searlelab.javapot.pipeline.JavaPotRunner;

/**
 * JavaPotCli is the command-line entrypoint for JavaPot.
 * It parses CLI arguments, runs the pipeline, and reports completion timing.
 */
public final class JavaPotCli {
	private JavaPotCli() {
	}

	/**
	 * Program entrypoint that parses CLI args and launches the JavaPot pipeline.
	 */
	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			CliParser.printHelp();
			return;
		}
		long startNanos = System.nanoTime();
		try {
			CliConfig config = CliParser.parse(args);
			JavaPotRunner.run(config);
			long elapsedNanos = System.nanoTime() - startNanos;
			System.out.println("JavaPot finished processing in " + formatDuration(elapsedNanos) + ".");
		} catch (CliParser.HelpRequestedException e) {
			CliParser.printHelp();
		}
	}

	private static String formatDuration(long elapsedNanos) {
		double seconds = elapsedNanos / 1_000_000_000.0;
		if (seconds < 60.0) {
			return formatValue(seconds) + " " + pluralize("second", seconds);
		}
		double minutes = seconds / 60.0;
		if (minutes < 60.0) {
			return formatValue(minutes) + " " + pluralize("minute", minutes);
		}
		double hours = minutes / 60.0;
		if (hours < 24.0) {
			return formatValue(hours) + " " + pluralize("hour", hours);
		}
		double days = hours / 24.0;
		return formatValue(days) + " " + pluralize("day", days);
	}

	private static String formatValue(double value) {
		if (!Double.isFinite(value) || value <= 0.0) {
			return "0.00";
		}
		BigDecimal rounded = BigDecimal.valueOf(value).round(new MathContext(2, RoundingMode.HALF_UP));
		double display = rounded.doubleValue();
		int exponent = (int) Math.floor(Math.log10(Math.abs(display)));
		int fractionDigits = Math.max(0, 2 - exponent - 1);
		return String.format(Locale.US, "%." + fractionDigits + "f", display);
	}

	private static String pluralize(String singular, double value) {
		double diff = Math.abs(value - 1.0);
		return diff < 1e-12 ? singular : singular + "s";
	}
}
