# JavaPot MixMax Parity TODO

## Backlog
- [ ] None

## In Progress
- [ ] None (awaiting issue-by-issue review/decision)

## Done
- [x] Create `docs/TODO.md` with checklist sections
- [x] Baseline and TODO bootstrap
- [x] CLI/config wiring for `--mixmax` and `--post-processing-mix-max`
- [x] Q-value core parity (`MixMaxQValues`, `Pi0Estimator`, training `skipDecoysPlusOne` support)
- [x] PEP core parity (I-spline + PAVA with deterministic fallback)
- [x] Training-path integration (confidence mode in iterative relabeling)
- [x] Final output-path integration (mix-max no spectrum competition; PEP estimator output)
- [x] Parity validation and bug reporting
- [x] Final compile verification
- [x] Add 10k-row Romero benchmark fixture from `CE1_2026Jan14_P26-008_quant_report_concatenated_features.txt` (`src/test/resources/data/minmax_10k.pin`)
- [x] Add benchmark integration test: mix-max reports more peptides than TDC at same FDR on separate-search-style input
- [x] Add mix-max confidence-shape assertion: reported `q-value <= PEP` for output rows
- [x] Prevent training/calibration hard-fail on decoy-sparse folds (fallback label init + finite calibration behavior)
- [x] Guard score calibration against zero/invalid denominator (`NaN`/`Inf`)
- [x] Validate and enforce fold-index ordering for `--load_models`
- [x] Add regression tests for fold sparsity, calibration stability, and model-load ordering
- [x] Fix fold assignment robustness with File+Scan grouping, Percolator-style randomized fold assignment, and deterministic label-empty repair when feasible
- [x] Keep existing JavaPot TDC spectrum competition key (`spectrumColumns`) as intentional divergence from Percolator key semantics
- [x] Enable default TDC training `skipDecoysPlusOne=true` in best-feature initialization and iterative relabeling
- [x] Add regression test for TDC training default `skipDecoysPlusOne` behavior (`PercolatorTrainerTest`)
- [x] Implement TDC confidence parity path (`QValues.tdc(...)` + `PepEstimator.tdcQvalsToPep(...)`) so reported `q-value <= PEP`

## Parity Findings
- [x] Enabling default TDC training `skipDecoysPlusOne` shifts identification counts vs prior JavaPot baseline; parity/performance regression windows were updated to preserve stable guardrails while allowing Percolator-aligned behavior.
- [x] Accepted divergence: TDC spectrum competition key remains JavaPot `spectrumColumns`-based (Percolator key migration intentionally not applied in this batch).
- [x] Resolved mismatch: TDC confidence output now satisfies `q-value <= PEP` on Romero 10k benchmark via `JavaPotRunner.estimateConfidence(...)` using `QValues.tdc(...)` + `PepEstimator.tdcQvalsToPep(...)`.

## Suite Log
- [x] Baseline: `mvn -am test` -> PASS (56 tests, 0 failures, 0 errors, 2 skipped)
- [x] CLI/config group: `mvn -am test` -> PASS (57 tests, 0 failures, 0 errors, 2 skipped)
- [x] Q-value core group: `mvn -am test` -> PASS (65 tests, 0 failures, 0 errors, 2 skipped)
- [x] PEP core group: `mvn -am test` -> PASS (69 tests, 0 failures, 0 errors, 2 skipped)
- [x] Training-path group: `mvn -am test` -> PASS (70 tests, 0 failures, 0 errors, 2 skipped)
- [x] Final output-path group: `mvn -am test` -> PASS (71 tests, 0 failures, 0 errors, 2 skipped)
- [x] Parity validation group: `mvn -am test` -> PASS (71 tests, 0 failures, 0 errors, 2 skipped)
- [x] Final compile: `mvn -am -Dmaven.test.skip=true compile` -> PASS
- [x] Romero benchmark focused test: `mvn -am -Dtest=MixMaxBenchmarkIntegrationTest test` -> PASS (1 test, 0 failures, 0 errors)
- [x] Romero benchmark group full suite: `mvn -am test` -> PASS (79 tests, 0 failures, 0 errors, 2 skipped)
- [x] Fold robustness group: `mvn -am test` -> PASS (81 tests, 0 failures, 0 errors, 2 skipped)
- [x] TDC training skipDecoysPlusOne focused tests: `mvn -am -Dtest=PercolatorTrainerTest,QValuesAndLabelsTest test` -> PASS (9 tests, 0 failures, 0 errors)
- [x] TDC training skipDecoysPlusOne group: `mvn -am test` -> PASS (82 tests, 0 failures, 0 errors, 2 skipped)
- [x] TDC confidence parity focused test: `mvn -am -Dtest=MixMaxBenchmarkIntegrationTest test` -> PASS (1 test, 0 failures, 0 errors)
- [x] TDC confidence parity group: `mvn -am test` -> PASS (84 tests, 0 failures, 0 errors, 2 skipped)
- [x] Final compile (post-parity cleanup): `mvn -am -Dmaven.test.skip=true compile` -> PASS
