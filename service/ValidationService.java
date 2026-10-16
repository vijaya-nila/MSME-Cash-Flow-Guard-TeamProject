package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.DataQualityReport;
import com.msme.cashflowguard.exception.CsvValidationException;
import com.msme.cashflowguard.model.ParsedCsv;
import com.msme.cashflowguard.model.RawRow;
import com.msme.cashflowguard.model.ValidatedRow;
import com.msme.cashflowguard.model.ValidationOutcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Stage 4: checks every row and builds the data-quality report.
 * Checks: required columns, invalid/missing dates, empty descriptions, invalid debit /
 * credit / balance, duplicates, debit+credit both filled, balance inconsistencies,
 * minimum recommended history. It never claims to detect fraud.
 */
@Service
public class ValidationService {

    public static final List<String> REQUIRED_COLUMNS = List.of("date", "description", "debit", "credit", "balance");
    public static final int RECOMMENDED_HISTORY_DAYS = 90;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final int MAX_DETAIL_WARNINGS = 5;

    private static final List<DateTimeFormatter> DATE_FORMATS = buildDateFormats();

    private static List<DateTimeFormatter> buildDateFormats() {
        String[] patterns = {
                "uuuu-MM-dd", "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d-MMM-uuuu", "d MMM uuuu",
                "d MMMM uuuu", "d-MMMM-uuuu", "d/M/uu", "d-M-uu", "uuuu/M/d"
        };
        List<DateTimeFormatter> list = new ArrayList<>();
        for (String p : patterns) {
            list.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p)
                    .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
        }
        return list;
    }

    public ValidationOutcome validate(ParsedCsv csv) {
        List<String> missing = new ArrayList<>();
        for (String col : REQUIRED_COLUMNS) {
            if (!csv.columnIndex().containsKey(col)) {
                missing.add(col);
            }
        }
        if (!missing.isEmpty()) {
            throw new CsvValidationException("Missing required column(s): " + String.join(", ", missing)
                    + ". The first line must be: date, description, debit, credit, balance.");
        }
        if (csv.rows().isEmpty()) {
            throw new CsvValidationException("The file has a header but no transactions.");
        }

        int cDate = csv.columnIndex().get("date");
        int cDesc = csv.columnIndex().get("description");
        int cDebit = csv.columnIndex().get("debit");
        int cCredit = csv.columnIndex().get("credit");
        int cBalance = csv.columnIndex().get("balance");

        List<ValidatedRow> valid = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;

        for (RawRow row : csv.rows()) {
            String problem = null;
            LocalDate date = null;
            BigDecimal debit = null;
            BigDecimal credit = null;
            BigDecimal balance = null;

            String rawDate = row.get(cDate).trim();
            if (rawDate.isEmpty()) {
                problem = "date is missing";
            } else {
                date = parseDate(rawDate);
                if (date == null) {
                    problem = "invalid date '" + shorten(rawDate) + "'";
                }
            }

            String description = row.get(cDesc).trim().replaceAll("\\s+", " ");
            if (problem == null && description.isEmpty()) {
                problem = "description is empty";
            }

            if (problem == null) {
                try {
                    debit = parseAmount(row.get(cDebit), false);
                } catch (NumberFormatException e) {
                    problem = "invalid debit value '" + shorten(row.get(cDebit)) + "'";
                }
            }
            if (problem == null) {
                try {
                    credit = parseAmount(row.get(cCredit), false);
                } catch (NumberFormatException e) {
                    problem = "invalid credit value '" + shorten(row.get(cCredit)) + "'";
                }
            }
            if (problem == null) {
                try {
                    balance = parseAmount(row.get(cBalance), true);
                    if (balance == null) {
                        problem = "balance is missing";
                    }
                } catch (NumberFormatException e) {
                    problem = "invalid balance value '" + shorten(row.get(cBalance)) + "'";
                }
            }
            if (problem == null) {
                boolean hasDebit = debit != null && debit.signum() > 0;
                boolean hasCredit = credit != null && credit.signum() > 0;
                if (hasDebit && hasCredit) {
                    problem = "both debit and credit are filled in";
                } else if (!hasDebit && !hasCredit) {
                    problem = "neither debit nor credit has an amount";
                }
            }

            if (problem != null) {
                invalid++;
                if (issues.size() < MAX_DETAIL_WARNINGS) {
                    issues.add("Row " + row.rowNumber() + ": " + problem + ".");
                }
                continue;
            }

            if (description.length() > 500) {
                description = description.substring(0, 500);
            }
            BigDecimal d = debit == null ? BigDecimal.ZERO.setScale(2) : debit;
            BigDecimal c = credit == null ? BigDecimal.ZERO.setScale(2) : credit;
            String key = date + "|" + description.toLowerCase(Locale.ROOT) + "|" + d + "|" + c + "|" + balance;
            if (!seen.add(key)) {
                duplicates++;
                continue;
            }
            valid.add(new ValidatedRow(row.rowNumber(), date, description, d, c, balance));
        }

        int total = csv.rows().size();
        if (valid.isEmpty()) {
            String first = issues.isEmpty() ? "" : " First problem: " + issues.get(0);
            throw new CsvValidationException("No valid transactions were found in this file." + first);
        }

        List<ValidatedRow> ordered = chronological(valid);

        int mismatches = 0;
        List<String> mismatchDetails = new ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            ValidatedRow prev = ordered.get(i - 1);
            ValidatedRow cur = ordered.get(i);
            BigDecimal expected = prev.balance().add(cur.credit()).subtract(cur.debit());
            if (expected.subtract(cur.balance()).abs().compareTo(TOLERANCE) > 0) {
                mismatches++;
                if (mismatchDetails.size() < 3) {
                    mismatchDetails.add("Row " + cur.rowNumber()
                            + ": balance does not match the previous balance and this amount.");
                }
            }
        }

        long historyDays = ChronoUnit.DAYS.between(ordered.get(0).date(), ordered.get(ordered.size() - 1).date()) + 1;

        List<String> warnings = new ArrayList<>(issues);
        if (invalid > issues.size()) {
            warnings.add((invalid - issues.size()) + " more invalid row(s) were skipped.");
        }
        if (invalid > 0) {
            warnings.add(invalid + " invalid row(s) were skipped and not saved.");
        }
        if (duplicates > 0) {
            warnings.add(duplicates + " duplicate row(s) were skipped. Review recommended; this is not a fraud determination.");
        }
        if (mismatches > 0) {
            warnings.addAll(mismatchDetails);
            warnings.add(mismatches + " row(s) have a balance that does not add up. "
                    + "Check that no rows are missing from the statement.");
        }
        if (historyDays < RECOMMENDED_HISTORY_DAYS) {
            warnings.add("Only " + historyDays + " day(s) of history found. At least "
                    + RECOMMENDED_HISTORY_DAYS + " days (about 3 months) are recommended for a reliable forecast.");
        }

        int score = qualityScore(total, invalid, duplicates, mismatches, valid.size(), historyDays);
        DataQualityReport report = new DataQualityReport(total, valid.size(), invalid, duplicates, mismatches,
                historyDays, warnings, score, DataQualityReport.NOTE);
        return new ValidationOutcome(ordered, report);
    }

    /** 100 minus penalties for invalid rows, duplicates, balance mismatches and short history. */
    static int qualityScore(int total, int invalid, int duplicates, int mismatches, int valid, long historyDays) {
        double invalidRatio = total == 0 ? 0 : (double) invalid / total;
        double duplicateRatio = total == 0 ? 0 : (double) duplicates / total;
        double mismatchRatio = valid == 0 ? 0 : (double) mismatches / valid;
        double score = 100
                - 60 * invalidRatio
                - 20 * duplicateRatio
                - 15 * mismatchRatio
                - (historyDays < RECOMMENDED_HISTORY_DAYS ? 10 : 0);
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    /** Orders rows oldest to newest. Bank files are often newest-first; same-day order is kept. */
    private List<ValidatedRow> chronological(List<ValidatedRow> rows) {
        List<ValidatedRow> copy = new ArrayList<>(rows);
        LocalDate first = copy.get(0).date();
        LocalDate last = copy.get(copy.size() - 1).date();
        if (first.isAfter(last)) {
            java.util.Collections.reverse(copy);
        }
        copy.sort(Comparator.comparing(ValidatedRow::date));   // stable sort keeps same-day order
        return copy;
    }

    static LocalDate parseDate(String raw) {
        String text = raw.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                LocalDate d = LocalDate.parse(text, f);
                if (d.getYear() >= 2000 && d.getYear() <= 2100) {
                    return d;
                }
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    /**
     * Returns null for a blank cell. Throws NumberFormatException for anything that is not a number.
     * Accepts commas, the rupee sign, "Rs", and a trailing Cr/Dr (balances).
     */
    static BigDecimal parseAmount(String raw, boolean allowNegative) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        boolean negative = false;
        if (s.endsWith("dr")) {
            negative = true;
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("cr")) {
            s = s.substring(0, s.length() - 2);
        }
        s = s.replace("\u20B9", "").replace("inr", "").replace("rs.", "").replace("rs", "")
                .replace(",", "").replaceAll("\\s+", "");
        if (s.startsWith("-")) {
            negative = !negative;
            s = s.substring(1);
        } else if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (!s.matches("\\d+(\\.\\d+)?")) {
            throw new NumberFormatException("not a number");
        }
        BigDecimal value = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new NumberFormatException("too large");
        }
        if (negative) {
            value = value.negate();
        }
        if (!allowNegative && value.signum() < 0) {
            throw new NumberFormatException("negative");
        }
        return value;
    }

    private static String shorten(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() > 25 ? t.substring(0, 25) + "..." : t;
    }
}
