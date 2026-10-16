package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.CustomerInflow;
import com.msme.cashflowguard.dto.DataQualityReport;
import com.msme.cashflowguard.dto.MessageResponse;
import com.msme.cashflowguard.dto.TransactionDto;
import com.msme.cashflowguard.dto.UploadResponse;
import com.msme.cashflowguard.entity.AppUser;
import com.msme.cashflowguard.entity.BankStatement;
import com.msme.cashflowguard.entity.RecurringTransaction;
import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.exception.ResourceNotFoundException;
import com.msme.cashflowguard.exception.UnauthorizedException;
import com.msme.cashflowguard.model.ParsedCsv;
import com.msme.cashflowguard.model.RecurringResult;
import com.msme.cashflowguard.model.ValidatedRow;
import com.msme.cashflowguard.model.ValidationOutcome;
import com.msme.cashflowguard.repository.AppUserRepository;
import com.msme.cashflowguard.repository.BankStatementRepository;
import com.msme.cashflowguard.repository.RecurringTransactionRepository;
import com.msme.cashflowguard.repository.TransactionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates the upload pipeline:
 * parse (Stage 3) -> validate (4) -> save -> classify (5) -> detect recurring (7) -> respond.
 * Only valid, non-duplicate rows are ever saved.
 */
@Service
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);
    private static final int PREVIEW_SIZE = 200;

    private final CsvParserService csvParser;
    private final ValidationService validation;
    private final ClassificationService classification;
    private final RecurringTransactionService recurringService;
    private final CashFlowAnalysisService analysis;
    private final AppUserRepository users;
    private final BankStatementRepository statements;
    private final TransactionRepository transactions;
    private final RecurringTransactionRepository recurringRepo;
    private final Clock clock;

    public StatementService(CsvParserService csvParser, ValidationService validation,
                            ClassificationService classification, RecurringTransactionService recurringService,
                            CashFlowAnalysisService analysis, AppUserRepository users,
                            BankStatementRepository statements, TransactionRepository transactions,
                            RecurringTransactionRepository recurringRepo, Clock clock) {
        this.csvParser = csvParser;
        this.validation = validation;
        this.classification = classification;
        this.recurringService = recurringService;
        this.analysis = analysis;
        this.users = users;
        this.statements = statements;
        this.transactions = transactions;
        this.recurringRepo = recurringRepo;
        this.clock = clock;
    }

    @Transactional
    public UploadResponse upload(Long userId, MultipartFile file) {
        AppUser owner = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Please login to continue."));

        ParsedCsv parsed = csvParser.parse(file);
        ValidationOutcome outcome = validation.validate(parsed);
        DataQualityReport report = outcome.report();
        List<ValidatedRow> rows = outcome.rows();

        BankStatement statement = new BankStatement();
        statement.setOwner(owner);
        statement.setFilename(cleanFilename(parsed.filename()));
        statement.setUploadedAt(LocalDateTime.now(clock));
        statement.setTotalRows(report.totalRows());
        statement.setValidRows(report.validRows());
        statement.setInvalidRows(report.invalidRows());
        statement.setDuplicateRows(report.duplicateRows());
        statement.setDataQualityScore(report.qualityScore());
        statement.setFirstTransactionDate(rows.get(0).date());
        statement.setLastTransactionDate(rows.get(rows.size() - 1).date());
        statement.setLatestBalance(rows.get(rows.size() - 1).balance());
        statement = statements.save(statement);

        List<Transaction> txns = new ArrayList<>(rows.size());
        int seq = 1;
        for (ValidatedRow r : rows) {
            Transaction t = new Transaction();
            t.setStatement(statement);
            t.setSequenceNo(seq++);
            t.setTxnDate(r.date());
            t.setDescription(r.description());
            t.setDebit(r.debit());
            t.setCredit(r.credit());
            t.setBalance(r.balance());
            txns.add(t);
        }
        classification.classifyAll(txns);
        List<RecurringResult> patterns = recurringService.detect(txns);

        transactions.saveAll(txns);
        List<RecurringTransaction> recurringEntities = new ArrayList<>();
        for (RecurringResult p : patterns) {
            RecurringTransaction e = new RecurringTransaction();
            e.setStatement(statement);
            e.setDescription(p.description());
            e.setCategory(p.category());
            e.setDirection(p.direction());
            e.setFrequency(p.frequency());
            e.setAverageAmount(p.averageAmount());
            e.setLastOccurrenceDate(p.lastOccurrenceDate());
            e.setExpectedDate(p.expectedDate());
            e.setOccurrenceCount(p.occurrenceCount());
            e.setConfidence(p.confidence());
            recurringEntities.add(e);
        }
        recurringRepo.saveAll(recurringEntities);
        log.info("Statement {} saved for user {}: {} transactions, {} recurring patterns",
                statement.getId(), userId, txns.size(), recurringEntities.size());

        List<TransactionDto> preview = new ArrayList<>();
        for (int i = txns.size() - 1; i >= 0 && preview.size() < PREVIEW_SIZE; i--) {
            preview.add(TransactionDto.from(txns.get(i)));
        }
        List<String> customers = new ArrayList<>();
        for (CustomerInflow c : analysis.customerInflows(txns)) {
            customers.add(c.customerName());
        }

        String message = "Statement uploaded: " + report.validRows() + " transaction(s) saved"
                + (report.invalidRows() > 0 ? ", " + report.invalidRows() + " invalid row(s) skipped" : "")
                + (report.duplicateRows() > 0 ? ", " + report.duplicateRows() + " duplicate(s) skipped" : "") + ".";

        return new UploadResponse(statement.getId(), statement.getFilename(), statement.getUploadedAt(), message,
                report.totalRows(), report.validRows(), report.invalidRows(), report.duplicateRows(),
                report.warnings(), report.qualityScore(), report.note(),
                statement.getFirstTransactionDate(), statement.getLastTransactionDate(), statement.getLatestBalance(),
                preview, customers);
    }

    /** Deletes the statement and everything calculated from it (only if it belongs to the user). */
    @Transactional
    public MessageResponse delete(Long userId, Long statementId) {
        BankStatement statement = statements.findByIdAndOwnerId(statementId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("That statement was not found."));
        transactions.deleteByStatementId(statementId);
        recurringRepo.deleteByStatementId(statementId);
        statements.delete(statement);
        log.info("Statement {} deleted by user {}", statementId, userId);
        return new MessageResponse("Your uploaded data was deleted.");
    }

    private static String cleanFilename(String name) {
        String n = name == null ? "statement.csv" : name;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        return n.length() > 255 ? n.substring(0, 255) : n;
    }
}
