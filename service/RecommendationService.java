package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.ForecastDay;
import com.msme.cashflowguard.dto.ForecastResponse;
import com.msme.cashflowguard.dto.Recommendation;
import com.msme.cashflowguard.dto.RecommendationResponse;
import com.msme.cashflowguard.dto.StressResult;
import com.msme.cashflowguard.entity.RecurringTransaction;
import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.model.FlowDirection;
import com.msme.cashflowguard.model.Priority;
import com.msme.cashflowguard.model.StatementData;
import com.msme.cashflowguard.model.StressLevel;
import com.msme.cashflowguard.model.TransactionCategory;
import com.msme.cashflowguard.util.MoneyUtil;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Stage 11: practical, plain-language cash-management suggestions.
 * It never gives legal, lending, tax or investment advice, and never claims fraud.
 */
@Service
public class RecommendationService {

    private final StatementDataService dataService;
    private final ForecastService forecastService;
    private final StressScoreService stressService;
    private final Clock clock;

    public RecommendationService(StatementDataService dataService, ForecastService forecastService,
                                 StressScoreService stressService, Clock clock) {
        this.dataService = dataService;
        this.forecastService = forecastService;
        this.stressService = stressService;
        this.clock = clock;
    }

    public RecommendationResponse getRecommendations(Long userId, Long statementId) {
        StatementData data = dataService.load(userId, statementId);
        LocalDate today = LocalDate.now(clock);
        ForecastResponse forecast = forecastService.compute(data, today, null);
        StressResult stress = stressService.score(data.transactions(), forecast);
        return new RecommendationResponse(recommend(data, forecast, stress, today), RecommendationResponse.DISCLAIMER);
    }

