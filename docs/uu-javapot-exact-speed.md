# JavaPot exact-result performance investigation

**Canonical plan document:** `docs/uu-javapot-exact-speed.md` — create when implementation begins.

**Intent**

Spend up to eight hours investigating exact-result optimizations, prioritizing speed over memory. Test training budgets of 3, 6, and 12 workers. Coordinate quiet measurement windows with other agents while tolerating ordinary concurrent work outside those windows.

Automatically retain validated optimizations when speed or memory improves and the other stays neutral or improves. Submit candidates with a measured regression in either metric for user review. Continue independent work while those decisions are pending.

**Current understanding**

- `ClassWeightGridSearch.select/evaluate` performs nine independent class-weight evaluations after deterministic split construction. Each evaluation contains three serial SVM fits.
- `JavaPotRunner.trainModels` starts at most three ordinary outer-fold workers; increasing `maxWorkers` alone does not parallelize the inner grid.
- `PercolatorTrainer.startingLabels/trainFold` does not supply the existing reusable TDC workspace.
- `JavaPotRunner.deduplicateByPeptide` sorts representatives already supplied in the same score-and-row order. Removing this sort is an additional hypothesis requiring differential verification.
- The measured 1.0.6 JAR, Astral plasma PIN, timsTOF PIN, and warmup PIN match their declared hashes.
- Offline test compilation, eight focused regression tests, and final Java compilation passed during planning. No implementation changes have been made.
- `/Users/searle.brian/Downloads/agent_handoff.txt` does not currently exist. Create it at implementation start and preserve subsequent contributions from other agents.

**Desired direction and scope**

- Prioritize Astral, then Exploris and timsTOF, then Stellar.
- Investigate bounded grid concurrency first, TDC workspace reuse independently, and additional measured hotspots within the remaining time.
- Preserve numerical arithmetic, convergence, reduction order, RNG consumption, folds, training membership, FDR promotion/fallback, embedding training cap, retry behavior, TDC/MIXMAX semantics, ownership, and complete ordered outputs.
- Keep EncyclopeDIA source/POM, installed Maven artifacts, dependencies, versions, and historical benchmark artifacts unchanged.
- Never stage, commit, push, or adopt the dependency into EncyclopeDIA.

**Implementation approach**

1. **Freeze the plan and comparators.** Save this plan verbatim before implementation edits. Create a new ignored study directory under `benchmark-results/`. Freeze the measured binary, clean source-built control, Trove, harnesses, wrapper runtime, commands, hashes, and JVM identity separately. Establish measured-binary/source-control parity first.

2. **Coordinate resource use through the shared file.** Read `~/Downloads/agent_handoff.txt` before resource-heavy work. Publish agent/study identity, intended work, expected CPU/heap demand, and proposed quiet blocks with explicit start/end times and timezone. Negotiate conflicts through the file; do not interpret silence as agreement from an agent declaring overlapping heavy work. Record confirmed reservations, start, release, cancellation, and overruns. Re-read immediately before each block. Use short bounded blocks, initially 15 minutes, and request extensions explicitly. Perform source inspection, analysis, and other light work while waiting.

3. **Measure the initial discriminator.** Within 90 minutes, separate grid-search time from iterative fitting on Astral plasma and timsTOF, and census repeated TDC allocation. Reuse `PipelineTiming`; add disabled diagnostic markers only where necessary. Report a checkpoint identifying measured costs, uncertainty, and the remaining priorities.

4. **Prototype bounded grid concurrency.** Preserve existing public entrypoints and default execution. Add an internal opt-in benchmark path. For budget `W`, reserve `F=min(W, foldCount)` outer workers and at most `W−F` shared helpers. Fold workers also evaluate pairs; helpers never submit nested work. With no helper capacity, retain serial execution. Construct splits before scheduling, share only read-only inputs, store outcomes by pair ordinal, and preserve strict original-order selection. Handle ordered failures, interruption, cancellation, and worker teardown before retry or return.

5. **Test independent small changes.** Reuse one fold-owned TDC workspace across initialization and iterations, preserving independently returned arrays and the MIXMAX path. Separately test direct traversal instead of the redundant peptide-selection sort. Investigate fold grouping, output construction, or direct normalization into shuffled storage only when measurements justify them. Preserve scaler reduction order and shuffled locality; do not repeat the rejected shared-row experiment.

6. **Keep pending decisions separate.** Freeze each review candidate’s source snapshot, patch, JAR, exactness results, and measurements. Ask for its decision asynchronously, stating absolute speed and memory differences with uncertainty. Continue from the accepted control on independent experiments. Never include a pending regression candidate in the accepted cumulative candidate. Revalidate combinations after any approval.

7. **Reserve the final two hours for validation and reporting.** Select at most one main mechanism; independently useful small changes may join after isolated and combined verification. Stop new experiments early enough to finish checks within eight hours.

**Validation approach**

- Preserve existing goldens and independent references. Compare grid-pair score bits, selected weights, scaler/model parameters, predictions, q-values/PEPs, ordered API results, TSVs, saved models, and loaded-model behavior.
- Cover ties, signed zero, supported nonfinite values, irregular inputs, both directions, TDC/MIXMAX, grouped crossfit, caps, retries/fallback, concurrent independent calls, worker limits, failures, interruption, and cleanup.
- Freeze fresh-JVM commands using `PerformanceReplay`, selected JAR first, unchanged warmups, and `-Xms1g -Xmx30g -XX:+UseG1GC`.
- Alternate B/C then C/B order. Screen with three pairs per input/budget; repeat the selected candidate with five Astral-plasma pairs and three timsTOF pairs. Publish every arm and identify gains requiring additional active workers.
- Serialize this study’s builds, tests, profiles, and timings. Run primary timing in negotiated quiet blocks and record interference. Retain disrupted runs as labeled evidence.
- Establish baseline repeatability before candidate interpretation. Declare neutrality bands from measurement precision before assessing candidates. An inconclusive difference is not evidence of neutrality.
- Assess speed using API elapsed time and memory using peak process footprint corroborated by RSS. Report allocation, collected occupancy, committed heap, CPU, and GC separately. Keep profiling and forced collection outside speed runs.
- Validate the final candidate across all six retained PINs and the frozen real wrapper on Astral, Exploris, and timsTOF. The unchanged wrapper validates legacy execution; concurrency adoption remains separate.
- Run focused tests, `mvn -o clean test`, final Java compilation, and diff review.

**Risks, decisions, and reviewer focus**

- **Accept without further review:** exactness passes, one primary metric demonstrably improves, and the other is demonstrably neutral or better.
- **Request review:** either primary metric demonstrably regresses. Present the isolated candidate and continue unrelated work.
- **Keep provisional:** noise prevents establishing improvement or neutrality. Seek another coordinated block; make no speed claim.
- Additional grid workers may increase scratch demand or saturate bandwidth. Sample percentages do not predict wall-time gains.
- Deliver accepted and pending candidates separately, with manifests, checks actually run, every measurement, rejected experiments, uncertainty, and remaining decisions. Dependency adoption and complete same-platform EncyclopeDIA/Linux validation require separate approval.
