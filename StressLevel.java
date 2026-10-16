package com.msme.cashflowguard.model;

/** Guide mapping: 70-100 = LOW, 40-69 = MEDIUM, 0-39 = HIGH. */
public enum StressLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static StressLevel fromScore(int score) {
        if (score >= 70) {
            return LOW;
        }
        if (score >= 40) {
            return MEDIUM;
        }
        return HIGH;
    }
}
