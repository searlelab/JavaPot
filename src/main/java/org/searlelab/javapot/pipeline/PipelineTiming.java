package org.searlelab.javapot.pipeline;

import java.lang.management.ManagementFactory;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Timespan;

/** Opt-in coarse diagnostics. No shared mutable state or collection in normal runs. */
final class PipelineTiming {
	private PipelineTiming() {
	}

	static long start() {
		return Boolean.getBoolean("javapot.benchmark.diagnostics") ? System.nanoTime() : 0;
	}

	static void record(String stage, long start) {
		if (start == 0) {
			return;
		}
		long elapsed = System.nanoTime() - start;
		if (Boolean.getBoolean("javapot.benchmark.collect")) {
			// Diagnostic runs only: these collections must never be included in speed comparisons.
			System.gc();
		}
		long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		Stage event = new Stage();
		event.stage = stage;
		event.elapsed = elapsed;
		event.heapBytes = heap;
		event.commit();
		System.out.println("[STAGE]\t" + stage + "\tns=" + elapsed + "\theap_bytes=" + heap);
	}

	@Name("org.searlelab.javapot.Stage")
	@Label("JavaPot stage")
	static final class Stage extends Event {
		@Label("Stage")
		String stage;
		@Timespan(Timespan.NANOSECONDS)
		long elapsed;
		long heapBytes;
	}
}
