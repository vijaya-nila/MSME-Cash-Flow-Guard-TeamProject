package com.msme.cashflowguard.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A detected recurring payment (Stage 7 result). */
public record RecurringResult(
        String description,
        TransactionCategory category,
        FlowDirection direction,
        Frequency frequency,
        BigDecimal averageAmount,
        LocalDate lastOccurrenceDate,
        LocalDate expectedDate,
        int occurrenceCount,
        double confidence) {
}
