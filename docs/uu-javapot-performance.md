# JavaPot speed and memory improvements

**Canonical plan document:** `docs/uu-javapot-performance.md`  
Write this plan verbatim to that file when implementation begins, before changing implementation files. It has not been written during Plan Mode.

## Intent

Improve JavaPot’s runtime and memory use while preserving search results exactly. Develop one optimization area at a time, with executable correctness checks and lightweight benchmarks after every cycle.

Agreed acceptance gates:

- At least **10% total API speedup**, with no more than **10% memory growth**; or
- At least **20% memory reduction**, with no more than **10% slowdown**.
- Require exact scientific parity in either case. Report smaller gains without treating them as satisfying these gates.
- Document validated data bugs separately with a reproducer and proposed correction. Do not include result-changing fixes in this performance work.

Use retained PIN inputs and the ScribeTwo wrapper for final validation. Whole raw-data searches and the deferred Stellar GPF capacity experiment are excluded.

## Current understanding

The handoff provides useful prioritization, but its timings and heap figures describe the broader embedding phase. They do not establish standalone JavaPot runtime, retained memory, or minimum heap requirements.

Source inspection confirms several opportunities:

- `QValues.tdc` copies inputs, sorts twice, and allocates multiple full-length intermediates. Its minimum-FDR and tie behavior must remain the reference semantics. See [QValues.java](/Users/searle.brian/Documents/java/javapot/src/main/java/org/searlelab/javapot/stats/QValues.java), `tdc`.
- `PinFileParser.read` can tokenize a rectangular PIN twice; `PsmDataset` retains raw strings alongside parsed numeric features. Public raw-cell and row access prevents simply discarding numeric text. See [PinFileParser.java](/Users/searle.brian/Documents/java/javapot/src/main/java/org/searlelab/javapot/io/PinFileParser.java), `read`, `isTraditionalPin`, and `parse`; and [PsmDataset.java](/Users/searle.brian/Documents/java/javapot/src/main/java/org/searlelab/javapot/data/PsmDataset.java), `parseFeatures`, `rawValueAt`, `rows`, and `withColumnGroups`.
- Fold training, inner cross-validation, and scoring copy matrices repeatedly. `PercolatorTrainer.takeRows` additionally allocates rows that it immediately replaces. Evidence: `PercolatorTrainer.trainFold`, `takeRows`, `filterLabeled`; `ClassWeightGridSearch.buildCvSplits`; `JavaPotRunner.predictScores`; and `PercolatorFoldModel.predict`.
- `JavaPotRunner.assignConfidenceAndBuildOutputs` builds all four TSV row collections, including outputs that may not be requested. This is another candidate allocation cost, subject to measurement.

The baseline is now committed **HEAD `8a10110`**, including your subset fixes. The working tree was clean when checked. Preserve those fixes and keep the embedding training cap at **500,000**; other caps provide additional same-cap parity coverage.

The wrapper passes FDR through `float`, then promotes it to `double`. Replays must use those actual values rather than assume the literal `double` value `0.01`. Evidence: `JavaPotExecutor.executePercolatorTSV` in EncyclopeDIA.

## Desired direction and scope

Prioritize confidence allocation/sorting, parsing, and matrix ownership before SVM arithmetic changes. Include compatible compact raw-text storage if profiling shows retained cells remain a major memory cost.

Preserve:

- Input interpretation, feature inclusion/order, raw-cell text, row order, grouping, and public dataset behavior.
- Fold assignments, RNG consumption, sampled training rows, normalization order, selected features/hyperparameters, and trained models.
- Scores, calibration, q-values, PEPs, representative selection, deterministic output order, and model/output formats.
- Existing API signatures, defensive-copy contracts, Java 11 compatibility, dependencies, and tooling.

Exclude alternative solvers, precision changes, reordered floating-point reductions, altered training settings, and broader architecture changes. Changing `subset-max-train` is not an optimization for acceptance purposes.

## Implementation approach

### 1. Freeze a reproducible baseline and executable benchmark harness

- Save the canonical plan, then capture commit, source diff, JAR/dependency hashes, JDK, JVM flags, hardware, options, and input identities.
- Validate all six input hashes against the handoff before use. Treat retained inputs and reference outputs as read-only.
- Add an opt-in standalone replay harness under test sources using `JavaPotApi.run`. Launch each arm in a separate JVM against immutable baseline/candidate artifacts; do not install candidate JARs over the embedding dependency.
- Use the explicit embedding configuration: promoted float FDR thresholds, cap 500,000, seed 1, three folds/workers, ten iterations, one retry, TDC, ordinary cross-validation, and no initial weights.
- Record total API time plus optional stage diagnostics for parsing, initial feature selection, training, scoring/calibration/fallback, confidence, output construction, and writing. Keep diagnostics disabled normally and verify enabling them preserves outputs.
- Save results, manifests, commands, logs, hashes, and comparison reports in a new durable benchmark directory. Keep large artifacts out of Git.

Produce a small-PIN and fixed-Stellar baseline before choosing representation changes. Confirm both exercise actual training rather than silently benchmarking fallback.

### 2. Reduce confidence and sorting overhead

- Freeze the existing TDC implementation as a test-only differential oracle.
- First remove unnecessary input copies and redundant intermediates while preserving read-only input behavior, both existing sort orders, arithmetic order, and final index mapping.
- Add caller-owned sort scratch support where measured reuse helps. Workspaces must belong to an invocation or fold, have bounded lifetimes, and never be shared across simultaneous runs.
- Consider skipping already ordered merge segments only with stable-order differential coverage.
- Do not replace the second sort or the q-value algorithm in this scope. Any proposed algorithmic replacement requires a revised plan supported by an equivalence argument.

