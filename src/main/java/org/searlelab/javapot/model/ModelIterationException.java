package org.searlelab.javapot.model;

/**
 * ModelIterationException signals unrecoverable failure during iterative fold training.
 * It isolates model-loop issues from parsing and I/O failures.
 */
public final class ModelIterationException extends RuntimeException {
	public ModelIterationException(String message) {
		super(message);
	}
}
