package com.msme.cashflowguard.service;

import com.msme.cashflowguard.dto.CashFlowSummary;
import com.msme.cashflowguard.dto.CategoryTotal;
import com.msme.cashflowguard.dto.CustomerInflow;
import com.msme.cashflowguard.dto.MonthlyTotal;
import com.msme.cashflowguard.dto.RecurringTransactionDto;
import com.msme.cashflowguard.entity.RecurringTransaction;
import com.msme.cashflowguard.entity.Transaction;
import com.msme.cashflowguard.model.StatementData;
import com.msme.cashflowguard.model.TransactionCategory;
import com.msme.cashflowguard.util.CashFlowMath;
import com.msme.cashflowguard.util.MoneyUtil;
import com.msme.cashflowguard.util.PartyNameExtractor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Stage 6: historical cash-flow analysis. All money maths uses BigDecimal. */
@Service
public class CashFlowAnalysisService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final int TOP_N = 8;

    private final StatementDataService dataService;
    private final Clock clock;

    public CashFlowAnalysisService(StatementDataService dataService, Clock clock) {
        this.dataService = dataService;
        this.clock = clock;
    }

    public CashFlowSummary getDashboard(Long userId, Long statementId) {
        return summarize(dataService.load(userId, statementId), LocalDate.now(clock));
    }

    public CashFlowSummary summarize(StatementData data, LocalDate today) {
        List<Transaction> txns = data.transactions();
        Transaction firstTxn = txns.get(0);
        Transaction lastTxn = txns.get(txns.size() - 1);

        BigDecimal inflows = CashFlowMath.totalInflows(txns);
        BigDecimal outflows = CashFlowMath.totalOutflows(txns);
        long historyDays = CashFlowMath.historyDays(txns);
        BigDecimal avgDailyOutflow = CashFlowMath.averageDailyOutflow(txns);
        BigDecimal threshold = CashFlowMath.safetyBuffer(avgDailyOutflow);

        NavigableMap<LocalDate, BigDecimal> daily = CashFlowMath.dailyClosingBalances(txns);
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        for (BigDecimal b : daily.values()) {
            sum = sum.add(b);
            min = (min == null || b.compareTo(min) < 0) ? b : min;
            max = (max == null || b.compareTo(max) > 0) ? b : max;
        }
        BigDecimal avgBalance = sum.divide(BigDecimal.valueOf(daily.size()), 2, RoundingMode.HALF_UP);

        LocalDate asOf = CashFlowMath.forecastStart(lastTxn.getTxnDate(), today);
        List<RecurringTransactionDto> recurring = new ArrayList<>();
        for (RecurringTransaction r : data.recurring()) {
            recurring.add(RecurringTransactionDto.from(r, asOf));
        }
        recurring.sort(Comparator.comparing(RecurringTransactionDto::expectedDate));

        List<CustomerInflow> customers = customerInflows(txns);
        List<CustomerInflow> topCustomers = customers.subList(0, Math.min(TOP_N, customers.size()));

        return new CashFlowSummary(
                data.statement().getId(),
                data.statement().getFilename(),
                data.statement().getUploadedAt(),
                firstTxn.getTxnDate(),
                lastTxn.getTxnDate(),
                historyDays,
                lastTxn.getBalance(),
                inflows,
                outflows,
                MoneyUtil.scale2(inflows.subtract(outflows)),
                avgBalance,
                MoneyUtil.scale2(min),
                MoneyUtil.scale2(max),
                avgDailyOutflow,
                threshold,
                CashFlowMath.lowBalanceDays(daily, threshold),
                monthlyTotals(txns),
                topCustomers,
                topExpenseCategories(txns),
                recurring);
    }

    /** Inflow and outflow per calendar month, oldest first. */
    public List<MonthlyTotal> monthlyTotals(List<Transaction> txns) {
        TreeMap<YearMonth, BigDecimal[]> months = new TreeMap<>();
        for (Transaction t : txns) {
            BigDecimal[] pair = months.computeIfAbsent(YearMonth.from(t.getTxnDate()),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            pair[0] = pair[0].add(MoneyUtil.nz(t.getCredit()));
            pair[1] = pair[1].add(MoneyUtil.nz(t.getDebit()));
        }
        List<MonthlyTotal> result = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal[]> e : months.entrySet()) {
            BigDecimal in = MoneyUtil.scale2(e.getValue()[0]);
            BigDecimal out = MoneyUtil.scale2(e.getValue()[1]);
            result.add(new MonthlyTotal(e.getKey().format(MONTH_LABEL), in, out, MoneyUtil.scale2(in.subtract(out))));
        }
        return result;
    }

    /** Customers (credits classified as customer payments) ranked by total received. */
    public List<CustomerInflow> customerInflows(List<Transaction> txns) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        for (Transaction t : txns) {
            if (t.getCategory() != TransactionCategory.CUSTOMER_PAYMENT || !t.isCredit()) {
                continue;
            }
            String name = PartyNameExtractor.extract(t.getDescription());
            String key = PartyNameExtractor.key(name);
            totals.merge(key, t.getCredit(), BigDecimal::add);
            counts.merge(key, 1, Integer::sum);
            display.putIfAbsent(key, name);
        }
        List<CustomerInflow> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            list.add(new CustomerInflow(display.get(e.getKey()), MoneyUtil.scale2(e.getValue()), counts.get(e.getKey())));
        }
        list.sort(Comparator.comparing(CustomerInflow::amount).reversed());
        return list;
    }

    /** Money paid out grouped by category, largest first. */
    public List<CategoryTotal> topExpenseCategories(List<Transaction> txns) {
        Map<TransactionCategory, BigDecimal> totals = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, Integer> counts = new EnumMap<>(TransactionCategory.class);
        for (Transaction t : txns) {
            if (!t.isDebit()) {
                continue;
            }
            TransactionCategory c = t.getCategory() == null ? TransactionCategory.UNKNOWN : t.getCategory();
            totals.merge(c, t.getDebit(), BigDecimal::add);
            counts.merge(c, 1, Integer::sum);
        }
        List<CategoryTotal> list = new ArrayList<>();
        for (Map.Entry<TransactionCategory, BigDecimal> e : totals.entrySet()) {
            list.add(new CategoryTotal(e.getKey().name(), MoneyUtil.scale2(e.getValue()), counts.get(e.getKey())));
        }
        list.sort(Comparator.comparing(CategoryTotal::amount).reversed());
        return new ArrayList<>(list.subList(0, Math.min(TOP_N, list.size())));
    }
}
