package com.msme.cashflowguard.entity;

import com.msme.cashflowguard.model.TransactionCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One valid row of a bank statement. MANY transactions -> ONE {@link BankStatement}.
 * Money uses BigDecimal, dates use LocalDate (guide rules).
 */
@Entity
@Table(name = "transactions",
        indexes = @Index(name = "idx_transactions_statement", columnList = "statement_id, sequence_no"))
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private BankStatement statement;

    /** Position in chronological order (1 = oldest). Keeps same-day rows in the right order. */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "debit", nullable = false, precision = 15, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false, precision = 15, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, columnDefinition = "VARCHAR(30)")
    private TransactionCategory category = TransactionCategory.UNKNOWN;

    @Column(name = "classification_confidence")
    private Double classificationConfidence;

    @Column(name = "classification_reason", length = 255)
    private String classificationReason;

    /** True when this row is part of a detected recurring pattern. */
    @Column(name = "recurring", nullable = false)
    private boolean recurring;

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

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public void setDebit(BigDecimal debit) {
        this.debit = debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public void setCategory(TransactionCategory category) {
        this.category = category;
    }

    public Double getClassificationConfidence() {
        return classificationConfidence;
    }

    public void setClassificationConfidence(Double classificationConfidence) {
        this.classificationConfidence = classificationConfidence;
    }

    public String getClassificationReason() {
        return classificationReason;
    }

    public void setClassificationReason(String classificationReason) {
        this.classificationReason = classificationReason;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public boolean isDebit() {
        return debit != null && debit.signum() > 0;
    }

    public boolean isCredit() {
        return credit != null && credit.signum() > 0;
    }
}
