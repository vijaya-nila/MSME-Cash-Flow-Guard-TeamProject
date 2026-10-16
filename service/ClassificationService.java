package com.msme.cashflowguard.service;

import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.model.ClassificationResult;
import com.msme.cashflowguard.model.TransactionCategory;
import com.msme.cashflowguard.util.PartyNameExtractor;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Stage 5: rule-based classification using (1) description keywords, (2) whether money
 * went in or out, and (3) repeated patterns. A transaction with weak evidence stays UNKNOWN.
 */
@Service
public class ClassificationService {

    /** Below this confidence a transaction is left as UNKNOWN. */
    public static final double MIN_CONFIDENCE = 0.50;

    private static final Pattern SALARY = Pattern.compile("\\b(salary|salaries|payroll|wages?|stipend)\\b");
    private static final Pattern RENT = Pattern.compile("\\b(rent|lease|landlord)\\b");
    private static final Pattern EMI = Pattern.compile("\\b(emi|loan|nach|ecs|instal+ment|repayment)\\b");
    private static final Pattern UTILITY = Pattern.compile(
            "\\b(electricity|electric|eb bill|tneb|tangedco|bescom|msedcl|water|gas|internet|broadband|airtel|jio|bsnl|vodafone|telephone|recharge|dth|wifi)\\b");
    private static final Pattern CASH_OUT = Pattern.compile("\\b(atm|cwdr|nfs)\\b|cash\\s*(wdl|wd|withdrawal)|self\\s*(cheque|chq)");
    private static final Pattern OWNER = Pattern.compile("\\b(owner|proprietor|drawings?|capital|director|partner|personal|self)\\b");
    private static final Pattern SUPPLIER = Pattern.compile(
            "\\b(supplier|suppliers|vendor|purchase|purchases|raw\\s*material|materials?|stock|inventory|wholesale|traders?|trading|enterprises|agencies|distributors?|industries|packaging)\\b");
    private static final Pattern SALES = Pattern.compile(
            "\\b(pos|sale|sales|settlement|razorpay|payu|phonepe|gpay|googlepay|bharatpe|qr|cdm)\\b|cash\\s*(deposit|dep)|card\\s*settlement");
    private static final Pattern CUSTOMER_WORDS = Pattern.compile(
            "\\b(customer|invoice|payment received|received|receipt|neft|rtgs|imps|upi|cheque|chq|clearing)\\b");

    /** Classifies one transaction. {@code partyRepeats} = how many times this party appears in the same direction. */
    public ClassificationResult classify(String description, BigDecimal debit, BigDecimal credit, int partyRepeats) {
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        boolean isDebit = debit != null && debit.signum() > 0;
        boolean isCredit = credit != null && credit.signum() > 0;

        if (isDebit && !isCredit) {
            ClassificationResult r = classifyDebit(d, partyRepeats);
            return finish(r);
        }
        if (isCredit && !isDebit) {
            ClassificationResult r = classifyCredit(d, partyRepeats);
            return finish(r);
        }
        return new ClassificationResult(TransactionCategory.UNKNOWN, 0.0, "No clear money-in or money-out amount.");
    }

    private ClassificationResult classifyDebit(String d, int repeats) {
        Matcher m;
        if ((m = SALARY.matcher(d)).find()) {
            return result(TransactionCategory.SALARY, 0.95, "Description contains '" + m.group() + "' and money went out.");
        }
        if ((m = RENT.matcher(d)).find()) {
            return result(TransactionCategory.RENT, 0.90, "Description contains '" + m.group() + "' and money went out.");
        }
        if ((m = EMI.matcher(d)).find()) {
            return result(TransactionCategory.EMI, 0.90, "Description contains '" + m.group() + "' and money went out.");
        }
        if ((m = UTILITY.matcher(d)).find()) {
            return result(TransactionCategory.UTILITY, 0.85, "Description contains '" + m.group() + "' (a utility or telecom bill).");
        }
        if ((m = CASH_OUT.matcher(d)).find()) {
            return result(TransactionCategory.CASH_WITHDRAWAL, 0.90, "Description looks like a cash withdrawal ('" + m.group().trim() + "').");
        }
        if ((m = OWNER.matcher(d)).find()) {
            return result(TransactionCategory.OWNER_TRANSFER, 0.80, "Description contains '" + m.group() + "', which suggests an owner transfer.");
        }
        if ((m = SUPPLIER.matcher(d)).find()) {
            return result(TransactionCategory.SUPPLIER_PAYMENT, 0.75, "Description contains '" + m.group() + "' and money went out.");
        }
        if (repeats >= 3) {
            return result(TransactionCategory.SUPPLIER_PAYMENT, 0.60,
                    "The same party was paid " + repeats + " times, which looks like a supplier.");
        }
        return result(TransactionCategory.UNKNOWN, 0.30, "No clear keyword or repeated pattern, so it is left unclassified.");
    }

    private ClassificationResult classifyCredit(String d, int repeats) {
        Matcher m;
        if ((m = OWNER.matcher(d)).find()) {
            return result(TransactionCategory.OWNER_TRANSFER, 0.80, "Description contains '" + m.group() + "', which suggests money from the owner.");
        }
        if ((m = SALES.matcher(d)).find()) {
            return result(TransactionCategory.SALES_RECEIPT, 0.80, "Description contains '" + m.group().trim() + "' (a sales or settlement credit).");
        }
        if ((m = CUSTOMER_WORDS.matcher(d)).find()) {
            double conf = repeats >= 2 ? 0.80 : 0.70;
            String why = "Money came in with '" + m.group() + "' in the description"
                    + (repeats >= 2 ? " and the same payer appears " + repeats + " times." : ".");
            return result(TransactionCategory.CUSTOMER_PAYMENT, conf, why);
        }
        if (repeats >= 2) {
            return result(TransactionCategory.CUSTOMER_PAYMENT, 0.60,
                    "The same payer sent money " + repeats + " times, which looks like a customer.");
        }
        return result(TransactionCategory.UNKNOWN, 0.30, "No clear keyword or repeated pattern, so it is left unclassified.");
    }

    private ClassificationResult result(TransactionCategory c, double confidence, String reason) {
        return new ClassificationResult(c, confidence, reason);
    }

    /** Weak evidence is never forced into a category. */
    private ClassificationResult finish(ClassificationResult r) {
        if (r.confidence() < MIN_CONFIDENCE && r.category() != TransactionCategory.UNKNOWN) {
            return new ClassificationResult(TransactionCategory.UNKNOWN, r.confidence(),
                    "Confidence too low to assign a category.");
        }
        return r;
    }

    /** Classifies a whole statement, using repeated-party counts as extra evidence. */
    public void classifyAll(List<Transaction> transactions) {
        Map<String, Integer> counts = new HashMap<>();
        for (Transaction t : transactions) {
            counts.merge(partyKey(t), 1, Integer::sum);
        }
        for (Transaction t : transactions) {
            ClassificationResult r = classify(t.getDescription(), t.getDebit(), t.getCredit(),
                    counts.getOrDefault(partyKey(t), 1));
            t.setCategory(r.category());
            t.setClassificationConfidence(r.confidence());
            t.setClassificationReason(truncate(r.reason(), 255));
        }
    }

    static String partyKey(Transaction t) {
        String dir = t.isDebit() ? "D" : "C";
        return dir + ":" + PartyNameExtractor.key(PartyNameExtractor.extract(t.getDescription()));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
