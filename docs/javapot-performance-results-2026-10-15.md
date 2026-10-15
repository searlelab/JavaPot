# JavaPot performance implementation, 2026-10-15

Canonical plan: [uu-javapot-performance.md](uu-javapot-performance.md). Subsequent capacity and acceptance decisions: [capacity addendum](javapot-performance-capacity-addendum.md). Runnable commands and measurement protocol: [benchmark guide](javapot-performance-benchmarks.md).

## Implementation and scientific constraints

`QValues.tdc` keeps the two original stable sorts, arithmetic sequence, minimum-FDR behavior, and final mapping while removing redundant arrays. `JavaPotRunner.chooseStartFeature` now reuses one fixed-size `QValues.TdcWorkspace` for the duration of its feature scan; returned q-value arrays remain independent. `LabelUpdater` has an additive workspace overload, and ordinary callers retain their existing API. `StableIntSort.merge` skips ordered runs and exchanges strictly separated reversed runs without changing tie order.

`PinFileParser.parse` detects the format while parsing, resolves feature indices once, and retains normalized original row text. `PsmDataset.fromTabRows`, `rawValueAt`, `rows`, and `withColumnGroups` retain exact lexical cell values without retaining a String object per cell. The public matrix constructor keeps its original alias behavior and defensive-copy contracts. Features still use doubles, in the original order.

`PercolatorTrainer`, `ClassWeightGridSearch`, and `JavaPotRunner` avoid private matrix copies where consumers are read-only. A measured alternative retained `PercolatorTrainer.takeRows` row copies for locality while removing its allocate-then-replace waste; that version improved both small diagnostic replays by approximately 16–17% relative to fully shared shuffled training rows, without an observed peak-memory penalty, and is retained. `PercolatorFoldModel.predict` normalizes and scores blocks of 4,096 rows with the original arithmetic. `JavaPotRunner.assignConfidenceAndBuildOutputs` builds only requested TSV families; API PSM and peptide records remain complete. No solver, convergence, floating-point precision, training membership, RNG consumption, normalization order, or training setting changes are included.

`PipelineTiming` supplies normally disabled, invocation-local coarse diagnostics. Forced collections are confined to separately requested diagnostic runs. No production dependency or tooling changes were introduced.

## Reproducibility and exactness

The authoritative original baseline is commit `8a101109d81e26e9927fefd8120d0865b400023c`. Frozen baseline and candidate artifacts, diffs, input identities, JVM options, commands, logs, JFR recordings, and comparison reports are under the ignored `benchmark-results/javapot-performance-2026-10-15/` directory. The original baseline JAR SHA-256 is `d4d50496f9b0fbeab37c834d81221ed6acd501accf7937d788b945e0f3662227`. Nothing was installed over the embedding dependency.

Retained input SHA-256 checks precede replay. The standalone API settings use the wrapper's promoted float FDR, `(double) 0.01f`, a 500,000-row cap, seed 1, three folds/workers, ten iterations, one retry, ordinary cross-validation, and TDC. Every successful API replay requires three saved trained fold models. API fingerprints cover ordered IDs/sequences/labels, raw score/q-value/PEP bits, and pi0; target/decoy/model files are compared byte-for-byte by SHA-256.

The checked-in `performance-baseline.sha256` was generated from the original JAR, before candidate validation. `ExactParitySnapshot.capture` covers feature bits, folds and sampled row order, scaler parameters, class weights and SVM parameters, predictions, API results, model bytes, and outputs for ordinary/grouped training, TDC/mixmax, and unlimited/active/oversized caps. Independent frozen confidence/sort/parser reference implementations provide differential coverage. Additional tests cover concurrent API calls, lexical raw cells, signed zero/nonfinite values, irregular matrix compatibility, block boundaries, input immutability, output routing, loaded models, retries, and fallback paths.

## Incremental observations

The initial fixed-Stellar diagnostic histogram found approximately 16 GB of retained String objects, backing byte arrays, and per-row cell-reference arrays. Parsed double rows accounted for approximately 2.1 GB. This justified compact raw text before adding a database or changing training.

