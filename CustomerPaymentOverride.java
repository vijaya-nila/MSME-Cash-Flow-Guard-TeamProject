package com.msme.cashflowguard.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Used by the what-if simulator: "this customer pays this amount on this date". */
public record CustomerPaymentOverride(String customerName, BigDecimal amount, LocalDate paymentDate) {
}
