package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.ForecastDay;
import com.msme.cashflowguard.dto.ForecastResponse;
import com.msme.cashflowguard.dto.StressResult;
import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.model.StatementData;
import com.msme.cashflowguard.model.StressLevel;
import com.msme.cashflowguard.util.CashFlowMath;
import com.msme.cashflowguard.util.MoneyUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import org.springframework.stereotype.Service;

/**
 * Stage 9: cash-stress score out of 100 (higher = healthier).
 *   Cash buffer      40 points  - lowest expected balance vs 20 days of normal spending
 *   Low-balance days 30 points  - share of low days in the forecast (70%) and in history (30%)
 *   Balance trend    30 points  - is the balance falling over the next 30 days / in the past?
 * 70-100 = LOW stress, 40-69 = MEDIUM, 0-39 = HIGH.
 */
@Service
public class StressScoreService {

    static final int BUFFER_FULL_DAYS = 20;

    private final StatementDataService dataService;
    private final ForecastService forecastService;
    private final Clock clock;

    public StressScoreService(StatementDataService dataService, ForecastService forecastService, Clock clock) {
        this.dataService = dataService;
        this.forecastService = forecastService;
        this.clock = clock;
    }

    public StressResult getStress(Long userId, Long statementId) {
        StatementData data = dataService.load(userId, statementId);
        ForecastResponse forecast = forecastService.compute(data, LocalDate.now(clock), null);
        return score(data.transactions(), forecast);
    }

    public StressResult score(List<Transaction> txns, ForecastResponse forecast) {
        BigDecimal avgDailyOutflow = CashFlowMath.averageDailyOutflow(txns);
        BigDecimal totalOut = CashFlowMath.totalOutflows(txns);
        BigDecimal net = CashFlowMath.totalInflows(txns).subtract(totalOut);

        // ---- 1. cash buffer (40) ----
        double bufferRatio;
        BigDecimal minBalance = forecast.minimumExpectedBalance();
        if (avgDailyOutflow.signum() <= 0) {
            bufferRatio = minBalance.signum() >= 0 ? 1.0 : 0.0;
        } else {
            BigDecimal full = avgDailyOutflow.multiply(BigDecimal.valueOf(BUFFER_FULL_DAYS));
            bufferRatio = clamp(minBalance.divide(full, 6, RoundingMode.HALF_UP).doubleValue());
        }
        int bufferPoints = (int) Math.round(40 * bufferRatio);

        // ---- 2. low-balance days (30) ----
        int days = forecast.days().size();
        double forecastShare = days == 0 ? 0 : (double) forecast.shortageRiskDays() / days;
        NavigableMap<LocalDate, BigDecimal> daily = CashFlowMath.dailyClosingBalances(txns);
        BigDecimal buffer = CashFlowMath.safetyBuffer(avgDailyOutflow);
        int histLow = CashFlowMath.lowBalanceDays(daily, buffer);
        double histShare = daily.isEmpty() ? 0 : (double) histLow / daily.size();
        double blended = 0.7 * forecastShare + 0.3 * histShare;
        int lowDayPoints = (int) Math.round(30 * clamp(1.0 - blended));

        // ---- 3. balance trend (30) ----
        BigDecimal change = BigDecimal.ZERO;
        if (days > 0) {
            ForecastDay lastDay = forecast.days().get(days - 1);
            change = lastDay.expectedBalance().subtract(forecast.openingBalance());
        }
        double forecastTrend;
        BigDecimal monthSpend = avgDailyOutflow.multiply(BigDecimal.valueOf(30));
        if (change.signum() >= 0) {
            forecastTrend = 1.0;
        } else if (monthSpend.signum() <= 0) {
            forecastTrend = 0.0;
        } else {
            forecastTrend = clamp(1.0 + change.divide(monthSpend, 6, RoundingMode.HALF_UP).doubleValue());
        }
        double historyTrend;
        if (net.signum() >= 0) {
            historyTrend = 1.0;
        } else if (totalOut.signum() <= 0) {
            historyTrend = 0.0;
        } else {
            historyTrend = clamp(1.0 + net.divide(totalOut, 6, RoundingMode.HALF_UP).doubleValue());
        }
        int trendPoints = (int) Math.round(30 * (0.6 * forecastTrend + 0.4 * historyTrend));

        int score = bufferPoints + lowDayPoints + trendPoints;
        StressLevel level = StressLevel.fromScore(score);

        // ---- plain-English explanation ----
        List<String> reasons = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        String coverText = avgDailyOutflow.signum() > 0
                ? ", about " + minBalance.divide(avgDailyOutflow, 0, RoundingMode.HALF_UP).max(BigDecimal.ZERO)
                + " day(s) of your usual spending" : "";
        reasons.add("Cash buffer (" + bufferPoints + "/40): your lowest expected balance in the next 30 days is "
                + MoneyUtil.inr(minBalance) + coverText + ".");
        if (bufferPoints < 24) {
            risks.add("Thin cash buffer");
        }
        reasons.add("Low-balance days (" + lowDayPoints + "/30): the forecast flags " + forecast.shortageRiskDays()
                + " of " + days + " days where cash may run low, and " + histLow + " such day(s) happened in your history.");
        if (lowDayPoints < 18) {
            risks.add("Many days with low cash");
        }
        String trendText = change.signum() >= 0
                ? "your balance is expected to hold or grow by " + MoneyUtil.inr(change)
                : "your balance is expected to fall by " + MoneyUtil.inr(change.negate());
        reasons.add("Balance trend (" + trendPoints + "/30): over the next 30 days " + trendText
                + "; in your past statement money in was " + (net.signum() >= 0 ? "higher" : "lower")
                + " than money out by " + MoneyUtil.inr(net.abs()) + ".");
        if (trendPoints < 18) {
            risks.add("Balance is trending down");
        }
        if (forecast.confidence() < 0.5) {
            risks.add("Low forecast confidence");
            reasons.add("Forecast confidence is low, so treat this score as a rough guide.");
        }

        return new StressResult(score, level, bufferPoints, lowDayPoints, trendPoints, reasons,
                forecast.confidence(), risks);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
