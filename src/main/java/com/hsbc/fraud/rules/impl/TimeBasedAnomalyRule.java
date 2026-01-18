package com.hsbc.fraud.rules.impl;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.rules.FraudRule;
import com.hsbc.fraud.rules.RuleResult;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Rule to detect transactions at unusual times (e.g., late night transactions).
 * Typically, legitimate transactions are less common during certain hours.
 */
public class TimeBasedAnomalyRule implements FraudRule {

    private static final long serialVersionUID = 1L;

    private final LocalTime nightStartTime;
    private final LocalTime nightEndTime;
    private final ZoneId zoneId;

    public TimeBasedAnomalyRule() {
        this(LocalTime.of(1, 0), LocalTime.of(5, 0), ZoneId.of("UTC"));
    }

    public TimeBasedAnomalyRule(LocalTime nightStartTime, LocalTime nightEndTime, ZoneId zoneId) {
        this.nightStartTime = nightStartTime;
        this.nightEndTime = nightEndTime;
        this.zoneId = zoneId;
    }

    @Override
    public String getRuleName() {
        return "TIME_BASED_ANOMALY";
    }

    @Override
    public String getDescription() {
        return "Detects transactions occurring during unusual hours";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction transaction) {
        if (transaction == null || transaction.getTimestamp() == null) {
            return Optional.empty();
        }

        LocalTime transactionTime = LocalTime.from(
                transaction.getTimestamp().atZone(zoneId)
        );

        if (isWithinNightHours(transactionTime)) {
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.TIME_BASED_ANOMALY,
                    0.4,
                    String.format("Transaction at unusual time: %s (Night hours: %s - %s)",
                            transactionTime, nightStartTime, nightEndTime)
            ));
        }

        return Optional.empty();
    }

    private boolean isWithinNightHours(LocalTime time) {
        if (nightStartTime.isBefore(nightEndTime)) {
            // Normal case: e.g., 1:00 AM to 5:00 AM
            return !time.isBefore(nightStartTime) && time.isBefore(nightEndTime);
        } else {
            // Crosses midnight: e.g., 11:00 PM to 5:00 AM
            return !time.isBefore(nightStartTime) || time.isBefore(nightEndTime);
        }
    }

    @Override
    public int getPriority() {
        return 30;
    }

    public LocalTime getNightStartTime() {
        return nightStartTime;
    }

    public LocalTime getNightEndTime() {
        return nightEndTime;
    }
}

