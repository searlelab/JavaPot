package org.searlelab.javapot.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Serial, opt-in replay driver. Timing and diagnostic runs are deliberately separate. */
public final class PerformanceBenchmark {
	private static String expectedPower;
	private static final Map<String, String> INPUTS = new LinkedHashMap<>();
	static {
		INPUTS.put("exploris_gpf", "d6fc6bd9e78e07d04439bbf43d86514ad7ec2e75a0cdc07038b6351da3a93c3c");
		INPUTS.put("astral_plasma", "36aeb0e28626ee0c666dad623ca12c905c61b0f4acd70d4cfdc9c010a1e8c6f7");
		INPUTS.put("astral_wide", "5cdf80290ef02ca5710a62a348fd6859285c25b460c93f9a48af6263f1b69dd5");
		INPUTS.put("timstof_large", "84ab37ebf6dab5a79f931b7323b1ac00034e701c8294776736be8ca23e2cad81");
		INPUTS.put("stellar_variable", "c95822c5749844d28f7161ba47b4c513adc298558b13272079595d12f051075c");
		INPUTS.put("stellar_fixed", "cf11d910adede6ec7c0b478b9a7e4fa2e05db2a67d88ccf4d573a1bdaac5ec6e");
	}

	private PerformanceBenchmark() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
			throw new IllegalArgumentException("Expected mode, baseline JAR, candidate JAR, input root, and new output directory");
		}
		Path parent = Path.of(args[4]).toAbsolutePath().getParent();
		try (FileChannel channel = FileChannel.open(parent.resolve(".benchmark.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			FileLock lock = channel.tryLock()) {
			if (lock == null) {
				throw new IllegalStateException("Another benchmark driver is active in " + parent);
			}
			run(args);
		}
	}

	private static void run(String[] args) throws Exception {
		if (args.length != 5) {
			throw new IllegalArgumentException("Usage: PerformanceBenchmark kernel|small|assessment|diagnostic|memory|verify|quiet BASELINE_JAR CANDIDATE_JAR INPUT_ROOT NEW_OUTPUT");
		}
		String mode = args[0];
		Path baseline = Path.of(args[1]).toRealPath();
		Path candidate = Path.of(args[2]).toRealPath();
		Path root = Path.of(args[3]).toRealPath();
		Path output = Path.of(args[4]).toAbsolutePath();
		Files.createDirectory(output);
		String metadata = "started=" + Instant.now() + "\nmode=" + mode + "\nbaseline=" + baseline + "\nbaseline_sha256=" + sha(baseline)
			+ "\ncandidate=" + candidate + "\ncandidate_sha256=" + sha(candidate) + "\njava=" + System.getProperty("java.home")
			+ "\nversion=" + System.getProperty("java.runtime.version") + "\nflags=-Xms1g -Xmx30g -XX:+UseG1GC\n"
			+ "memory_gate_metric=peak_RSS; require corroborating total peak footprint; also report parser post-collection heap\n";
		Files.writeString(output.resolve("manifest.txt"), metadata);
		if (Files.getFileStore(output).getUsableSpace() < 20L * 1024 * 1024 * 1024) {
			throw new IllegalStateException("At least 20 GiB free space required");
		}
		if (mode.equals("quiet")) {
			expectedPower = powerSource();
			awaitQuiet(output);
			return;
		}
		for (Map.Entry<String, String> entry : INPUTS.entrySet()) {
			Path pin = pin(root, entry.getKey());
			if (Files.size(pin) == 0 || !sha(pin).equals(entry.getValue())) {
				throw new IllegalStateException("PIN identity mismatch: " + pin);
			}
			Files.writeString(output.resolve("manifest.txt"), entry.getKey() + "=" + entry.getValue() + " bytes=" + Files.size(pin) + "\n",
				StandardOpenOption.APPEND);
		}
		if (mode.equals("verify")) {
			return;
		}
		expectedPower = powerSource();
		Files.writeString(output.resolve("power-source.txt"), expectedPower + "\n");
		boolean diagnostic = mode.equals("diagnostic") || mode.equals("memory") || Boolean.getBoolean("javapot.benchmark.diagnostic");
		List<String> cases = mode.equals("small") || mode.equals("kernel")
			? List.of("exploris_gpf", "astral_plasma") : new ArrayList<>(INPUTS.keySet());
		String selected = System.getProperty("javapot.benchmark.cases");
		if (selected != null) {
			cases = Arrays.asList(selected.split(","));
			for (String name : cases) {
				if (!INPUTS.containsKey(name)) {
					throw new IllegalArgumentException("Unknown case " + name);
				}
			}
		}
		if (!List.of("small", "assessment", "kernel", "diagnostic", "memory").contains(mode)) {
			throw new IllegalArgumentException("Unknown mode " + mode);
		}
		if (!diagnostic) {
			awaitQuiet(output);
		}
		Files.writeString(output.resolve("status.txt"), diagnostic ? "DIAGNOSTIC ONLY; timing ineligible\n" : "TIMING; pending environmental checks\n");
		Path warmup = Path.of("src/test/resources/data/10k_psms_test.pin").toRealPath();
		Path harness = Path.of("target/test-classes").toRealPath();
		Path trove = Path.of(System.getProperty("user.home"), ".m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar").toRealPath();
		for (String name : cases) {
			int pairs = diagnostic ? 1 : (name.equals("exploris_gpf") || name.equals("astral_plasma") ? 5 : 3);
			long[] a = new long[pairs];
			long[] b = new long[pairs];
			long[] rssA = new long[pairs];
			long[] rssB = new long[pairs];
			long[] footprintA = new long[pairs];
			long[] footprintB = new long[pairs];
			for (int pair = 0; pair < pairs; pair++) {
				Path[] runs = new Path[2];
				for (int position = 0; position < 2; position++) {
					int arm = (position + pair) % 2;
					Path jar = arm == 0 ? baseline : candidate;
					Path run = output.resolve(name + "-" + pair + "-" + (arm == 0 ? "A" : "B"));
					Files.createDirectory(run);
					runs[arm] = run;
					List<String> command = new ArrayList<>(List.of("/usr/bin/time", "-l",
						Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Xms1g", "-Xmx30g", "-XX:+UseG1GC"));
					if (diagnostic && !mode.equals("memory") && !Boolean.getBoolean("javapot.benchmark.no_profile")) {
						command.add("-XX:StartFlightRecording=filename=" + run.resolve("run.jfr") + ",settings=profile,dumponexit=true");
						command.add("-Djavapot.benchmark.diagnostics=true");
					}
					command.addAll(List.of("-cp", jar + ":" + trove + ":" + harness, PerformanceReplay.class.getName(),
						mode.equals("kernel") ? "kernel" : mode.equals("memory") ? "parser" : "api", pin(root, name).toString(), run.toString(), warmup.toString()));
					Files.write(run.resolve("command.txt"), command);
					System.out.println("Starting " + run.getFileName() + " at " + Instant.now());
					Process process = new ProcessBuilder(command).redirectOutput(run.resolve("stdout.log").toFile())
						.redirectError(run.resolve("stderr.log").toFile()).start();
					boolean environmentValid = true;
					while (!process.waitFor(5, TimeUnit.SECONDS)) {
						try {
							environmentValid &= sample(run.resolve("environment.log"), process.descendants().mapToLong(ProcessHandle::pid).toArray());
						} catch (Exception monitoringFailure) {
							environmentValid = false;
							Files.writeString(run.resolve("monitoring-error.txt"), monitoringFailure.toString());
						}
					}
					if (process.exitValue() != 0) {
						throw new IllegalStateException("Replay failed: " + run + " exit=" + process.exitValue());
					}
					if (!diagnostic && !environmentValid) {
						Files.writeString(output.resolve("status.txt"), "TIMING INVALID: background load or unavailable monitoring; stopped, logs retained\n");
						throw new IllegalStateException("Timing stopped after environmental interference; see " + run.resolve("environment.log"));
					}
					String filename = mode.equals("kernel") ? "kernel.tsv" : mode.equals("memory") ? "parser.tsv" : "metrics.tsv";
					String[] metrics = Files.readAllLines(run.resolve(filename)).get(1).split("\t");
					(arm == 0 ? a : b)[pair] = Long.parseLong(metrics[0]);
					(arm == 0 ? rssA : rssB)[pair] = memoryMetric(run.resolve("stderr.log"), "maximum resident set size");
					(arm == 0 ? footprintA : footprintB)[pair] = memoryMetric(run.resolve("stderr.log"), "peak memory footprint");
				}
				compare(runs[0], runs[1], mode.equals("kernel") || mode.equals("memory"), mode.equals("memory") ? "parser.tsv" : mode.equals("kernel") ? "kernel.tsv" : "metrics.tsv");
			}
			boolean timingEligible = !diagnostic && spread(a) <= 0.05 && spread(b) <= 0.05
				&& !Files.readString(output.resolve("status.txt")).contains("INVALID");
			double timeRatio = pairedRatio(a, b);
			double memoryRatio = Math.max(pairedRatio(rssA, rssB), pairedRatio(footprintA, footprintB));
			String gate = mode.equals("kernel") || mode.equals("memory") ? "KERNEL_OR_MEMORY_ONLY"
				: !timingEligible ? "PENDING_ENVIRONMENT"
				: (timeRatio <= 0.90 && memoryRatio <= 1.10) || (memoryRatio <= 0.80 && timeRatio <= 1.10) ? "REFERENCE_TARGET_MET"
				: (timeRatio <= 1.0 && memoryRatio < 1.0) || (timeRatio < 1.0 && memoryRatio <= 1.0)
					? "BENEFIT_WITHOUT_REGRESSION" : "REVIEW_TRADEOFF";
			String summary = name + "\tbaseline_ns=" + median(a) + "\tcandidate_ns=" + median(b) + "\tspeedup=" + (1 - timeRatio)
				+ "\tbaseline_spread=" + spread(a) + "\tcandidate_spread=" + spread(b)
				+ "\tbaseline_peak_rss=" + median(rssA) + "\tcandidate_peak_rss=" + median(rssB)
				+ "\tbaseline_peak_footprint=" + median(footprintA) + "\tcandidate_peak_footprint=" + median(footprintB)
				+ "\tparity=EXACT\ttiming_eligible=" + timingEligible + "\tgate=" + gate + "\n";
			Files.writeString(output.resolve("summary.tsv"), summary, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			System.out.print(summary);
		}
	}

	private static Path pin(Path root, String name) {
		return root.resolve(name + "/output/search.elib.scribe2/search.elib.scribe2.features.txt");
	}

	private static void compare(Path a, Path b, boolean kernel, String file) throws Exception {
		String[] left = Files.readAllLines(a.resolve(file)).get(1).split("\t");
		String[] right = Files.readAllLines(b.resolve(file)).get(1).split("\t");
		if (!left[left.length - 1].equals(right[right.length - 1])) {
			throw new IllegalStateException("Exact numeric/result mismatch: " + a + " vs " + b);
		}
		if (!kernel) {
			for (String output : List.of("targets.tsv", "decoys.tsv", "model.tsv")) {
				if (!sha(a.resolve("result/" + output)).equals(sha(b.resolve("result/" + output)))) {
					throw new IllegalStateException("Output mismatch: " + output + " in " + a + " vs " + b);
				}
			}
		}
	}

	private static void awaitQuiet(Path output) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
		int consecutive = 0;
		while (System.nanoTime() < deadline) {
			consecutive = sample(output.resolve("quiet.log"), new long[0]) ? consecutive + 1 : 0;
			if (consecutive >= 13) {
				Files.writeString(output.resolve("quiet-status.txt"), "60 seconds quiet; warm-cache/fixed-JVM-warmup protocol\n");
				return;
			}
			Thread.sleep(5000);
		}
		Files.writeString(output.resolve("quiet-status.txt"), "BLOCKED: no 60-second quiet window within 15 minutes\n");
		throw new IllegalStateException("Timing blocked; see " + output.resolve("quiet.log"));
	}

	private static boolean sample(Path log, long[] excluded) throws Exception {
		String processes = capture("/bin/ps", "-Ao", "pid=,pcpu=,comm=");
		String thermal = capture("/usr/bin/pmset", "-g", "therm");
		String vm = capture("/usr/bin/vm_stat");
		String power = powerSource();
		Files.writeString(log, Instant.now() + "\n" + processes + thermal + vm + power + "\n",
			StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		boolean quiet = !thermal.contains("Error:") && power.equals(expectedPower);
		for (String line : thermal.split("\n")) {
			String lower = line.toLowerCase(Locale.ROOT);
			if ((lower.contains("thermal") || lower.contains("performance warning"))
				&& !lower.contains("no thermal warning") && !lower.contains("no performance warning")
				&& !line.contains("Thermal_Level")) {
				// Unknown warning formats cannot establish a quiet thermal state.
				quiet = false;
			}
			if (line.contains("CPU_Speed_Limit") || line.contains("CPU_Scheduler_Limit")) {
				quiet &= Integer.parseInt(line.replaceAll("[^0-9]", "")) >= 100;
			} else if (line.contains("Thermal_Level")) {
				quiet &= Integer.parseInt(line.replaceAll("[^0-9]", "")) == 0;
			}
		}
		for (String line : processes.split("\n")) {
			String[] fields = line.trim().split("\\s+", 3);
			if (fields.length < 3) {
				continue;
			}
			long pid = Long.parseLong(fields[0]);
			if (pid != ProcessHandle.current().pid() && Arrays.stream(excluded).noneMatch(id -> id == pid)
				&& Double.parseDouble(fields[1]) > 10.0) {
				quiet = false;
			}
		}
		String swap = vm.lines().filter(line -> line.startsWith("Swapouts:")).findFirst().orElseThrow();
		long current = Long.parseLong(swap.replaceAll("[^0-9]", ""));
		Path previous = log.resolveSibling("swapouts.txt");
		if (Files.exists(previous) && current > Long.parseLong(Files.readString(previous).trim())) {
			quiet = false;
		}
		Files.writeString(previous, Long.toString(current));
		return quiet;
	}

	private static String powerSource() throws Exception {
		return capture("/usr/bin/pmset", "-g", "batt").lines()
			.filter(line -> line.startsWith("Now drawing from")).findFirst().orElseThrow();
	}

	private static String capture(String... command) throws Exception {
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.waitFor() != 0) {
			throw new IOException("Monitoring command failed: " + Arrays.toString(command) + ": " + out);
		}
		return out;
	}

	private static long memoryMetric(Path file, String metric) throws IOException {
		for (String line : Files.readAllLines(file)) {
			if (line.contains(metric)) {
				return Long.parseLong(line.trim().split("\\s+")[0]);
			}
		}
		throw new IOException("Missing " + metric + ": " + file);
	}

	private static double pairedRatio(long[] baseline, long[] candidate) {
		double[] ratios = new double[baseline.length];
		for (int i = 0; i < ratios.length; i++) {
			ratios[i] = candidate[i] / (double) baseline[i];
		}
		Arrays.sort(ratios);
		return ratios[ratios.length / 2];
	}

	private static double median(long[] input) {
		long[] sorted = input.clone();
		Arrays.sort(sorted);
		return sorted[sorted.length / 2];
	}

	private static double spread(long[] input) {
		return (Arrays.stream(input).max().orElseThrow() - Arrays.stream(input).min().orElseThrow()) / median(input);
	}

	private static String sha(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = Files.newInputStream(path)) {
			byte[] bytes = new byte[1024 * 1024];
			int length;
			while ((length = input.read(bytes)) >= 0) {
				digest.update(bytes, 0, length);
			}
		}
		StringBuilder result = new StringBuilder();
		for (byte value : digest.digest()) {
			result.append(String.format("%02x", value & 255));
		}
		return result.toString();
	}
}
