package com.msme.cashflowguard.model;

import java.util.List;
import java.util.Map;

/** Output of the CSV parser: header names, where each column is, and the raw rows. */
public record ParsedCsv(String filename, List<String> headers, Map<String, Integer> columnIndex, List<RawRow> rows) {
}
