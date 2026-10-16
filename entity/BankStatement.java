package com.msme.cashflowguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One uploaded CSV. Many statements can belong to one user (MANY statements -> ONE user).
 * The transactions of a statement are stored in the "transactions" table
 * (MANY transactions -> ONE statement, see {@link Transaction}).
 */
@Entity
@Table(name = "bank_statements")
public class BankStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    /** Upload timestamp (guide: LocalDateTime for upload time). */
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "invalid_rows", nullable = false)
    private int invalidRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "data_quality_score", nullable = false)
    private int dataQualityScore;

    @Column(name = "first_transaction_date")
    private LocalDate firstTransactionDate;

    @Column(name = "last_transaction_date")
    private LocalDate lastTransactionDate;

    @Column(name = "latest_balance", precision = 15, scale = 2)
    private BigDecimal latestBalance;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public void setOwner(AppUser owner) {
        this.owner = owner;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public void setValidRows(int validRows) {
        this.validRows = validRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(int invalidRows) {
        this.invalidRows = invalidRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public void setDuplicateRows(int duplicateRows) {
        this.duplicateRows = duplicateRows;
    }

    public int getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(int dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    public LocalDate getFirstTransactionDate() {
        return firstTransactionDate;
    }

    public void setFirstTransactionDate(LocalDate firstTransactionDate) {
        this.firstTransactionDate = firstTransactionDate;
    }

    public LocalDate getLastTransactionDate() {
        return lastTransactionDate;
    }

    public void setLastTransactionDate(LocalDate lastTransactionDate) {
        this.lastTransactionDate = lastTransactionDate;
    }

    public BigDecimal getLatestBalance() {
        return latestBalance;
    }

    public void setLatestBalance(BigDecimal latestBalance) {
        this.latestBalance = latestBalance;
    }
}
