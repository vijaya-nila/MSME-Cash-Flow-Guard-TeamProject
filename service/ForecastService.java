package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.ForecastDay;
import com.msme.cashflowguard.dto.ForecastResponse;
import com.msme.cashflowguard.entity.BusinessDetail;
import com.msme.cashflowguard.entity.RecurringTransaction;
import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.entity.UpcomingPayment;
import com.msme.cashflowguard.exception.BadRequestException;
import com.msme.cashflowguard.model.CustomerPaymentOverride;
import com.msme.cashflowguard.model.FlowDirection;
import com.msme.cashflowguard.model.StatementData;
import com.msme.cashflowguard.model.TransactionCategory;
import com.msme.cashflowguard.util.CashFlowMath;
import com.msme.cashflowguard.util.MoneyUtil;
import com.msme.cashflowguard.util.PartyNameExtractor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Stage 8: 30-day cash-flow forecast.
 *
 * Projected Balance = Previous Balance + Expected Inflows - Expected Outflows   (for every calendar day)
 *
 * Expected flows = detected recurring payments on their expected dates
 *                + the historical daily average of everything that is NOT recurring.
 * Three scenarios (conservative / expected / optimistic) change those amounts by fixed factors.
 */
@Service
public class ForecastService {

    public static final int FORECAST_DAYS = 30;

    static final BigDecimal CONS_REC_IN = new BigDecimal("0.85");
    static final BigDecimal CONS_IRR_IN = new BigDecimal("0.70");
    static final BigDecimal CONS_REC_OUT = new BigDecimal("1.05");
    static final BigDecimal CONS_IRR_OUT = new BigDecimal("1.15");
    static final BigDecimal OPT_IRR_IN = new BigDecimal("1.15");
    static final BigDecimal OPT_IRR_OUT = new BigDecimal("0.90");

    private final StatementDataService dataService;
    private final Clock clock;

    public ForecastService(StatementDataService dataService, Clock clock) {
        this.dataService = dataService;
        this.clock = clock;
    }

    public ForecastResponse getForecast(Long userId, Long statementId) {
        StatementData data = dataService.load(userId, statementId);
        return compute(data, LocalDate.now(clock), null);
    }

    public LocalDate forecastStart(List<Transaction> txns, LocalDate today) {
        return CashFlowMath.forecastStart(txns.get(txns.size() - 1).getTxnDate(), today);
    }

