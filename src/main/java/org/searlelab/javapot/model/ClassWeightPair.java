package org.searlelab.javapot.model;

/**
 * ClassWeightPair stores the negative and positive class weights applied during SVM training.
 * Instances are produced by grid search and embedded in fitted models.
 */
public record ClassWeightPair(double negative, double positive) {
}
