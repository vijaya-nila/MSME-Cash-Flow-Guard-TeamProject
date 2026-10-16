package com.msme.cashflowguard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BusinessDetailsResponse(
        BigDecimal monthlyRent,
        BigDecimal otherFixedExpenses,
        BigDecimal cashInHand,
        List<UpcomingPaymentRequest> upcomingPayments,
        LocalDateTime updatedAt,
        String message) {
}
