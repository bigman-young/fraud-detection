package com.hsbc.fraud.rules.impl;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.rules.FraudRule;
import com.hsbc.fraud.rules.RuleResult;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Rule to detect high-value transactions that exceed a configurable threshold.
 * Different thresholds can be set for different transaction types.
 */
public class HighValueTransactionRule implements FraudRule {

    private static final long serialVersionUID = 1L;

    private final BigDecimal threshold;
    private final BigDecimal criticalThreshold;

    public HighValueTransactionRule() {
        this(new BigDecimal("10000"), new BigDecimal("50000"));
    }

    public HighValueTransactionRule(BigDecimal threshold, BigDecimal criticalThreshold) {
        this.threshold = threshold;
        this.criticalThreshold = criticalThreshold;
    }

    @Override
    public String getRuleName() {
        return "HIGH_VALUE_TRANSACTION";
    }

    @Override
    public String getDescription() {
        return "Detects transactions exceeding the configured amount threshold";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction transaction) {
        if (transaction == null || transaction.getAmount() == null) {
            return Optional.empty();
        }

        BigDecimal amount = transaction.getAmount();
        
        if (amount.compareTo(criticalThreshold) >= 0) {
            double riskScore = 0.9 + (amount.subtract(criticalThreshold)
                    .divide(criticalThreshold, 2, java.math.RoundingMode.HALF_UP)
                    .min(new BigDecimal("0.1"))
                    .doubleValue());
            
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.HIGH_VALUE_TRANSACTION,
                    Math.min(1.0, riskScore),
                    String.format("Critical: Transaction amount %s %s exceeds critical threshold of %s",
                            amount, transaction.getCurrency(), criticalThreshold)
            ));
        }

        if (amount.compareTo(threshold) >= 0) {
            // Calculate risk score based on how much it exceeds the threshold
            double exceedRatio = amount.subtract(threshold)
                    .divide(criticalThreshold.subtract(threshold), 2, java.math.RoundingMode.HALF_UP)
                    .doubleValue();
            double riskScore = 0.5 + (exceedRatio * 0.4);

            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.HIGH_VALUE_TRANSACTION,
                    riskScore,
                    String.format("Transaction amount %s %s exceeds threshold of %s",
                            amount, transaction.getCurrency(), threshold)
            ));
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return 100;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public BigDecimal getCriticalThreshold() {
        return criticalThreshold;
    }
}

