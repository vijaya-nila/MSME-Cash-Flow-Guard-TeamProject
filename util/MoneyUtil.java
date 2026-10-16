package com.msme.cashflowguard.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small helpers for rupee amounts. All money in this project is BigDecimal (never double). */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    public static BigDecimal scale2(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Formats like Indian rupees: ₹1,25,000 (paise only when present). */
    public static String inr(BigDecimal value) {
        BigDecimal v = scale2(value);
        boolean negative = v.signum() < 0;
        BigDecimal abs = v.abs();
        String plain = abs.toPlainString();
        String intPart = plain.contains(".") ? plain.substring(0, plain.indexOf('.')) : plain;
        String frac = plain.contains(".") ? plain.substring(plain.indexOf('.') + 1) : "00";
        StringBuilder grouped = new StringBuilder();
        int len = intPart.length();
        if (len <= 3) {
            grouped.append(intPart);
        } else {
            String last3 = intPart.substring(len - 3);
            String rest = intPart.substring(0, len - 3);
            StringBuilder restGrouped = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                int fromEnd = rest.length() - i;
                restGrouped.append(rest.charAt(i));
                if (fromEnd > 1 && fromEnd % 2 == 1) {
                    restGrouped.append(',');
                }
            }
            grouped.append(restGrouped).append(',').append(last3);
        }
        String result = "\u20B9" + grouped;
        if (!"00".equals(frac)) {
            result += "." + frac;
        }
        return negative ? "-" + result : result;
    }
}
