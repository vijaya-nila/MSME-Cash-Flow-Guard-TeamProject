package com.msme.cashflowguard.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A row that passed validation. Exactly one of debit / credit is above zero. */
public record ValidatedRow(int rowNumber, LocalDate date, String description,
                           BigDecimal debit, BigDecimal credit, BigDecimal balance) {
}
