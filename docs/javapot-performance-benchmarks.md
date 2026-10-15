# JavaPot performance replay

The approved scope is frozen in `uu-javapot-performance.md`. This guide describes executable validation, not a change to that plan.

## Build and freeze an arm

Run from the JavaPot repository. Build serially, outside benchmark windows:

```sh
mvn -am -DskipTests clean test-compile jar:jar
```

Copy `target/javapot-1.0.5.jar` to a **new** arm directory under `benchmark-results/`, record its SHA-256 and the source revision/diff, and make the copy read-only. Do not overwrite the baseline or install a candidate in the local Maven repository. The baseline for this work is commit `8a101109d81e26e9927fefd8120d0865b400023c`; its original production JAR, source archive, dependency JAR, hashes, and diagnostic counterpart are retained under `benchmark-results/javapot-performance-2026-10-15/baseline/`.

The driver uses only JDK APIs and existing dependencies. It launches production JARs in separate JVMs with `-Xms1g -Xmx30g -XX:+UseG1GC`, the same JDK, a fixed pair of 10k-PIN warmups, and a full untimed input read. The requested heap is not a claim about the minimum completing heap. Each run trains with the actual embedding settings, including `(double) 0.01f`, the 500,000-row cap, and three fold workers.

## Commands

Set paths to the frozen artifacts and the retained inputs; shell variables here are ordinary path values:

```sh
baseline="$PWD/benchmark-results/javapot-performance-2026-10-15/baseline/javapot.jar"
candidate="$PWD/benchmark-results/javapot-performance-2026-10-15/FINAL_ARM/javapot.jar"
inputs="/Users/searle.brian/Documents/java/encyclopedia_benchmarking/uu_scribetwo_multi_instrument_diag_2026_10_14"
results="$PWD/benchmark-results/javapot-performance-2026-10-15"
```

Each output directory must be new. These are the primary timing modes:

```sh
java -cp target/test-classes:target/classes org.searlelab.javapot.pipeline.PerformanceBenchmark kernel "$baseline" "$candidate" "$inputs" "$results/kernel-timing"
java -cp target/test-classes:target/classes org.searlelab.javapot.pipeline.PerformanceBenchmark small "$baseline" "$candidate" "$inputs" "$results/small-timing"
java -cp target/test-classes:target/classes org.searlelab.javapot.pipeline.PerformanceBenchmark assessment "$baseline" "$candidate" "$inputs" "$results/assessment-timing"
```

The driver validates all six SHA-256 input identities, requires 20 GiB free space, and serializes drivers through a file lock. It uses five pairs for small inputs and three for larger inputs, alternating A/B and B/A order. Override cases with `-Djavapot.benchmark.cases=astral_wide` or a comma-separated list when appropriate. Do not reduce pair counts for acceptance.

Timing modes wait up to 15 minutes for 60 quiet seconds and monitor other processes, thermal messages, and swapouts. They need permission to read macOS process/system statistics. No unrelated process is terminated. The driver stops after its active child finishes if monitoring fails or background load returns, preserving logs rather than spending additional pairs on invalid conditions. Timing is also ineligible if either arm's range/median exceeds 5%. Unrecognized thermal/performance warnings invalidate the window. The monitor cannot prove a physical CPU temperature from an absent thermal warning: preserve the warmup/power protocol, inspect logs for drift, and do not accept a marginal claim without repeatable measurements.

The confidence kernel uses up to the first 100,000 retained rows and their first feature, both directions, and both pseudocount modes. It records thread allocation bytes separately from process memory. When the selected JAR exposes `QValues.TdcWorkspace`, the cross-version harness creates one before warmup and reuses it, as initial feature selection does. Reflection is confined to this harness so it can also load the original JAR. Total API timing includes workspace construction; kernel timing measures steady reuse.

To continue exactness/resource assessment when quiet timing is unavailable:

```sh
java -Djavapot.benchmark.diagnostic=true -Djavapot.benchmark.no_profile=true -cp target/test-classes:target/classes org.searlelab.javapot.pipeline.PerformanceBenchmark small "$baseline" "$candidate" "$inputs" "$results/small-diagnostic"
java -cp target/test-classes:target/classes org.searlelab.javapot.pipeline.PerformanceBenchmark diagnostic "$baseline" "$candidate" "$inputs" "$results/profile-assessment"
java -Djavapot.benchmark.cases=exploris_gpf,stellar_fixed -cp target/test-classes:target/classes org.searlelab.javapot.pipeline.PerformanceBenchmark memory "$baseline" "$candidate" "$inputs" "$results/parser-memory"
```

