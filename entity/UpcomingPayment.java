package com.msme.cashflowguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A known upcoming payment typed in by the owner (stored inside business_upcoming_payments). */
@Embeddable
public class UpcomingPayment {

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    public UpcomingPayment() {
    }

    public UpcomingPayment(String name, BigDecimal amount, LocalDate dueDate) {
        this.name = name;
        this.amount = amount;
        this.dueDate = dueDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
