package com.msme.cashflowguard.service;

import com.msme.cashflowguard.entity.BankStatement;
import com.msme.cashflowguard.entity.BusinessDetail;
import com.msme.cashflowguard.exception.BadRequestException;
import com.msme.cashflowguard.exception.ResourceNotFoundException;
import com.msme.cashflowguard.model.StatementData;
import com.msme.cashflowguard.repository.BankStatementRepository;
import com.msme.cashflowguard.repository.BusinessDetailRepository;
import com.msme.cashflowguard.repository.RecurringTransactionRepository;
import com.msme.cashflowguard.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads one statement (only if it belongs to the user) with everything the analysis needs. */
@Service
public class StatementDataService {

    private final BankStatementRepository statements;
    private final TransactionRepository transactions;
    private final RecurringTransactionRepository recurring;
    private final BusinessDetailRepository businessDetails;

    public StatementDataService(BankStatementRepository statements,
                                TransactionRepository transactions,
                                RecurringTransactionRepository recurring,
                                BusinessDetailRepository businessDetails) {
        this.statements = statements;
        this.transactions = transactions;
        this.recurring = recurring;
        this.businessDetails = businessDetails;
    }

    /** {@code statementId == null} means "the user's most recent statement". */
    @Transactional(readOnly = true)
    public StatementData load(Long userId, Long statementId) {
        BankStatement statement;
        if (statementId == null) {
            statement = statements.findFirstByOwnerIdOrderByUploadedAtDesc(userId)
                    .orElseThrow(() -> new BadRequestException("No statement uploaded yet. Upload a bank statement first."));
        } else {
            statement = statements.findByIdAndOwnerId(statementId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("That statement was not found."));
        }
        BusinessDetail business = businessDetails.findByUserId(userId).orElse(null);
        return new StatementData(
                statement,
                transactions.findByStatementIdOrderBySequenceNoAsc(statement.getId()),
                recurring.findByStatementIdOrderByExpectedDateAsc(statement.getId()),
                business);
    }
}