    public List<Recommendation> recommend(StatementData data, ForecastResponse forecast, StressResult stress, LocalDate today) {
        List<Recommendation> list = new ArrayList<>();
        List<Transaction> txns = data.transactions();
        LocalDate lastDate = txns.get(txns.size() - 1).getTxnDate();
        long staleDays = Math.max(0, ChronoUnit.DAYS.between(lastDate, today));
        BigDecimal threshold = forecast.shortageThreshold();

        // ---- stress level ----
        if (stress.stressLevel() == StressLevel.HIGH) {
            list.add(new Recommendation("Protect your cash over the next 30 days", Priority.HIGH,
                    "Your cash-stress score is " + stress.score() + " out of 100 (HIGH).",
                    String.join(" ", stress.mainRiskFactors().isEmpty() ? List.of("Several measures are weak.") : stress.mainRiskFactors()),
                    "List the payments due in the next 30 days and decide which are essential. Follow up on money owed to you first."));
        } else if (stress.stressLevel() == StressLevel.MEDIUM) {
            list.add(new Recommendation("Keep a closer watch on your cash", Priority.MEDIUM,
                    "Your cash-stress score is " + stress.score() + " out of 100 (MEDIUM).",
                    String.join(", ", stress.mainRiskFactors().isEmpty() ? List.of("Some measures are below comfortable levels") : stress.mainRiskFactors()) + ".",
                    "Check your balance weekly and avoid adding non-essential payments until the forecast improves."));
        }

        // ---- low projected balance ----
        if (forecast.minimumExpectedBalance().signum() < 0) {
            list.add(new Recommendation("Your balance may go below zero", Priority.HIGH,
                    "The forecast shows an expected balance of " + MoneyUtil.inr(forecast.minimumExpectedBalance())
                            + " around " + forecast.minimumExpectedBalanceDate() + ".",
                    "Expected outflows are higher than expected inflows before that date.",
                    "Arrange funds or move payments to after " + forecast.minimumExpectedBalanceDate() + " where possible."));
        } else if (forecast.shortageRiskDays() > 0) {
            list.add(new Recommendation("Low balance expected on " + forecast.shortageRiskDays() + " day(s)", Priority.MEDIUM,
                    "Your lowest expected balance is " + MoneyUtil.inr(forecast.minimumExpectedBalance())
                            + " on " + forecast.minimumExpectedBalanceDate() + ", under the safety level of " + MoneyUtil.inr(threshold) + ".",
                    "The forecast flags days below about " + 7 + " days of your normal spending.",
                    "Plan ahead for " + forecast.minimumExpectedBalanceDate() + " and review payments due around then."));
        }

        // ---- delayed customer payments ----
        for (RecurringTransaction r : data.recurring()) {
            if (r.getDirection() == FlowDirection.INFLOW && r.getCategory() == TransactionCategory.CUSTOMER_PAYMENT
                    && r.getExpectedDate().plusDays(3).isBefore(lastDate)) {
                long late = ChronoUnit.DAYS.between(r.getExpectedDate(), lastDate);
                list.add(new Recommendation("Follow up with " + r.getDescription(), Priority.MEDIUM,
                        r.getDescription() + " usually pays " + r.getFrequency().getLabel().toLowerCase() + " (about "
                                + MoneyUtil.inr(r.getAverageAmount()) + "), but no payment appears after " + r.getLastOccurrenceDate() + ".",
                        "The payment looks about " + late + " day(s) late compared with its usual pattern.",
                        "Contact " + r.getDescription() + " to confirm when the payment will arrive."));
            }
        }

        // ---- upcoming EMI / salary / supplier inside the forecast window ----
        for (RecurringTransaction r : data.recurring()) {
            if (r.getDirection() != FlowDirection.OUTFLOW) {
                continue;
            }
            LocalDate next = r.getFrequency().nextOnOrAfter(r.getLastOccurrenceDate(), forecast.startDate());
            if (next.isAfter(forecast.endDate())) {
                continue;
            }
            BigDecimal balanceThen = balanceOn(forecast, next);
            boolean tight = balanceThen != null && balanceThen.compareTo(threshold) < 0;
            if (r.getCategory() == TransactionCategory.EMI) {
                list.add(new Recommendation("Arrange funds before the EMI date", tight ? Priority.HIGH : Priority.LOW,
                        "An EMI of about " + MoneyUtil.inr(r.getAverageAmount()) + " is expected on " + next + ".",
                        tight ? "Your expected balance around that date is only " + MoneyUtil.inr(balanceThen) + "."
                                : "Your forecast shows enough cash for it.",
                        "Keep the EMI amount available in the account by " + next + "."));
            } else if (r.getCategory() == TransactionCategory.SALARY) {
                list.add(new Recommendation("Salary payment coming up", tight ? Priority.HIGH : Priority.LOW,
                        "Salaries of about " + MoneyUtil.inr(r.getAverageAmount()) + " are expected around " + next + ".",
                        tight ? "Your expected balance around that date is only " + MoneyUtil.inr(balanceThen) + "."
                                : "Your forecast shows enough cash for it.",
                        "Make sure customer receipts arrive before " + next + " or keep a cushion for payroll."));
            } else if (r.getCategory() == TransactionCategory.SUPPLIER_PAYMENT && tight) {
                list.add(new Recommendation("Review the upcoming supplier payment", Priority.MEDIUM,
                        "A payment of about " + MoneyUtil.inr(r.getAverageAmount()) + " to " + r.getDescription()
                                + " is expected on " + next + ".",
                        "Your expected balance around that date is only " + MoneyUtil.inr(balanceThen) + ".",
                        "Check whether this payment can be timed after your next customer receipts."));
            }
        }

        // ---- data freshness ----
        if (staleDays > 30) {
            list.add(new Recommendation("Upload a newer bank statement", Priority.HIGH,
                    "Your statement ends on " + lastDate + ", " + staleDays + " days ago.",
                    "Forecasts based on old data are unreliable.",
                    "Export a recent statement from your bank and upload it."));
        } else if (staleDays > 7) {
            list.add(new Recommendation("Upload a newer bank statement", Priority.MEDIUM,
                    "Your statement ends on " + lastDate + ", " + staleDays + " days ago.",
                    "Recent transactions are missing from the forecast.",
                    "Upload a statement that includes the last few days."));
        }

        // ---- forecast confidence ----
        if (forecast.confidence() < 0.5) {
            list.add(new Recommendation("Treat the forecast as a rough guide", Priority.MEDIUM,
                    "Forecast confidence is " + Math.round(forecast.confidence() * 100) + "%.",
                    "There is limited history, few regular payments, or the data is old.",
                    "Upload at least 3 months of statements to improve accuracy."));
        }

        // ---- unusual transactions (not a fraud finding) ----
        Transaction unusual = findUnusualDebit(txns);
        if (unusual != null) {
            list.add(new Recommendation("Review the unusual transaction", Priority.MEDIUM,
                    "A payment of " + MoneyUtil.inr(unusual.getDebit()) + " on " + unusual.getTxnDate() + " ('"
                            + unusual.getDescription() + "') is much larger than your usual payments.",
                    "Review recommended; this is not a fraud determination.",
                    "Confirm that you recognise this payment and that it is recorded correctly."));
        }

        if (list.isEmpty()) {
            list.add(new Recommendation("Your cash position looks stable", Priority.LOW,
                    "No cash shortage is expected in the next 30 days.",
                    "Stress level is " + stress.stressLevel() + " and the forecast stays above the safety level.",
                    "Keep uploading a fresh statement every month to keep the forecast accurate."));
        }

        list.sort(Comparator.comparingInt(r -> r.priority().ordinal()));
        return list;
    }

    private static BigDecimal balanceOn(ForecastResponse forecast, LocalDate date) {
        for (ForecastDay d : forecast.days()) {
            if (d.date().equals(date)) {
                return d.expectedBalance();
            }
        }
        return null;
    }

    /** A debit above mean + 3 standard deviations AND above 3x the median (needs 8+ debits). */
    Transaction findUnusualDebit(List<Transaction> txns) {
        List<Transaction> debits = new ArrayList<>();
        for (Transaction t : txns) {
            if (t.isDebit()) {
                debits.add(t);
            }
        }
        if (debits.size() < 8) {
            return null;
        }
        double sum = 0;
        for (Transaction t : debits) {
            sum += t.getDebit().doubleValue();
        }
        double mean = sum / debits.size();
        double sq = 0;
        for (Transaction t : debits) {
            double diff = t.getDebit().doubleValue() - mean;
            sq += diff * diff;
        }
        double std = Math.sqrt(sq / debits.size());
        List<Double> sorted = new ArrayList<>();
        for (Transaction t : debits) {
            sorted.add(t.getDebit().doubleValue());
        }
        sorted.sort(Double::compare);
        double median = sorted.get(sorted.size() / 2);
        Transaction best = null;
        for (Transaction t : debits) {
            double v = t.getDebit().doubleValue();
            if (v > mean + 3 * std && v > 3 * median && (best == null || t.getDebit().compareTo(best.getDebit()) > 0)) {
                best = t;
            }
        }
        return best;
    }
}
