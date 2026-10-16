package com.msme.cashflowguard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Body of POST /api/business-details. Every field is optional. */
public record BusinessDetailsRequest(
        Long statementId,

        @DecimalMin(value = "0", message = "Monthly rent must be 0 or more.")
        @DecimalMax(value = "10000000000", message = "Monthly rent is too large.")
        BigDecimal monthlyRent,

        @DecimalMin(value = "0", message = "Other fixed expenses must be 0 or more.")
        @DecimalMax(value = "10000000000", message = "Other fixed expenses are too large.")
        BigDecimal otherFixedExpenses,

        @DecimalMin(value = "0", message = "Cash in hand must be 0 or more.")
        @DecimalMax(value = "10000000000", message = "Cash in hand is too large.")
        BigDecimal cashInHand,

        @Valid
        @Size(max = 10, message = "You can add up to 10 upcoming payments.")
        List<UpcomingPaymentRequest> upcomingPayments) {
}
