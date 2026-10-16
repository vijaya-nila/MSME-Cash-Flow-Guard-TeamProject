package com.msme.cashflowguard.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pulls a readable counterparty name out of a bank narration.
 *
 * <pre>
 *  "UPI/CR/412345/Ravi Traders/ravi@okaxis/Payment" -> "Ravi Traders"
 *  "NEFT-ABC CORP-INV123"                            -> "Abc Corp"
 *  "Salary March 2025"                               -> "Salary"
 *  "Payment from Ravi Traders"                       -> "Ravi Traders"
 * </pre>
 *
 * The same function is used for classification, customers, recurring detection and
 * the what-if simulator, so a name shown in the customer drop-down always matches.
 */
public final class PartyNameExtractor {

    public static final String UNKNOWN = "Unknown";

    private static final Set<String> STOP_WORDS = Set.of(
            "upi", "neft", "imps", "rtgs", "ach", "nach", "ecs", "cr", "dr", "credit", "debit",
            "transfer", "trf", "tfr", "payment", "pymt", "pmt", "paid", "received", "receipt",
            "from", "to", "by", "via", "ref", "refno", "no", "txn", "trxn", "transaction",
            "inv", "invoice", "bill", "chq", "cheque", "chg", "clg", "clearing", "bank", "mob",
            "ok", "okaxis", "oksbi", "okicici", "okhdfcbank", "ybl", "ibl", "axl", "paytm",
            "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "the", "of", "and", "for", "a", "an", "in", "on", "at",
            "ms", "mr", "mrs", "shri", "smt",
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
            "january", "february", "march", "april", "june", "july", "august", "september",
            "october", "november", "december");

    private PartyNameExtractor() {
    }

    public static String extract(String description) {
        if (description == null || description.isBlank()) {
            return UNKNOWN;
        }
        String[] segments = description.split("[/|\\\\\\-_:;,]+");
        for (String segment : segments) {
            List<String> words = cleanWords(segment);
            if (!words.isEmpty()) {
                return titleCase(words.subList(0, Math.min(4, words.size())));
            }
        }
        return UNKNOWN;
    }

    /** Lower-case identity used to compare two names. */
    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> cleanWords(String segment) {
        List<String> out = new ArrayList<>();
        for (String raw : segment.trim().split("\\s+")) {
            if (raw.isEmpty() || raw.chars().anyMatch(Character::isDigit) || raw.indexOf('@') >= 0) {
                continue;
            }
            String word = raw.replaceAll("[^A-Za-z&]", "");
            if (word.length() < 2) {
                continue;
            }
            if (STOP_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(word);
        }
        return out;
    }

    private static String titleCase(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String lower = w.toLowerCase(Locale.ROOT);
            sb.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return sb.toString();
    }
}
