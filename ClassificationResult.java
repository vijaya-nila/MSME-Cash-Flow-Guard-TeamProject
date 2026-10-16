package com.msme.cashflowguard.model;

/** Category + how sure we are + a plain-English reason. */
public record ClassificationResult(TransactionCategory category, double confidence, String reason) {
}
