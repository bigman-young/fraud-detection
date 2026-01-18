package com.hsbc.fraud.rules;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import com.hsbc.fraud.rules.impl.HighValueTransactionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("High Value Transaction Rule Tests")
class HighValueTransactionRuleTest {

    private HighValueTransactionRule rule;

    @BeforeEach
    void setUp() {
        rule = new HighValueTransactionRule(
                new BigDecimal("10000"),
                new BigDecimal("50000")
        );
    }

    @Test
    @DisplayName("Should not trigger for transactions below threshold")
    void shouldNotTriggerForLowValueTransaction() {
        Transaction transaction = createTransaction(new BigDecimal("5000"));

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should trigger for transactions at threshold")
    void shouldTriggerAtThreshold() {
        Transaction transaction = createTransaction(new BigDecimal("10000"));

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo(AlertType.HIGH_VALUE_TRANSACTION);
        assertThat(result.get().getRiskScore()).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    @DisplayName("Should trigger with higher risk for critical threshold")
    void shouldTriggerHighRiskForCriticalThreshold() {
        Transaction transaction = createTransaction(new BigDecimal("50000"));

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getRiskScore()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    @DisplayName("Should trigger with maximum risk for very high values")
    void shouldTriggerMaxRiskForVeryHighValues() {
        Transaction transaction = createTransaction(new BigDecimal("100000"));

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getRiskScore()).isLessThanOrEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {10001, 25000, 49999})
    @DisplayName("Should calculate proportional risk score between thresholds")
    void shouldCalculateProportionalRiskScore(double amount) {
        Transaction transaction = createTransaction(new BigDecimal(String.valueOf(amount)));

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getRiskScore())
                .isGreaterThan(0.5)
                .isLessThan(0.9);
    }

    @Test
    @DisplayName("Should handle null transaction gracefully")
    void shouldHandleNullTransaction() {
        Optional<RuleResult> result = rule.evaluate(null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle null amount gracefully")
    void shouldHandleNullAmount() {
        Transaction transaction = createTransaction(null);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should include amount in rule result reason")
    void shouldIncludeAmountInReason() {
        Transaction transaction = createTransaction(new BigDecimal("15000"));

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getReason()).contains("15000");
    }

    @Test
    @DisplayName("Should return correct rule name")
    void shouldReturnCorrectRuleName() {
        assertThat(rule.getRuleName()).isEqualTo("HIGH_VALUE_TRANSACTION");
    }

    private Transaction createTransaction(BigDecimal amount) {
        return Transaction.builder()
                .transactionId("TX-TEST-001")
                .accountId("ACC-001")
                .targetAccountId("ACC-002")
                .amount(amount)
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("US")
                .channel("ONLINE")
                .build();
    }
}

