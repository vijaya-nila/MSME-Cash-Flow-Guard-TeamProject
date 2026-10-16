package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.ForecastResponse;
import com.msme.cashflowguard.dto.StressResult;
import com.msme.cashflowguard.dto.WhatIfRequest;
import com.msme.cashflowguard.dto.WhatIfResponse;
import com.msme.cashflowguard.entity.RecurringTransaction;
import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.exception.BadRequestException;
import com.msme.cashflowguard.model.CustomerPaymentOverride;
import com.msme.cashflowguard.model.FlowDirection;
import com.msme.cashflowguard.model.StatementData;
import com.msme.cashflowguard.model.StressLevel;
import com.msme.cashflowguard.util.MoneyUtil;
import com.msme.cashflowguard.util.PartyNameExtractor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Stage 10: "what if this customer pays N days late?" for ANY whole number N from 1 to 30.
 * Runs the forecast twice (payment on its expected date vs shifted by N days),
 * then recalculates minimum balance, shortage-risk days and stress level.
 */
@Service
public class WhatIfSimulationService {

    public static final int MIN_DELAY = 1;
    public static final int MAX_DELAY = 30;

    private final StatementDataService dataService;
    private final ForecastService forecastService;
    private final StressScoreService stressService;
    private final Clock clock;

    public WhatIfSimulationService(StatementDataService dataService, ForecastService forecastService,
                                   StressScoreService stressService, Clock clock) {
        this.dataService = dataService;
        this.forecastService = forecastService;
        this.stressService = stressService;
        this.clock = clock;
    }

    public WhatIfResponse simulate(Long userId, WhatIfRequest request) {
        StatementData data = dataService.load(userId, request.statementId());
        return simulate(data, request, LocalDate.now(clock));
    }

    public WhatIfResponse simulate(StatementData data, WhatIfRequest request, LocalDate today) {
        Integer delay = request.delayDays();
        if (delay == null || delay < MIN_DELAY || delay > MAX_DELAY) {
            throw new BadRequestException("Delay must be a whole number from 1 to 30.");
        }
        if (request.paymentAmount() == null || request.paymentAmount().signum() <= 0) {
            throw new BadRequestException("Enter a payment amount greater than zero.");
        }
        String customer = request.customerName() == null ? "" : request.customerName().trim();
        if (customer.isEmpty()) {
            throw new BadRequestException("Please select a customer.");
        }
        List<Transaction> txns = data.transactions();
        if (txns.isEmpty()) {
            throw new BadRequestException("This statement has no transactions.");
        }

        LocalDate start = forecastService.forecastStart(txns, today);
        String[] basis = new String[1];
        LocalDate original = originalPaymentDate(data, customer, start, basis);
        LocalDate simulated = original.plusDays(delay);
        BigDecimal amount = MoneyUtil.scale2(request.paymentAmount());

        ForecastResponse normal = forecastService.compute(data, today,
                new CustomerPaymentOverride(customer, amount, original));
        ForecastResponse delayed = forecastService.compute(data, today,
                new CustomerPaymentOverride(customer, amount, simulated));
        StressResult normalStress = stressService.score(txns, normal);
        StressResult delayedStress = stressService.score(txns, delayed);

        String recommendation = recommendation(customer, delay, original, simulated, normal, delayed,
                normalStress, delayedStress);

        return new WhatIfResponse(customer, delay, amount, original, simulated,
                normal.minimumExpectedBalance(), delayed.minimumExpectedBalance(),
                normal.shortageRiskDays(), delayed.shortageRiskDays(),
                normalStress.stressLevel(), delayedStress.stressLevel(),
                normalStress.score(), delayedStress.score(),
                basis[0], recommendation);
    }