Confidence-only and simpler-parser snapshots remain available independently. Confidence-only passed exact replay on all six PINs but did not materially solve the full-process memory problem. Compact raw text passed all six exact API/output/model comparisons. In its single diagnostic pair, timsTOF peak footprint decreased from 14.687 to 12.143 GB with essentially flat runtime (89.80 versus 89.87 seconds). Variable Stellar decreased from 17.772 to 9.356 GB. These are decimal GB, not GiB. The user explicitly approved retaining the timsTOF benefit below the original 20% reference target.

The first combined matrix/view candidate further reduced timsTOF peak footprint to 4.462 GB from 14.930 GB, with 91.20 versus 111.30 seconds API time in one diagnostic pair. Variable Stellar decreased from 16.297 to 4.619 GB with 96.18 versus 114.11 seconds. The user explicitly accepted this larger memory benefit even if runtime cannot be recovered. The retained training-locality alternative and confidence-buffer reuse subsequently improved the timsTOF tradeoff to 89.54 versus 94.87 seconds, with peak footprint 16.007 versus 5.292 GB. Do not treat these one-pair timings as a quiet, repeated speed claim.

## Measurement limits

All benchmark subprocesses run serially; builds, tests, and separate profiling jobs are excluded from benchmark windows. Unprofiled API runs use identical G1, `-Xms1g -Xmx30g`, warmed input files, two fixed untimed warmups, fresh JVMs, and fresh output locations. The predeclared memory metric is peak RSS, corroborated by macOS peak footprint; parser post-collection heap is reported separately. Lower-heap completion checks do not establish a minimum required heap.

Diagnostic one-pair measurements establish exactness and gross resource differences, not speed acceptance. Background Microsoft Defender and occasional desktop activity have prevented consistent quiet conditions during diagnostic work. The primary driver requires 60 sampled quiet seconds, stable power, no reported thermal pressure or swap growth, then alternating A/B and B/A runs with five small-input or three large-input pairs. Timing eligibility also requires spreads no greater than 5%. An absent thermal warning alone does not establish temperature consistency.

No whole raw-data searches were run. Retained-PIN wrapper replay does not recreate upstream raw-search memory coexistence. Stellar GPF lacks a successful current-HEAD full-file baseline output, so capacity completion there cannot establish full-file original/candidate parity.

The final retained-data kernel fingerprints also match exactly. Across eight steady repeated calls, Exploris allocation falls from 49.224 to 5.626 MB and Astral-plasma from 56.002 to 6.401 MB (approximately 88.6% lower). Workspace creation is outside this kernel interval and inside complete API timing. These allocation reductions do not independently qualify as a full-pipeline speed or memory claim.

## Frozen final candidate: complete API replay

Candidate JAR SHA-256: `e135e9d906fef809ac2d2eb64340187e167642e21c254be0f51e5f8b1fefceb9`. All six retained PIN identities verified; all six API fingerprints and target/decoy/model file hashes match the original baseline. No fallback or underperformance messages occurred in these full replays.

One unprofiled diagnostic pair per input, fixed 30 GiB maximum heap; **timing ineligible**. Memory columns are decimal GB. RSS and footprint must both be considered: they differ substantially in the baseline, consistent with the recorded system compression pressure. Do not attribute the entire gap to a single mechanism.

| PIN | Baseline API s | Candidate API s | Baseline RSS GB | Candidate RSS GB | Baseline footprint GB | Candidate footprint GB | Results/models |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| exploris_gpf | 9.91 | 10.37 | 3.09 | 1.76 | 3.07 | 1.74 | exact |
| astral_plasma | 9.43 | 10.70 | 3.01 | 2.02 | 2.98 | 2.00 | exact |
| astral_wide | 40.43 | 49.65 | 8.19 | 2.74 | 8.17 | 2.71 | exact |
| timstof_large | 89.54 | 94.87 | 16.02 | 5.31 | 16.01 | 5.29 | exact |
| stellar_variable | 88.61 | 97.39 | 15.22 | 5.15 | 15.21 | 5.12 | exact |
| stellar_fixed | 219.64 | 211.83 | 22.69 | 13.90 | 32.55 | 13.88 | exact |

