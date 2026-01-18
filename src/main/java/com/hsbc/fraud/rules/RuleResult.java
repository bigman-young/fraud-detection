package com.hsbc.fraud.rules;

import com.hsbc.fraud.model.AlertType;

import java.io.Serializable;

/**
 * Result of a fraud rule evaluation.
 */
public class RuleResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String ruleName;
    private final AlertType alertType;
    private final double riskScore;
    private final String reason;

    public RuleResult(String ruleName, AlertType alertType, double riskScore, String reason) {
        this.ruleName = ruleName;
        this.alertType = alertType;
        this.riskScore = Math.min(1.0, Math.max(0.0, riskScore)); // Clamp to [0, 1]
        this.reason = reason;
    }

    public String getRuleName() {
        return ruleName;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "RuleResult{" +
                "ruleName='" + ruleName + '\'' +
                ", alertType=" + alertType +
                ", riskScore=" + riskScore +
                ", reason='" + reason + '\'' +
                '}';
    }
}

