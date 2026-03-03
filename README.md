# JavaPot

JavaPot is a Java clone of the mokapot main code path for Percolator-style semisupervised peptide detection. It focuses on deterministic, high-throughput rescoring of tab-delimited PIN files while maintaining close algorithmic behavior to mokapot/Percolator.

## Capabilities
- Percolator-style semisupervised learning with a linear support vector machine classifier.
- Single-input-file workflow: one PIN file per run (no XML/PepXML input).
- Deterministic seeded behavior across fold splitting, training, and model selection.
- Optional multithreaded fold training via `--max_workers`.
- Model persistence with `--save_models` and `--load_models`.
- Confidence output tables written as `targets.psms.tsv` and `targets.peptides.tsv`.

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
  -d DEST_DIR, --dest_dir DEST_DIR
                        The directory in which to write the result files. Defaults to the current working directory
  -w MAX_WORKERS, --max_workers MAX_WORKERS
                        The number of processes to use for model training. Defaults to --folds when omitted. Note that using more than one worker will result in garbled logging messages.
  --train_fdr TRAIN_FDR
                        The maximum false discovery rate at which to consider a target PSM as a positive example during model training.
  --test_fdr TEST_FDR   The false-discovery rate threshold at which to evaluate the learned models.
  --max_iter MAX_ITER   The number of iterations to use for training.
  --seed SEED           An integer to use as the random seed.
  --direction DIRECTION
                        The name of the feature to use as the initial direction for ranking PSMs.
  --subset_max_train SUBSET_MAX_TRAIN
                        Maximum number of PSMs to use during the training of each of the cross validation folds in the model.
  --save_models         Save the models learned by javapot as Java serialized model objects.
  --load_models LOAD_MODELS [LOAD_MODELS ...]
                        Load previously saved models and skip model training. Number of models must match --folds.
  --folds FOLDS         Number of cross-validation folds. Default: 3.
```

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
