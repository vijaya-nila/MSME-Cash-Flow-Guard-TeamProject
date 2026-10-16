package com.msme.cashflowguard.model;

import com.msme.cashflowguard.dto.DataQualityReport;
import java.util.List;

/** Valid rows in chronological order + the data-quality report. */
public record ValidationOutcome(List<ValidatedRow> rows, DataQualityReport report) {
}
