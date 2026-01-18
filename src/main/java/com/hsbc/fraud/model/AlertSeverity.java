package com.hsbc.fraud.model;

/**
 * Severity levels for fraud alerts.
 */
public enum AlertSeverity {
    LOW(1),      // Minor anomaly, low risk
    MEDIUM(2),   // Moderate risk, needs review
    HIGH(3),     // High risk, immediate attention needed
    CRITICAL(4); // Critical risk, immediate action required

    private final int level;

    AlertSeverity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static AlertSeverity fromRiskScore(double riskScore) {
        if (riskScore >= 0.9) return CRITICAL;
        if (riskScore >= 0.7) return HIGH;
        if (riskScore >= 0.4) return MEDIUM;
        return LOW;
    }
}

