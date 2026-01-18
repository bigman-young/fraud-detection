package com.hsbc.fraud.rules.impl;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import com.hsbc.fraud.rules.FraudRule;
import com.hsbc.fraud.rules.RuleResult;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Rule to detect suspicious cross-border wire transfers.
 * Applies stricter thresholds for international transactions.
 */
public class CrossBorderTransactionRule implements FraudRule {

    private static final long serialVersionUID = 1L;

    private final String homeCountryCode;
    private final BigDecimal crossBorderThreshold;

    public CrossBorderTransactionRule() {
        this("US", new BigDecimal("5000"));
    }

    public CrossBorderTransactionRule(String homeCountryCode, BigDecimal crossBorderThreshold) {
        this.homeCountryCode = homeCountryCode;
        this.crossBorderThreshold = crossBorderThreshold;
    }

    @Override
    public String getRuleName() {
        return "CROSS_BORDER_TRANSACTION";
    }

    @Override
    public String getDescription() {
        return "Detects suspicious cross-border transactions exceeding threshold";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction transaction) {
        if (transaction == null) {
            return Optional.empty();
        }

        // Only apply to wire transfers
        if (transaction.getTransactionType() != TransactionType.WIRE_TRANSFER &&
            transaction.getTransactionType() != TransactionType.TRANSFER) {
            return Optional.empty();
        }

        String countryCode = transaction.getCountryCode();
        if (countryCode == null || countryCode.equalsIgnoreCase(homeCountryCode)) {
            return Optional.empty();
        }

        BigDecimal amount = transaction.getAmount();
        if (amount == null) {
            return Optional.empty();
        }

        if (amount.compareTo(crossBorderThreshold) >= 0) {
            double riskScore = 0.5 + (amount.divide(crossBorderThreshold.multiply(new BigDecimal("4")), 
                    2, java.math.RoundingMode.HALF_UP)
                    .min(new BigDecimal("0.45"))
                    .doubleValue());

            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.CROSS_BORDER_SUSPICIOUS,
                    riskScore,
                    String.format("Cross-border %s to %s for %s %s exceeds threshold of %s",
                            transaction.getTransactionType(),
                            countryCode,
                            amount,
                            transaction.getCurrency(),
                            crossBorderThreshold)
            ));
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return 70;
    }

    public String getHomeCountryCode() {
        return homeCountryCode;
    }

    public BigDecimal getCrossBorderThreshold() {
        return crossBorderThreshold;
    }
}

