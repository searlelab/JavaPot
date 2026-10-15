package org.searlelab.javapot.pipeline;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;

import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.data.PsmDataset;
import org.searlelab.javapot.io.ModelIO;
import org.searlelab.javapot.io.PinFileParser;
import org.searlelab.javapot.stats.QValues;

/**
 * Opt-in subprocess entry point; deliberately runs against either frozen production JAR.
 * No JUnit agent, profiler, or Maven process belongs in the timed subprocess.
 */
public final class PerformanceReplay {
	private PerformanceReplay() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			throw new IllegalArgumentException("Usage: PerformanceReplay api|parser|kernel INPUT OUTPUT WARMUP_PIN");
		}
		Path pin = Path.of(args[1]).toRealPath();
		Path output = Path.of(args[2]);
		Files.createDirectories(output);
		Files.writeString(output.resolve("jvm.txt"), "version=" + System.getProperty("java.runtime.version")
			+ "\njava_home=" + System.getProperty("java.home") + "\nflags=" + ManagementFactory.getRuntimeMXBean().getInputArguments()
			+ "\nmax_heap_bytes=" + Runtime.getRuntime().maxMemory()
			+ "\napi_source=" + JavaPotApi.class.getProtectionDomain().getCodeSource().getLocation() + "\n");
		if (Files.size(pin) == 0) {
			throw new IllegalArgumentException("Empty PIN: " + pin);
		}
		if (args[0].equals("kernel")) {
			runKernel(pin, output);
			return;
		}
		// Equal filesystem warming, excluded from the measured interval.
		try (InputStream input = Files.newInputStream(pin)) {
			byte[] buffer = new byte[1024 * 1024];
			while (input.read(buffer) >= 0) {
				// Read without retaining the PIN bytes.
			}
		}
		Path warmup = Path.of(args[3]).toRealPath();
		for (int i = 0; i < 2; i++) {
			JavaPotApi.run(options(warmup, output.resolve("warmup" + i)));
		}
		System.gc();
		long gcBefore = gcMillis();
		long cpuBefore = cpuNanos();
		long start = System.nanoTime();
		if (args[0].equals("parser")) {
			PsmDataset dataset = PinFileParser.read(pin);
			long elapsed = System.nanoTime() - start;
			System.gc();
			long live = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
			if (Boolean.getBoolean("javapot.benchmark.histogram")) {
				Process histogram = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/jcmd").toString(),
					Long.toString(ProcessHandle.current().pid()), "GC.class_histogram")
					.redirectOutput(output.resolve("histogram.txt").toFile()).redirectErrorStream(true).start();
				if (histogram.waitFor() != 0) {
					throw new IllegalStateException("Histogram failed");
				}
			}
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DataOutputStream out = digestStream(digest)) {
				out.writeInt(dataset.size());
				out.writeInt(dataset.headers().size());
				out.writeInt(dataset.featureCount());
				for (String header : dataset.headers()) {
					writeString(out, header);
				}
				for (String feature : dataset.featureNames()) {
					writeString(out, feature);
				}
				for (int row = 0; row < dataset.size(); row++) {
					out.writeBoolean(dataset.targetAt(row));
					out.writeInt(dataset.psmGroupAt(row));
					for (int col = 0; col < dataset.headers().size(); col++) {
						writeString(out, dataset.rawValueAt(row, col));
					}
					for (double value : dataset.rawFeatures()[row]) {
						out.writeLong(Double.doubleToRawLongBits(value));
					}
				}
			}
			Files.writeString(output.resolve("parser.tsv"), "elapsed_ns\tlive_heap_bytes\trows\tfeatures\tsha256\n"
				+ elapsed + "\t" + live + "\t" + dataset.size() + "\t" + dataset.featureCount() + "\t" + hex(digest.digest()) + "\n");
			return;
		}
		if (!args[0].equals("api")) {
			throw new IllegalArgumentException("Unknown mode: " + args[0]);
		}
		JavaPotOptions options = options(pin, output.resolve("result"));
		Files.writeString(output.resolve("options.txt"), options.toString() + "\n");
		gcBefore = gcMillis();
		cpuBefore = cpuNanos();
		Instant wallStart = Instant.now();
		start = System.nanoTime();
		JavaPotRunResult result = JavaPotApi.run(options);
		long elapsed = System.nanoTime() - start;
		Instant wallEnd = Instant.now();
		long gcElapsed = gcMillis() - gcBefore;
		long cpuElapsed = cpuBefore < 0 ? -1
			: cpuNanos() - cpuBefore;
		Files.writeString(output.resolve("api-window.txt"), "start=" + wallStart + "\nend=" + wallEnd + "\n");
		if (!Files.isRegularFile(options.saveModelFile()) || ModelIO.loadModels(options.saveModelFile()).size() != 3) {
			throw new IllegalStateException("Benchmark must train all three folds, not fall back without models");
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (DataOutputStream out = digestStream(digest)) {
			writeRows(out, result.psms());
			writeRows(out, result.peptides());
			writeString(out, String.valueOf(result.psmPi0()));
			writeString(out, String.valueOf(result.peptidePi0()));
		}
		Files.writeString(output.resolve("metrics.tsv"), "elapsed_ns\tcpu_ns\tgc_ms\tpsms\tpeptides\tresult_sha256\n"
			+ elapsed + "\t" + cpuElapsed + "\t" + gcElapsed + "\t" + result.psms().size() + "\t"
			+ result.peptides().size() + "\t" + hex(digest.digest()) + "\n");
	}

	private static JavaPotOptions options(Path pin, Path output) throws Exception {
		Files.createDirectory(output);
		return new JavaPotOptions(pin, (double) 0.01f, (double) 0.01f, 500000,
			output.resolve("targets.tsv"), output.resolve("decoys.tsv"), output.resolve("model.tsv"), null, false);
	}

	private static void runKernel(Path pin, Path output) throws Exception {
		PsmDataset dataset = PinFileParser.read(pin);
		int size = Math.min(Integer.getInteger("javapot.benchmark.kernel.rows", 100000), dataset.size());
		double[] scores = Arrays.copyOf(dataset.featureColumn(dataset.featureNames()[0]), size);
		boolean[] targets = Arrays.copyOf(dataset.targets(), size);
		// Reflect only in this cross-version harness: the frozen baseline has no workspace API.
		Method reusable = null;
		Object workspace = null;
		try {
			Class<?> type = Class.forName("org.searlelab.javapot.stats.QValues$TdcWorkspace");
			workspace = type.getConstructor(int.class).newInstance(size);
			reusable = QValues.class.getMethod("tdc", double[].class, boolean[].class, boolean.class, boolean.class, type);
		} catch (ClassNotFoundException baselineWithoutWorkspace) {
			// The original algorithm allocates its own buffers on every call.
		}
		for (int i = 0; i < 12; i++) {
			kernelTdc(scores, targets, i, reusable, workspace);
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		boolean allocationAvailable = threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled();
		long allocatedBefore = allocationAvailable ? threads.getThreadAllocatedBytes(Thread.currentThread().getId()) : -1;
		long start = System.nanoTime();
		double[][] results = new double[8][];
		for (int i = 0; i < results.length; i++) {
			results[i] = kernelTdc(scores, targets, i, reusable, workspace);
		}
		long elapsed = System.nanoTime() - start;
		long allocated = allocationAvailable ? threads.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocatedBefore : -1;
		try (DataOutputStream out = digestStream(digest)) {
			for (double[] result : results) {
				for (double value : result) {
					out.writeLong(Double.doubleToRawLongBits(value));
				}
			}
		}
		Files.writeString(output.resolve("kernel.tsv"), "elapsed_ns\tallocated_bytes\tworkspace_reused\tsha256\n"
			+ elapsed + "\t" + allocated + "\t" + (workspace != null) + "\t" + hex(digest.digest()) + "\n");
	}

	private static double[] kernelTdc(double[] scores, boolean[] targets, int iteration, Method reusable, Object workspace) throws Exception {
		return reusable == null ? QValues.tdc(scores, targets, (iteration & 1) == 0, (iteration & 2) == 0)
			: (double[]) reusable.invoke(null, scores, targets, (iteration & 1) == 0, (iteration & 2) == 0, workspace);
	}

	private static long cpuNanos() {
		return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean
			? ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime() : -1;
	}

	private static long gcMillis() {
		long total = 0;
		for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
			total += Math.max(0, gc.getCollectionTime());
		}
		return total;
	}

	private static DataOutputStream digestStream(MessageDigest digest) {
		return new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
	}

	private static void writeString(DataOutputStream out, String value) throws Exception {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	private static void writeRows(DataOutputStream out, List<JavaPotPeptide> rows) throws Exception {
		out.writeInt(rows.size());
		for (JavaPotPeptide row : rows) {
			writeString(out, row.psmId());
			writeString(out, row.peptideSequence());
			out.writeBoolean(row.isDecoy());
			out.writeLong(Double.doubleToRawLongBits(row.score()));
			out.writeLong(Double.doubleToRawLongBits(row.qValue()));
			out.writeLong(Double.doubleToRawLongBits(row.pep()));
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder();
		for (byte value : bytes) {
			out.append(String.format("%02x", value & 255));
		}
		return out.toString();
	}
}
