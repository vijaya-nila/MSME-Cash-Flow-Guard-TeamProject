package com.msme.cashflowguard.entity;

import com.msme.cashflowguard.model.FlowDirection;
import com.msme.cashflowguard.model.Frequency;
import com.msme.cashflowguard.model.TransactionCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A payment pattern detected in a statement (salary, rent, EMI, repeat customer...). */
@Entity
@Table(name = "recurring_transactions")
public class RecurringTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private BankStatement statement;

    /** Counterparty / payment name, e.g. "Sri Kumaran Textiles" or "Salary". */
    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, columnDefinition = "VARCHAR(30)")
    private TransactionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, columnDefinition = "VARCHAR(10)")
    private FlowDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, columnDefinition = "VARCHAR(15)")
    private Frequency frequency;

    @Column(name = "average_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal averageAmount;

    @Column(name = "last_occurrence_date", nullable = false)
    private LocalDate lastOccurrenceDate;

    /** Next date after the last occurrence, following the detected frequency. */
    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BankStatement getStatement() {
        return statement;
    }

    public void setStatement(BankStatement statement) {
        this.statement = statement;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public void setCategory(TransactionCategory category) {
        this.category = category;
    }

    public FlowDirection getDirection() {
        return direction;
    }

    public void setDirection(FlowDirection direction) {
        this.direction = direction;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public BigDecimal getAverageAmount() {
        return averageAmount;
    }

    public void setAverageAmount(BigDecimal averageAmount) {
        this.averageAmount = averageAmount;
    }

    public LocalDate getLastOccurrenceDate() {
        return lastOccurrenceDate;
    }

    public void setLastOccurrenceDate(LocalDate lastOccurrenceDate) {
        this.lastOccurrenceDate = lastOccurrenceDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(int occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