The large memory reductions are supported by both RSS and footprint. Astral-plasma and Astral-wide exceed the original 10% slowdown reference in these diagnostic pairs; they remain explicitly reported tradeoffs under the user’s later acceptance. Other timing differences also remain provisional, rather than passing a formal gate on a single pair.

## Retained dataset memory

The separate fixed-Stellar parser probe reports post-collection live heap of **18.071 → 4.858 GB**, a **73.1% reduction**. Its complete raw-cell and numeric-feature-bit fingerprints match (`19c0f7fff4ab716651c5c951d9546215bb023857fbd96ba0287fb4300b7b64cb`), covering all 3,992,216 rows and 62 features. This is live dataset memory, not the full API process peak. Evidence: `final-parser-memory/stellar_fixed-0-{A,B}/parser.tsv` under the benchmark root. Collection and full-table fingerprinting are excluded from primary timing.

## Smaller-heap fixed-Stellar capacity

At `-Xms1g -Xmx8g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError`, the original baseline terminates with `OutOfMemoryError: Java heap space` while parsing (exit 3). The candidate completes the full API run and saves all three trained models, retaining the 500,000-row cap. Its full API fingerprint and target/decoy/model bytes match the original successful 30 GiB run exactly. Peak process footprint is 8.886 GB.

Evidence: `fixed-baseline-8g/{command.json,stdout.log,exit.txt}` and `fixed-8g/{command.json,jvm.txt,metrics.tsv,parity.txt,stderr.log}` under the benchmark root. The candidate capacity trial includes JFR for remaining-hotspot analysis, so it is not a matched speed comparison. Eight GiB is a demonstrated completing heap, not a minimum-heap claim. This isolated replay does not establish that the full upstream raw search fits in eight GiB.

## Stellar GPF capacity extension

The independently verified 6,350,420,228-byte retained GPF PIN (9,586,485 rows, 62 features) completes at **16 GiB maximum heap**, retaining the original **500,000-row training cap**. All three fold models were saved; no fallback or underperformance messages occurred. API time is 445.49 seconds in this single capacity trial, with peak RSS 17.632 GB and peak footprint 17.618 GB. It produces 2,054,831 representative PSMs and 737,052 peptide records, including 45,939 passing peptides at the configured threshold.

Evidence: `gpf-input.sha256` and `gpf-16g/{command.json,jvm.txt,stdout.log,metrics.tsv,stderr.log,exit.txt}`. The API fingerprint is `98f4f6096364f43b699f969246d1fc6482927dbbf343e918191d6b35e537cb6c`. This is capacity evidence, **not full-file baseline parity**: the historical original embedding run failed parsing at 30 GiB and no successful current-HEAD GPF reference output exists. Exact original/candidate parity is established separately on the six agreed PINs and differential tests.

The exact in-memory storage path was sufficient for this retained GPF case. No disk-backed database, automatic fallback, or smaller-cap experiment was added. Those remain options if a larger future case requires them; lowering the training cap would not solve raw-row parsing memory.

## Embedding wrapper and remaining hotspots

The actual `JavaPotExecutor.executePercolatorTSV` source was compiled separately against the existing EncyclopeDIA runtime. Each fresh JVM placed the selected frozen JavaPot JAR first on its classpath. Exploris and fixed Stellar both match exactly for target/decoy files, packaged model bytes, returned peptide fields, and pi0 handling. Returned counts are 35,326 and 10,677, respectively; returned pi0 is 1.0 for both arms. No EncyclopeDIA production files or installed JavaPot dependency were modified.

Evidence: `wrapper-exploris_gpf-parity.txt`, `wrapper-stellar_fixed-parity.txt`, each wrapper run's `command.json` and `result/{returned.sha256,summary.txt}`, and `wrapper-tools/{wrapper-source.sha256,runtime.sha256}`. The typed replay source is `src/test/resources/performance/WrapperReplay.java`.

