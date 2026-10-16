package com.msme.cashflowguard.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** How often a recurring transaction repeats. */
public enum Frequency {
    WEEKLY("Weekly", 7, 2),
    FORTNIGHTLY("Fortnightly", 14, 3),
    MONTHLY("Monthly", 30, 5),
    QUARTERLY("Quarterly", 91, 10);

    private static final int SAFETY_LIMIT = 5000;

    private final String label;
    private final int nominalDays;
    private final int toleranceDays;

    Frequency(String label, int nominalDays, int toleranceDays) {
        this.label = label;
        this.nominalDays = nominalDays;
        this.toleranceDays = toleranceDays;
    }

    public String getLabel() {
        return label;
    }

    public int getNominalDays() {
        return nominalDays;
    }

    public int getToleranceDays() {
        return toleranceDays;
    }

    /** The k-th occurrence after {@code anchor}. Months keep the original day-of-month where possible. */
    public LocalDate plus(LocalDate anchor, int k) {
        switch (this) {
            case WEEKLY:
                return anchor.plusWeeks(k);
            case FORTNIGHTLY:
                return anchor.plusWeeks(2L * k);
            case MONTHLY:
                return anchor.plusMonths(k);
            default:
                return anchor.plusMonths(3L * k);
        }
    }

    /** All occurrences (after lastOccurrence) that fall inside [from, to]. */
    public List<LocalDate> occurrences(LocalDate lastOccurrence, LocalDate from, LocalDate to) {
        List<LocalDate> result = new ArrayList<>();
        int k = 1;
        LocalDate d = plus(lastOccurrence, k);
        while (d.isBefore(from) && k < SAFETY_LIMIT) {
            k++;
            d = plus(lastOccurrence, k);
        }
        while (!d.isAfter(to) && k < SAFETY_LIMIT) {
            result.add(d);
            k++;
            d = plus(lastOccurrence, k);
        }
        return result;
    }

    /** First occurrence on or after {@code from}. */
    public LocalDate nextOnOrAfter(LocalDate lastOccurrence, LocalDate from) {
        int k = 1;
        LocalDate d = plus(lastOccurrence, k);
        while (d.isBefore(from) && k < SAFETY_LIMIT) {
            k++;
            d = plus(lastOccurrence, k);
        }
        return d;
    }

    /** Frequency whose nominal gap is close enough to the (median) gap in days. */
    public static Optional<Frequency> fromGap(double gapDays) {
        for (Frequency f : values()) {
            if (Math.abs(gapDays - f.nominalDays) <= f.toleranceDays) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    /** Approximate number of times this payment happens in a month. */
    public double timesPerMonth() {
        switch (this) {
            case WEEKLY:
                return 52.0 / 12.0;
            case FORTNIGHTLY:
                return 26.0 / 12.0;
            case MONTHLY:
                return 1.0;
            default:
                return 1.0 / 3.0;
        }
    }
}
