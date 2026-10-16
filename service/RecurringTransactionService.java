package com.msme.cashflowguard.service;

import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.model.FlowDirection;
import com.msme.cashflowguard.model.Frequency;
import com.msme.cashflowguard.model.RecurringResult;
import com.msme.cashflowguard.model.TransactionCategory;
import com.msme.cashflowguard.util.PartyNameExtractor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Stage 7: finds salary, rent, EMI, utility, repeated supplier and repeated customer payments.
 * Evidence used: same party name, similar amounts, repeated dates, regular intervals.
 * A pattern with too little evidence is NOT marked recurring.
 */
@Service
public class RecurringTransactionService {

    static final double MIN_CONFIDENCE = 0.55;
    private static final double MIN_REGULAR_SHARE = 0.75;

    private static final Set<TransactionCategory> ELIGIBLE = EnumSet.of(
            TransactionCategory.SALARY, TransactionCategory.RENT, TransactionCategory.EMI,
            TransactionCategory.SUPPLIER_PAYMENT, TransactionCategory.CUSTOMER_PAYMENT, TransactionCategory.UTILITY);

    private static final Set<TransactionCategory> FIXED_AMOUNT = EnumSet.of(
            TransactionCategory.SALARY, TransactionCategory.RENT, TransactionCategory.EMI);

    /** Largest allowed spread of amounts (std-dev / mean) per category. */
    private static final Map<TransactionCategory, Double> MAX_AMOUNT_SPREAD = new EnumMap<>(TransactionCategory.class);

    static {
        MAX_AMOUNT_SPREAD.put(TransactionCategory.SALARY, 0.25);
        MAX_AMOUNT_SPREAD.put(TransactionCategory.RENT, 0.15);
        MAX_AMOUNT_SPREAD.put(TransactionCategory.EMI, 0.15);
        MAX_AMOUNT_SPREAD.put(TransactionCategory.UTILITY, 0.60);
        MAX_AMOUNT_SPREAD.put(TransactionCategory.CUSTOMER_PAYMENT, 0.60);
        MAX_AMOUNT_SPREAD.put(TransactionCategory.SUPPLIER_PAYMENT, 0.50);
    }

    /**
     * Detects recurring patterns in classified, chronologically ordered transactions.
     * Every transaction that belongs to a pattern gets {@code recurring = true}.
     */
    public List<RecurringResult> detect(List<Transaction> transactions) {
        Map<String, List<Transaction>> groups = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            if (t.getCategory() == null || !ELIGIBLE.contains(t.getCategory())) {
                continue;
            }
            String key = (t.isDebit() ? "OUT" : "IN") + "|"
                    + PartyNameExtractor.key(PartyNameExtractor.extract(t.getDescription()));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<RecurringResult> results = new ArrayList<>();
        for (List<Transaction> group : groups.values()) {
            RecurringResult r = evaluate(group);
            if (r != null) {
                results.add(r);
                for (Transaction t : group) {
                    t.setRecurring(true);
                }
            }
        }
        results.sort(Comparator.comparing(RecurringResult::expectedDate));
        return results;
    }

    private RecurringResult evaluate(List<Transaction> group) {
        TreeMap<LocalDate, BigDecimal> byDate = new TreeMap<>();
        Map<TransactionCategory, Integer> categoryVotes = new EnumMap<>(TransactionCategory.class);
        Transaction latest = group.get(0);
        for (Transaction t : group) {
            BigDecimal amount = t.isDebit() ? t.getDebit() : t.getCredit();
            byDate.merge(t.getTxnDate(), amount, BigDecimal::add);
            categoryVotes.merge(t.getCategory(), 1, Integer::sum);
            if (!t.getTxnDate().isBefore(latest.getTxnDate())) {
                latest = t;
            }
        }
        int n = byDate.size();
        if (n < 2) {
            return null;
        }

        List<LocalDate> dates = new ArrayList<>(byDate.keySet());
        List<BigDecimal> amounts = new ArrayList<>(byDate.values());
        long[] gaps = new long[n - 1];
        for (int i = 1; i < n; i++) {
            gaps[i - 1] = ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
        }

        Optional<Frequency> maybeFrequency = Frequency.fromGap(median(gaps));
        if (maybeFrequency.isEmpty()) {
            return null;
        }
        Frequency frequency = maybeFrequency.get();
        int regular = 0;
        for (long g : gaps) {
            if (Math.abs(g - frequency.getNominalDays()) <= frequency.getToleranceDays()) {
                regular++;
            }
        }
        double regularShare = (double) regular / gaps.length;
        if (regularShare < MIN_REGULAR_SHARE) {
            return null;
        }

        TransactionCategory category = majority(categoryVotes);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal a : amounts) {
            sum = sum.add(a);
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        double spread = spread(amounts, mean);
        double limit = MAX_AMOUNT_SPREAD.getOrDefault(category, 0.5);

        if (n < 3) {
            // Two occurrences are only enough for a fixed monthly payment with a near-identical amount.
            boolean ok = FIXED_AMOUNT.contains(category) && frequency == Frequency.MONTHLY && spread <= 0.10;
            if (!ok) {
                return null;
            }
        } else if (spread > limit) {
            return null;
        }

        double confidence = 0.45
                + 0.08 * Math.min(n - 2, 4)
                + 0.15 * regularShare
                + 0.10 * (1.0 - Math.min(spread / limit, 1.0))
                + (FIXED_AMOUNT.contains(category) ? 0.05 : 0.0)
                - (n == 2 ? 0.10 : 0.0);
        confidence = Math.max(0.0, Math.min(0.95, confidence));
        confidence = Math.round(confidence * 100.0) / 100.0;
        if (confidence < MIN_CONFIDENCE) {
            return null;
        }

        LocalDate last = dates.get(n - 1);
        FlowDirection direction = group.get(0).isDebit() ? FlowDirection.OUTFLOW : FlowDirection.INFLOW;
        return new RecurringResult(
                PartyNameExtractor.extract(latest.getDescription()),
                category,
                direction,
                frequency,
                mean,
                last,
                frequency.plus(last, 1),
                n,
                confidence);
    }

    private static double median(long[] values) {
        long[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    /** Standard deviation divided by the mean (0 = identical amounts). */
    private static double spread(List<BigDecimal> amounts, BigDecimal mean) {
        if (mean.signum() <= 0) {
            return Double.MAX_VALUE;
        }
        double m = mean.doubleValue();
        double sq = 0;
        for (BigDecimal a : amounts) {
            double diff = a.doubleValue() - m;
            sq += diff * diff;
        }
        return Math.sqrt(sq / amounts.size()) / m;
    }

    private static TransactionCategory majority(Map<TransactionCategory, Integer> votes) {
        TransactionCategory best = TransactionCategory.UNKNOWN;
        int bestVotes = -1;
        for (Map.Entry<TransactionCategory, Integer> e : votes.entrySet()) {
            if (e.getValue() > bestVotes) {
                best = e.getKey();
                bestVotes = e.getValue();
            }
        }
        return best;
    }
}