    /** {@code override} (nullable) pins one customer's payment to a given date (what-if simulator). */
    public ForecastResponse compute(StatementData data, LocalDate today, CustomerPaymentOverride override) {
        List<Transaction> txns = data.transactions();
        if (txns.isEmpty()) {
            throw new BadRequestException("This statement has no transactions to forecast from.");
        }
        Transaction lastTxn = txns.get(txns.size() - 1);
        LocalDate lastDate = lastTxn.getTxnDate();
        LocalDate start = forecastStart(txns, today);
        LocalDate end = start.plusDays(FORECAST_DAYS - 1L);
        long historyDays = CashFlowMath.historyDays(txns);
        BigDecimal avgDailyOutflow = CashFlowMath.averageDailyOutflow(txns);
        BigDecimal buffer = CashFlowMath.safetyBuffer(avgDailyOutflow);

        BusinessDetail biz = data.business();
        BigDecimal cashInHand = biz != null ? MoneyUtil.nz(biz.getCashInHand()) : BigDecimal.ZERO;
        BigDecimal opening = MoneyUtil.scale2(lastTxn.getBalance().add(cashInHand));

        BigDecimal[] recIn = zeros();
        BigDecimal[] recOut = zeros();
        BigDecimal[] extraOut = zeros();

        int recurringUsed = 0;
        for (RecurringTransaction r : data.recurring()) {
            boolean inflow = r.getDirection() == FlowDirection.INFLOW;
            if (override != null && inflow && PartyNameExtractor.key(r.getDescription())
                    .equals(PartyNameExtractor.key(override.customerName()))) {
                continue;   // replaced by the simulator's own payment below
            }
            recurringUsed++;
            BigDecimal[] target = inflow ? recIn : recOut;
            for (LocalDate d : r.getFrequency().occurrences(r.getLastOccurrenceDate(), start, end)) {
                int idx = (int) ChronoUnit.DAYS.between(start, d);
                target[idx] = target[idx].add(r.getAverageAmount());
            }
        }

        if (override != null && !override.paymentDate().isBefore(start) && !override.paymentDate().isAfter(end)) {
            int idx = (int) ChronoUnit.DAYS.between(start, override.paymentDate());
            recIn[idx] = recIn[idx].add(override.amount());
        }

        List<String> businessNotes = new ArrayList<>();
        addBusinessExtras(data, biz, start, end, extraOut, businessNotes);

        // Non-recurring money in / out, spread evenly over the history
        BigDecimal irregIn = BigDecimal.ZERO;
        BigDecimal irregOut = BigDecimal.ZERO;
        String overrideKey = override == null ? null : PartyNameExtractor.key(override.customerName());
        for (Transaction t : txns) {
            if (t.isRecurring()) {
                continue;
            }
            if (t.isCredit()) {
                if (t.getCategory() == TransactionCategory.OWNER_TRANSFER) {
                    continue;   // owner top-ups are not business income
                }
                if (overrideKey != null
                        && overrideKey.equals(PartyNameExtractor.key(PartyNameExtractor.extract(t.getDescription())))) {
                    continue;
                }
                irregIn = irregIn.add(t.getCredit());
            } else if (t.isDebit()) {
                irregOut = irregOut.add(t.getDebit());
            }
        }
        BigDecimal days = BigDecimal.valueOf(historyDays);
        BigDecimal irrInDaily = irregIn.divide(days, 4, RoundingMode.HALF_UP);
        BigDecimal irrOutDaily = irregOut.divide(days, 4, RoundingMode.HALF_UP);

        List<ForecastDay> out = new ArrayList<>();
        BigDecimal cons = opening;
        BigDecimal exp = opening;
        BigDecimal opt = opening;
        int shortageDays = 0;
        BigDecimal minExp = null;
        LocalDate minExpDate = null;
        BigDecimal minCons = null;

        for (int i = 0; i < FORECAST_DAYS; i++) {
            LocalDate date = start.plusDays(i);
            BigDecimal in = recIn[i];
            BigDecimal outflow = recOut[i].add(extraOut[i]);

            BigDecimal expIn = MoneyUtil.scale2(in.add(irrInDaily));
            BigDecimal expOut = MoneyUtil.scale2(outflow.add(irrOutDaily));
            BigDecimal consIn = MoneyUtil.scale2(in.multiply(CONS_REC_IN).add(irrInDaily.multiply(CONS_IRR_IN)));
            BigDecimal consOut = MoneyUtil.scale2(outflow.multiply(CONS_REC_OUT).add(irrOutDaily.multiply(CONS_IRR_OUT)));
            BigDecimal optIn = MoneyUtil.scale2(in.add(irrInDaily.multiply(OPT_IRR_IN)));
            BigDecimal optOut = MoneyUtil.scale2(outflow.add(irrOutDaily.multiply(OPT_IRR_OUT)));

            cons = cons.add(consIn).subtract(consOut);
            exp = exp.add(expIn).subtract(expOut);
            opt = opt.add(optIn).subtract(optOut);

            boolean risk = exp.compareTo(buffer) < 0;
            if (risk) {
                shortageDays++;
            }
            if (minExp == null || exp.compareTo(minExp) < 0) {
                minExp = exp;
                minExpDate = date;
            }
            if (minCons == null || cons.compareTo(minCons) < 0) {
                minCons = cons;
            }
            out.add(new ForecastDay(date, expIn, expOut, cons, exp, opt, risk));
        }

        long staleDays = Math.max(0, ChronoUnit.DAYS.between(lastDate, today));
        double confidence = confidence(data, historyDays, staleDays);

        List<String> assumptions = new ArrayList<>();
        assumptions.add("Projected Balance = Previous Balance + Expected Inflows - Expected Outflows, worked out for each of the "
                + FORECAST_DAYS + " days from " + start + " to " + end + ".");
        assumptions.add("The starting balance is the latest balance in your statement (" + MoneyUtil.inr(lastTxn.getBalance())
                + " on " + lastDate + ")."
                + (cashInHand.signum() > 0 ? " Your cash in hand of " + MoneyUtil.inr(cashInHand) + " is added." : ""));
        if (recurringUsed > 0) {
            assumptions.add(recurringUsed + " recurring payment(s) found in your statement are placed on their usual dates "
                    + "using their average amounts.");
        } else {
            assumptions.add("No recurring payments were detected, so the forecast relies only on your average daily money in and out.");
        }
        assumptions.add("Other (non-recurring) money is assumed to continue at your historical daily average: about "
                + MoneyUtil.inr(irrInDaily.setScale(2, RoundingMode.HALF_UP)) + " in and "
                + MoneyUtil.inr(irrOutDaily.setScale(2, RoundingMode.HALF_UP)) + " out per day.");
        assumptions.add("Conservative scenario: recurring receipts 15% lower, other receipts 30% lower, expenses 5-15% higher. "
                + "Optimistic scenario: other receipts 15% higher and other expenses 10% lower.");
        assumptions.add("A day is flagged as a possible shortage when the expected balance is below " + MoneyUtil.inr(buffer)
                + " (about " + CashFlowMath.SAFETY_DAYS_OF_COVER + " days of your average spending).");
        assumptions.addAll(businessNotes);
        if (staleDays > 7) {
            assumptions.add("Your statement ends on " + lastDate + ", " + staleDays
                    + " days ago, so this forecast is less reliable. Upload a newer statement.");
        }
        if (historyDays < ValidationService.RECOMMENDED_HISTORY_DAYS) {
            assumptions.add("Only " + historyDays + " days of history were available, so patterns are uncertain.");
        }
        assumptions.add("This is an estimate, not a guarantee.");

        return new ForecastResponse(
                data.statement() == null ? null : data.statement().getId(),
                start, end, opening, buffer, out,
                MoneyUtil.scale2(minExp), minExpDate, MoneyUtil.scale2(minCons),
                shortageDays, confidence, assumptions);
    }

