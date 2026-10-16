package com.msme.cashflowguard.model;

import com.msme.cashflowguard.entity.BankStatement;
import com.msme.cashflowguard.entity.BusinessDetail;
import com.msme.cashflowguard.entity.RecurringTransaction;
import com.msme.cashflowguard.entity.Transaction;
import java.util.List;

/** Everything the analysis services need about one statement. {@code business} may be null. */
public record StatementData(
        BankStatement statement,
        List<Transaction> transactions,
        List<RecurringTransaction> recurring,
        BusinessDetail business) {
}
