package com.msme.cashflowguard.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The optional "Business details" page: extra facts a bank statement cannot show.
 * ONE user -> ONE business-details record.
 */
@Entity
@Table(name = "business_details")
public class BusinessDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "monthly_rent", precision = 15, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "other_fixed_expenses", precision = 15, scale = 2)
    private BigDecimal otherFixedExpenses;

    @Column(name = "cash_in_hand", precision = 15, scale = 2)
    private BigDecimal cashInHand;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "business_upcoming_payments",
            joinColumns = @JoinColumn(name = "business_detail_id"))
    private List<UpcomingPayment> upcomingPayments = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent;
    }

    public BigDecimal getOtherFixedExpenses() {
        return otherFixedExpenses;
    }

    public void setOtherFixedExpenses(BigDecimal otherFixedExpenses) {
        this.otherFixedExpenses = otherFixedExpenses;
    }

    public BigDecimal getCashInHand() {
        return cashInHand;
    }

    public void setCashInHand(BigDecimal cashInHand) {
        this.cashInHand = cashInHand;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<UpcomingPayment> getUpcomingPayments() {
        return upcomingPayments;
    }

    public void setUpcomingPayments(List<UpcomingPayment> upcomingPayments) {
        this.upcomingPayments = upcomingPayments;
    }
}