    /** Expected payment date: recurring pattern first, then payment history, else the first forecast day. */
    LocalDate originalPaymentDate(StatementData data, String customer, LocalDate start, String[] basisOut) {
        String key = PartyNameExtractor.key(customer);
        for (RecurringTransaction r : data.recurring()) {
            if (r.getDirection() == FlowDirection.INFLOW && PartyNameExtractor.key(r.getDescription()).equals(key)) {
                basisOut[0] = "Based on this customer's regular " + r.getFrequency().getLabel().toLowerCase() + " payment pattern.";
                return r.getFrequency().nextOnOrAfter(r.getLastOccurrenceDate(), start);
            }
        }
        TreeSet<LocalDate> dates = new TreeSet<>();
        for (Transaction t : data.transactions()) {
            if (t.isCredit() && PartyNameExtractor.key(PartyNameExtractor.extract(t.getDescription())).equals(key)) {
                dates.add(t.getTxnDate());
            }
        }
        if (dates.size() >= 2) {
            List<Long> gaps = new ArrayList<>();
            LocalDate prev = null;
            for (LocalDate d : dates) {
                if (prev != null) {
                    gaps.add(ChronoUnit.DAYS.between(prev, d));
                }
                prev = d;
            }
            Collections.sort(gaps);
            long gap = Math.max(1, gaps.get(gaps.size() / 2));
            LocalDate next = dates.last().plusDays(gap);
            int guard = 0;
            while (next.isBefore(start) && guard++ < 5000) {
                next = next.plusDays(gap);
            }
            basisOut[0] = "Estimated from the usual gap (" + gap + " days) between this customer's past payments.";
            return next;
        }
        basisOut[0] = "No payment pattern was found for this customer, so the payment is assumed on the first forecast day.";
        return start;
    }

    private String recommendation(String customer, int delay, LocalDate original, LocalDate simulated,
                                  ForecastResponse normal, ForecastResponse delayed,
                                  StressResult normalStress, StressResult delayedStress) {
        int extraShortage = delayed.shortageRiskDays() - normal.shortageRiskDays();
        BigDecimal drop = normal.minimumExpectedBalance().subtract(delayed.minimumExpectedBalance());
        boolean worse = delayedStress.stressLevel().ordinal() > normalStress.stressLevel().ordinal();
        boolean outside = simulated.isAfter(delayed.endDate());
        StringBuilder sb = new StringBuilder();

        if (extraShortage > 0 || worse) {
            sb.append("If ").append(customer).append(" pays ").append(delay).append(delay == 1 ? " day" : " days")
                    .append(" late (").append(simulated).append(" instead of ").append(original).append("), your lowest balance falls by ")
                    .append(MoneyUtil.inr(drop.max(BigDecimal.ZERO))).append(" and ")
                    .append(extraShortage > 0 ? extraShortage + " more day(s) are flagged for possible shortage" : "the risk days stay the same")
                    .append(". Cash stress goes from ").append(normalStress.stressLevel()).append(" to ")
                    .append(delayedStress.stressLevel()).append(". ");
            sb.append("Consider following up with ").append(customer).append(" before ").append(original)
                    .append(" and reviewing which of your own payments could be timed after the money arrives.");
        } else if (drop.signum() > 0 && delayed.minimumExpectedBalance().compareTo(delayed.shortageThreshold()) < 0) {
            sb.append("Your cash is already below the safety level in this forecast, and a ").append(delay)
                    .append("-day delay from ").append(customer).append(" makes the low point ")
                    .append(MoneyUtil.inr(drop)).append(" deeper (").append(MoneyUtil.inr(delayed.minimumExpectedBalance()))
                    .append("). Follow up with ").append(customer).append(" early and review payments you can time after the money arrives.");
        } else if (drop.signum() > 0) {
            sb.append("A ").append(delay).append("-day").append(" delay from ").append(customer)
                    .append(" lowers your lowest balance by ").append(MoneyUtil.inr(drop))
                    .append(", but your cash stays above the safety level and stress stays ")
                    .append(delayedStress.stressLevel()).append(". Keep an eye on this payment.");
        } else {
            sb.append("This delay does not change your lowest balance in the next 30 days, and stress stays ")
                    .append(delayedStress.stressLevel()).append(".");
        }
        if (outside) {
            sb.append(" The delayed payment would arrive after the 30-day forecast window.");
        }
        if (delayedStress.stressLevel() == StressLevel.HIGH && normalStress.stressLevel() != StressLevel.HIGH) {
            sb.append(" Plan for this before it happens.");
        }
        return sb.toString();
    }
}
