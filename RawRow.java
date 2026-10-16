package com.msme.cashflowguard.model;

import java.util.List;

/** One data line of the CSV, exactly as text (before validation). */
public record RawRow(int rowNumber, List<String> values) {

    public String get(int index) {
        if (index < 0 || index >= values.size()) {
            return "";
        }
        String v = values.get(index);
        return v == null ? "" : v;
    }
}
