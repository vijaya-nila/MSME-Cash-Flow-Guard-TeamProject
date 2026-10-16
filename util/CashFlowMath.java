package com.msme.cashflowguard.util;

import com.msme.cashflowguard.entity.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Shared cash-flow arithmetic so the dashboard, forecast and stress score
 * all use exactly the same definitions.
 *
 * Definitions:
 *  - history days        = days from the first to the last transaction date (inclusive)
 *  - daily closing       = balance after the last transaction of each calendar day (carried forward)
 *  - average daily spend = total outflows / history days
 *  - safety buffer       = 7 days of average daily spend
 *  - low-balance day     = a day whose closing balance is below the safety buffer
 */
public final class CashFlowMath {

    /** How many days of normal spending count as a safe minimum cushion. */
    public static final int SAFETY_DAYS_OF_COVER = 7;

    private CashFlowMath() {
    }

    public static long historyDays(List<Transaction> txns) {
        if (txns.isEmpty()) {
            return 1;
        }
        LocalDate first = txns.get(0).getTxnDate();
        LocalDate last = txns.get(txns.size() - 1).getTxnDate();
        return ChronoUnit.DAYS.between(first, last) + 1;
    }

    /** Date the forecast starts: the day after the later of "today" and the last transaction. */
    public static LocalDate forecastStart(LocalDate lastTransactionDate, LocalDate today) {
        LocalDate base = today.isAfter(lastTransactionDate) ? today : lastTransactionDate;
        return base.plusDays(1);
    }

    public static BigDecimal totalInflows(List<Transaction> txns) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Transaction t : txns) {
            sum = sum.add(MoneyUtil.nz(t.getCredit()));
        }
        return MoneyUtil.scale2(sum);
    }

    public static BigDecimal totalOutflows(List<Transaction> txns) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Transaction t : txns) {
            sum = sum.add(MoneyUtil.nz(t.getDebit()));
        }
        return MoneyUtil.scale2(sum);
    }

    public static BigDecimal averageDailyOutflow(List<Transaction> txns) {
        if (txns.isEmpty()) {
            return MoneyUtil.scale2(BigDecimal.ZERO);
        }
        return totalOutflows(txns).divide(BigDecimal.valueOf(historyDays(txns)), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal safetyBuffer(BigDecimal averageDailyOutflow) {
        return MoneyUtil.scale2(averageDailyOutflow.multiply(BigDecimal.valueOf(SAFETY_DAYS_OF_COVER)));
    }

    /** Closing balance for every calendar day between the first and last transaction. */
    public static NavigableMap<LocalDate, BigDecimal> dailyClosingBalances(List<Transaction> txns) {
        TreeMap<LocalDate, BigDecimal> result = new TreeMap<>();
        if (txns.isEmpty()) {
            return result;
        }
        TreeMap<LocalDate, BigDecimal> lastOfDay = new TreeMap<>();
        for (Transaction t : txns) {
            lastOfDay.put(t.getTxnDate(), t.getBalance());   // chronological order: the last one wins
        }
        LocalDate day = txns.get(0).getTxnDate();
        LocalDate end = txns.get(txns.size() - 1).getTxnDate();
        BigDecimal carry = txns.get(0).getBalance();
        while (!day.isAfter(end)) {
            BigDecimal today = lastOfDay.get(day);
            if (today != null) {
                carry = today;
            }
            result.put(day, carry);
            day = day.plusDays(1);
        }
        return result;
    }

    public static int lowBalanceDays(NavigableMap<LocalDate, BigDecimal> dailyBalances, BigDecimal buffer) {
        int count = 0;
        for (BigDecimal b : dailyBalances.values()) {
            if (b.compareTo(buffer) < 0) {
                count++;
            }
        }
        return count;
    }
}
