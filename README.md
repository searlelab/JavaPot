# JavaPot

JavaPot is a Java clone of Dr. Will Fondrie's mokapot main code path for Percolator-style semisupervised peptide detection. It focuses on deterministic, high-throughput rescoring of tab-delimited PIN files while maintaining close algorithmic behavior to mokapot/Percolator. It also borrows certain features directly from Percolator, such as mixmax mode, PEP estimation with splines, and a more robust cross-validation approach.

## Capabilities
- Percolator-style semisupervised learning with a linear support vector machine classifier.
- Single-input-file workflow: one PIN file per run (no XML/PepXML input).
- Deterministic seeded behavior across fold splitting, training, and model selection.
- Optional multithreaded fold training via `--max_workers`.
- Percolator-style output headers by default, with optional mokapot-style output naming via `--output_format mokapot`.
- Model persistence with `--write_model_files` and `--load_models`.
- Confidence output tables written with `<pin_name>` prefixes (for example, `sample.peptides.tsv`).

## Requirements
- Java 17
- Maven 3.9+

## Build
```bash
mvn -am -Dmaven.test.skip=true compile
```

## Run
```bash
mvn -q -DincludeScope=runtime -Dmdep.outputFile=/tmp/javapot.cp dependency:build-classpath
java -cp "target/classes:$(cat /tmp/javapot.cp)" org.searlelab.javapot.cli.JavaPotCli \
  /path/to/input.pin \
  --dest_dir /path/to/output
```

## CLI options
```text
Usage: javapot [options] <pin_file>
Options:
  -h, --help            Show this help message and exit.
  -d DEST_DIR, --dest_dir DEST_DIR
                        The directory in which to write the result files. Defaults to the input PIN directory.
  -w MAX_WORKERS, --max_workers MAX_WORKERS
                        The number of processes to use for model training. Defaults to --folds when omitted. Note that using more than one worker will result in garbled logging messages.
  --output_format OUTPUT_FORMAT
                        Output TSV schema to write: percolator (default) or mokapot.
  --quiet               Suppress progress/status logging output.
  --train_fdr TRAIN_FDR
                        The maximum false discovery rate at which to consider a target PSM as a positive example during model training.
  --test_fdr TEST_FDR   The false-discovery rate threshold at which to evaluate the learned models.
  --max_iter MAX_ITER   The number of iterations to use for training.
  --seed SEED           An integer to use as the random seed.
  --direction DIRECTION
                        The name of the feature to use as the initial direction for ranking PSMs.
  --subset_max_train SUBSET_MAX_TRAIN
                        Maximum number of PSMs to use during the training of each of the cross validation folds in the model.
  --write_model_files   Save the models learned by javapot as Java serialized model objects.
  --write_psm_files     Write target PSM output files in addition to peptide files.
  --write_decoy_files   Write decoy peptide/PSM forensic output files.
  --mixmax, --post-processing-mix-max
                        Use Percolator mix-max post-processing for q-value and PEP assignment.
  --results-peptides PATH
                        Write target peptide output to PATH (relative to current working directory).
  --decoy-results-peptides PATH
                        Write decoy peptide output to PATH (relative to current working directory).
  --results-psms PATH
                        Write target PSM output to PATH (relative to current working directory).
  --decoy-results-psms PATH
                        Write decoy PSM output to PATH (relative to current working directory).
  --load_models LOAD_MODELS [LOAD_MODELS ...]
                        Load previously saved models and skip model training. Number of models must match --folds.
  --folds FOLDS         Number of cross-validation folds. Default: 3.
```

## Programmatic API
JavaPot can be run in-process without shelling out to CLI:

```java
import java.nio.file.Path;
import org.searlelab.javapot.cli.JavaPotOptions;
import org.searlelab.javapot.pipeline.JavaPotApi;
import org.searlelab.javapot.pipeline.JavaPotRunResult;

JavaPotOptions options = new JavaPotOptions(
    Path.of("/path/to/input.pin"),
    0.01,
    0.01,
    Path.of("targets.peptides.tsv"),
    Path.of("decoys.peptides.tsv"),
    true // mixmax
);

JavaPotRunResult result = JavaPotApi.run(options);
// result.peptides() / result.psms() are ArrayList<JavaPotPeptide>
// result.psmPi0() and result.peptidePi0() are populated in mixmax mode (null in TDC mode)
```

`JavaPotPeptide` exposes: `score`, `qValue`, `pep`, `isDecoy`, `psmId`, and `peptideSequence`.

## Relationship to mokapot and Percolator
JavaPot is intended to mirror the mokapot Percolator-like algorithmic path in Java. For reference implementations and related tooling, see:
- mokapot GitHub: [https://github.com/wfondrie/mokapot](https://github.com/wfondrie/mokapot)
- Percolator GitHub: [https://github.com/percolator/percolator](https://github.com/percolator/percolator)

## Citations
- mokapot: Fast and Flexible Semisupervised Learning for Peptide Detection.  
  Fondrie WE, Noble WS.  
  J Proteome Res. 2021 Apr 2;20(4):1966-1971. doi: 10.1021/acs.jproteome.0c01010. Epub 2021 Feb 17. PMID: 33596079.

- Semi-supervised learning for peptide identification from shotgun proteomics datasets.  
  Käll L, Canterbury JD, Weston J, Noble WS, MacCoss MJ.  
  Nat Methods. 2007 Nov;4(11):923-5. doi: 10.1038/nmeth1113. Epub 2007 Oct 21. PMID: 17952086.

### Contribution guidelines ###
Any contribution must follow the coding style of the project, be presented with tests and stand up to code review before it will be accepted.

### Who do I talk to? ###
This is a [Searle Lab](http://www.searlelab.org/) project from the Department of Quantitative Health Sciences at the Mayo Clinic. For more information please contact [Brian Searle](http://www.searlelab.org/people/brian_searle/index.html) (searle dot brian at mayo dot edu).