Benchmark the confidence kernel and complete training; kernel improvements alone do not qualify.

### 3. Reduce parser allocation and retained text

- Consolidate format detection and parsing into one tokenization pass. Preserve traditional protein normalization, skipped lines, trailing empty fields, malformed-row rejection, and global feature dropping.
- Precompute feature-column indices rather than resolve names for every numeric cell.
- If post-collection measurements still identify raw-cell objects as a major cost, retain parser-owned normalized row text rather than one retained string per cell. Extract raw cells without regenerating text from numeric values.
- Preserve the existing public constructor’s behavior, exact raw-cell access, independent `rows()` copies, and `withColumnGroups` semantics. Avoid an unbounded lazy cache that recreates the original retained footprint.

Keep the simpler parser changes independently measurable. Retain compact storage only if it passes the balanced gate.

### 4. Reduce matrix and output duplication

- Remove allocate-then-replace waste first.
- Replace private matrix copies with ordered row-reference arrays only where consumers are proven read-only. Keep normalized data fold-owned and preserve scaler accumulation order.
- Reuse scoring buffers or process held-out rows in bounded blocks, retaining identical per-row normalization and dot-product operation order.
- Avoid materializing unrequested TSV tables while preserving complete API PSM/peptide results and every requested output.
- Audit inner cross-validation copies under the same ownership rules. Do not change training membership or order.

### 5. Assess remaining SVM cost and integration

- Reprofile after the preceding areas. Limit SVM work to measured allocation reuse or invariant bookkeeping that preserves the exact arithmetic sequence.
- Defer solver, convergence, precision, and numerical-order changes.
- Validate the final package through the same `JavaPotExecutor.executePercolatorTSV` wrapper on retained small and fixed-Stellar PINs. Use separate JVM classpaths for baseline and candidate and fresh output locations.
- Compare wrapper outputs, returned peptide records, pi0 handling, and packaged model contents. No EncyclopeDIA production changes or raw searches are required.

If evidence requires a materially different approach, return with a proposed plan revision rather than silently expanding scope.

## Validation approach

### Correctness after each cycle

Extend existing parser, dataset, sort, confidence, scaler, fold, trainer, and integration tests.

Use exact comparisons of numeric bits and ordered records for parsed features, normalization, model parameters, scores, q-values, and PEPs. Compare deterministic output/model bytes as well. Output-directory paths may differ; scientific content may not.

Cover ties, all-equal scores, both directions, no/all decoys, pseudocount modes, signed zero, supported nonfinite values, missing features, ragged proteins, comments, group metadata, retries/fallback, loaded models, repeated calls, and simultaneous calls. Exercise grouped/mixmax paths where changed utilities are shared. Include unlimited, active, and oversized training caps against matching baseline settings.

Run focused Maven tests after relevant edits, `mvn clean test` after each completed improvement area, and compile before each handoff.

### Lightweight benchmark cadence

- **Every development cycle:** changed kernel plus paired complete Exploris and Astral-plasma replay.
- **Promising candidates:** Astral-wide scaling check.
- **Completed improvement area:** timsTOF, variable Stellar, and fixed Stellar.
- **Final package:** all six PINs and retained-PIN wrapper validation.

Provide documented commands for kernel, small, and assessment modes. Use existing JUnit/JDK facilities without new benchmark dependencies. Timing acceptance belongs in the opt-in runner, not ordinary unit-test assertions.

### Consistent measurement conditions

- Run benchmarks serially, with no concurrent builds, tests, searches, profiling jobs, or other agent workloads.
- Before timing, require 60 seconds of sampled quiet conditions: no sustained non-monitor process consuming over 10% of one core, no thermal pressure indication, and no active swap growth.
- Wait up to 15 minutes for a valid window. If unavailable, continue correctness and diagnostic memory work but leave performance acceptance pending. Do not terminate unrelated processes.
- Use the same JDK, collector, heap settings, workers, power state, logging, input path/basename, and output filesystem for both arms.
- Standardize on warm filesystem caches: read the input before each arm, outside timing. Use a fixed untimed JVM warmup sequence and fresh JVMs for measured API runs.
- Alternate A/B and B/A order. Use five measured pairs for small inputs and three for large assessment cases; retain individual measurements and report paired medians and spread.
- If baseline spread exceeds 5%, restore consistent conditions and repeat before assessing a near-threshold speed claim. Large differences may be reported as provisional evidence without weakening correctness gates.

Use unprofiled runs for primary timing. Use separate matched diagnostic runs for allocation, GC pauses, and post-collection heap; exclude forced collections and histograms from timing comparisons. Record peak RSS and post-collection heap separately. Predeclare the memory metric used for each candidate and report both, preventing metric cherry-picking. Select a completing heap during baseline setup, freeze it per case, and perform targeted lower-heap checks only after a memory improvement is established.

## Risks, decisions, and reviewer focus

- The historical embedding JAR is not established as identical to current HEAD. Current-HEAD baseline/candidate parity is authoritative; historical outputs remain secondary checks.
- Compact text trades retained objects for extraction work. Matrix views trade copying for stricter ownership. Both require direct evidence and concurrency tests.
- A 10% speed claim needs sufficiently low noise; lack of a thermal warning does not establish thermal consistency.
- Whole-search memory coexistence remains outside this effort. Wrapper replay validates integration but does not reproduce all upstream retained state.
- Keep the canonical plan unchanged during implementation; record measurements and cycle outcomes separately. Never stage, commit, or push.

**Checks performed during planning:** repository and wrapper inspection, input existence/size checks, process/thermal inspection, and `mvn -am -Dmaven.test.skip=true compile` — passed. No source files were changed. No correctness suite or performance benchmark was run; observed background activity was unsuitable for timing.