Diagnostic modes use one pair and **never qualify a speed claim**. The `diagnostic` mode records JFR; `memory` probes parser post-collection heap and exact raw-cell/feature-bit fingerprints. Treat parser live heap as a separate metric from full-process peak RSS. `/usr/bin/time -l` RSS includes JVM startup, warmups, and the output fingerprint, consistently for both arms. macOS peak memory footprint can include compressed memory and is also retained in the logs.

For a targeted capacity trial, use the same subprocess entry point at a fixed smaller heap, outside primary timing. This example retains all embedding settings, including the 500,000 training cap:

```sh
trove="$PWD/benchmark-results/javapot-performance-2026-10-15/baseline/trove4j.jar"
/usr/bin/time -l java -Xms1g -Xmx8g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -cp "$candidate:$trove:$PWD/target/test-classes" org.searlelab.javapot.pipeline.PerformanceReplay api "$inputs/stellar_fixed/output/search.elib.scribe2/search.elib.scribe2.features.txt" "$results/NEW-fixed-8g" "$PWD/src/test/resources/data/10k_psms_test.pin"
```

Repeat with the frozen original JAR to establish same-heap capacity separately from same-result comparison against its successful 30 GiB run. The authorized GPF capacity extension uses the retained `stellar_gpf` PIN with `-Xmx16g` and the same cap; verify its additional SHA-256 from the capacity addendum first. Completion does not establish minimum heap or recreate upstream raw-search coexistence. The recorded fixed-Stellar candidate capacity trial additionally enabled JFR for qualitative remaining-hotspot analysis; that run is timing ineligible.

`PipelineTiming` supplies optional coarse stage events and `[STAGE]` log records with `-Djavapot.benchmark.diagnostics=true`. Add `-Djavapot.benchmark.collect=true` only for separate live-heap probes: it forces collection at checkpoints, invalidating speed comparisons. Stage heap without this property is current occupancy, **not retained heap**. Stage timing includes nested operations; do not sum overlapping stage durations.

## Exactness gates

Each API replay rejects a missing model or anything other than three trained fold models. It fingerprints every ordered API PSM/peptide ID, sequence, label, raw numeric bit pattern, and pi0 value, then compares target/decoy/model file SHA-256 hashes. Matching discovery counts alone is insufficient. Differences throw and stop the run; `summary.tsv` reports parity only after all comparisons pass.

Focused coverage and the full suite are executable without retained workstation inputs:

```sh
mvn -am -Dtest=QValuesExactnessTest,StableIntSortTest,PinFileParserDifferentialTest,ExactPerformanceParityTest test
mvn clean test
```

`performance-baseline.sha256` was generated by `ExactParitySnapshot` loaded against the frozen original baseline JAR, **before** candidate validation. It covers parsed numeric bits, fold/sample order, scaler values, model weights/bias/class weights, predictions, API results, and output/model bytes for ordinary/grouped, TDC/mixmax, and unlimited/active/oversized caps. Never regenerate it from a candidate to make a regression pass. The confidence and parser reference classes are frozen copies from the same baseline, with independent reference sorting.

## Retained-PIN wrapper replay

`src/test/resources/performance/WrapperReplay.java` is compiled separately against the existing EncyclopeDIA application so JavaPot gains no embedding dependency. Put the selected JavaPot JAR **before** all EncyclopeDIA classes/JARs on each subprocess classpath. Record the actual wrapper code source and embedding artifact hash. Use the same input path and parameter map for both arms.

The harness calls the real `JavaPotExecutor.executePercolatorTSV`, writes only to a new output directory, and fingerprints returned peptide fields and pi0. Compare `targets.tsv`, `decoys.tsv`, `targets.tsv.model`, and `returned.sha256` byte-for-byte. No raw search is invoked and no embedding production source needs modification. This checks wrapper packaging/loading; it does not recreate upstream raw-search memory coexistence.

## Acceptance

The predeclared full-pipeline memory gate uses peak RSS. Report parser post-collection heap, GC time, CPU, and allocation alongside it, without substituting one for another. In response to the observed >30 GB process size, also require corroborating reduction in macOS peak memory footprint; an RSS reduction caused by compression alone is not a memory win. Lower-heap fixed-Stellar completion is an additional capacity check, not a minimum-heap claim. Require exact parity. Reference targets are at least 10% API speedup with at most 10% memory growth, or at least 20% memory reduction with at most 10% slowdown. The user clarified that a clear memory saving with flat runtime is worth retaining even below 20%; these targets guard against adverse tradeoffs, not against smaller unambiguous benefits. Report median and spread, retain the measurements, and mark acceptance pending if a quiet, repeatable timing window was unavailable.

The user subsequently accepted the large combined memory reduction even if its observed slowdown cannot be recovered. Keep that explicit decision separate from the driver's reference-target classification; see the capacity addendum. It does not relax exactness or turn diagnostic timings into a speed claim.