    private void addBusinessExtras(StatementData data, BusinessDetail biz, LocalDate start, LocalDate end,
                                   BigDecimal[] extraOut, List<String> notes) {
        if (biz == null) {
            return;
        }
        int added = 0;
        if (biz.getUpcomingPayments() != null) {
            for (UpcomingPayment p : biz.getUpcomingPayments()) {
                LocalDate due = p.getDueDate();
                int idx;
                if (due == null) {
                    idx = 14;
                } else if (due.isBefore(start) || due.isAfter(end)) {
                    continue;
                } else {
                    idx = (int) ChronoUnit.DAYS.between(start, due);
                }
                extraOut[idx] = extraOut[idx].add(p.getAmount());
                added++;
            }
        }
        if (added > 0) {
            notes.add(added + " upcoming payment(s) you entered are included (payments with no due date are placed mid-forecast).");
        }

        BigDecimal rent = MoneyUtil.nz(biz.getMonthlyRent());
        boolean rentSeen = data.recurring().stream().anyMatch(
                r -> r.getCategory() == TransactionCategory.RENT && r.getDirection() == FlowDirection.OUTFLOW);
        if (rent.signum() > 0 && !rentSeen) {
            for (int i = 0; i < FORECAST_DAYS; i++) {
                if (start.plusDays(i).getDayOfMonth() == 1) {
                    extraOut[i] = extraOut[i].add(rent);
                    notes.add("Monthly rent of " + MoneyUtil.inr(rent)
                            + " (not visible in your statement) is placed on the 1st of the month.");
                }
            }
        }

        BigDecimal other = MoneyUtil.nz(biz.getOtherFixedExpenses());
        if (other.signum() > 0) {
            double seen = 0;
            for (RecurringTransaction r : data.recurring()) {
                if (r.getDirection() == FlowDirection.OUTFLOW && (r.getCategory() == TransactionCategory.SALARY
                        || r.getCategory() == TransactionCategory.EMI || r.getCategory() == TransactionCategory.UTILITY)) {
                    seen += r.getAverageAmount().doubleValue() * r.getFrequency().timesPerMonth();
                }
            }
            BigDecimal shortfall = other.subtract(BigDecimal.valueOf(seen)).setScale(2, RoundingMode.HALF_UP);
            if (shortfall.signum() > 0) {
                extraOut[14] = extraOut[14].add(shortfall);
                notes.add("Fixed expenses you entered are " + MoneyUtil.inr(shortfall)
                        + " higher than the ones visible in your statement; that difference is placed mid-forecast.");
            }
        }
    }

    /** Never above 90%: a forecast is never certain. */
    double confidence(StatementData data, long historyDays, long staleDays) {
        double c = 0.40;
        if (historyDays >= 180) {
            c += 0.25;
        } else if (historyDays >= 90) {
            c += 0.20;
        } else if (historyDays >= 60) {
            c += 0.12;
        } else if (historyDays >= 30) {
            c += 0.05;
        }
        int recurring = data.recurring().size();
        if (recurring >= 3) {
            c += 0.12;
        } else if (recurring > 0) {
            c += 0.06;
        }
        int quality = data.statement() == null ? 70 : data.statement().getDataQualityScore();
        if (quality >= 90) {
            c += 0.10;
        } else if (quality >= 75) {
            c += 0.05;
        } else if (quality < 50) {
            c -= 0.05;
        }
        int n = data.transactions().size();
        if (n >= 60) {
            c += 0.08;
        } else if (n >= 20) {
            c += 0.04;
        }
        if (staleDays > 90) {
            c -= 0.30;
        } else if (staleDays > 30) {
            c -= 0.15;
        } else if (staleDays > 7) {
            c -= 0.05;
        }
        c = Math.max(0.20, Math.min(0.90, c));
        return Math.round(c * 100.0) / 100.0;
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] arr = new BigDecimal[FORECAST_DAYS];
        Arrays.fill(arr, BigDecimal.ZERO);
        return arr;
    }
}