The candidate eight-GiB JFR was restricted to its recorded API window for qualitative remaining-hotspot analysis. Among 24,843 execution samples, the first JavaPot frame was `LinearSvmModel.dot` in 9,701, `LinearSvmModel.fit` in 4,535, and `LinearSvmModel.objective` in 709. Confidence comparator and merge work also remains prominent. These are CPU sample counts across threads, not wall-time percentages or matched baseline ratios. Evidence: `fixed-8g/{api-window.txt,hotspots.txt,run.jfr}`. The remaining dominant SVM work is numerical; no solver, convergence, precision, or arithmetic-order changes were made.

## Executed checks

Focused commands included:

```sh
mvn -am -Dtest=QValuesExactnessTest,StableIntSortTest,ExactPerformanceParityTest test
mvn -am -Dtest=PercolatorTrainerTest,ExactPerformanceParityTest,BoundedScoringTest test
```

`mvn clean test` passed after each completed improvement area. The benchmark handoff suite reported **152 tests, zero failures/errors, two existing skips**. Subsequent edits only strengthened the opt-in benchmark monitor; `mvn -am -DskipTests test-compile` passed afterward. `mvn -am -DskipTests test-compile jar:jar` built the frozen final artifact. The required final `mvn -am -Dmaven.test.skip=true compile` and `git diff --check` passed. At that handoff, frozen candidate class bytes matched the compiled production classes; frozen and runtime Trove JAR bytes matched. The later review fix below is separate from that frozen artifact.

Kernel, small-input, all-six assessment, parser-memory, lower-heap capacity, and wrapper command lines and logs are retained in their benchmark directories. The final six-case diagnostic replay is `final-assessment/`; the steady kernel comparison is `final-kernel/`. Large artifacts are ignored by Git. No files were staged, committed, or pushed.

## Primary timing status

After all builds, tests, profiling, capacity trials, and wrapper replays stopped, the primary assessment driver validated the six hashes again and attempted its full 15-minute quiet-window wait. It sampled from `2026-10-15T17:44:45Z` through `17:59:43Z` (177 samples). No 60-second quiet window qualified, and **no primary timed arms were launched**. Desktop applications, security software, and indexing activity exceeded the CPU threshold. Power stayed on AC and the recorded swapout counter did not grow. No unrelated processes were terminated.

Evidence: `primary-timing/{manifest.txt,quiet.log,quiet-summary.txt,quiet-status.txt}` and `primary-timing.log`. The status is `BLOCKED: no 60-second quiet window within 15 minutes`. The final monitor source/classes are additionally frozen in `final/primary-harness-source-and-classes.tar.gz`.

**Repeatable speed assessment remains pending.** The single-pair timing numbers above are diagnostic observations. Retain the demonstrated memory/capacity improvements under the user's explicit tradeoff acceptance, without claiming that the original timing gates passed. Run the documented primary assessment command in a new output directory on a quiet machine before making a formal speed claim.

## Minor review follow-up: compact factory row widths

The review reproduced a new-factory issue: `PsmDataset.fromTabRows` accepted short or long rows, allowing `rawValueAt` to wrap to the first cell or disagree with `rows()`. The factory now copies the caller's row list, counts fields without token allocation, and rejects every row whose width differs from the headers before typed parsing. Empty trailing fields count as fields; ragged protein rows still require normalization before calling this factory. The legacy matrix constructor and scoring code are unchanged.

`DataModelsTest.compactRowsRejectMissingAndExtraFields` covers the reported missing-protein row, an extra populated field, an extra empty field, and a valid empty protein field. Focused `DataModelsTest`, `PinFileParserDifferentialTest`, and `ExactPerformanceParityTest` runs passed all 13 tests, including the unchanged original-baseline golden hash. `mvn -am test` passed **153 tests, zero failures/errors, two existing skips**; `mvn -am -Dmaven.test.skip=true compile` also passed. No retained-PIN performance benchmarks were rerun for this API validation fix. The benchmark measurements and frozen `final/javapot.jar` above remain the pre-review artifact, rather than measurements of the additional validation scan.

`mvn -am -DskipTests jar:jar` rebuilt the current JAR. The separate review artifact and check logs are retained in `review-row-width/`; its JAR SHA-256 is `0ad54fb59f751eee244f8f797bd5430fd36d5309c1780547dd203dd9b22ddfba`. The frozen benchmark JAR was not overwritten.
