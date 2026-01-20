package com.hsbc.fraud.engine;

import com.hsbc.fraud.model.*;
import com.hsbc.fraud.rules.FraudRule;
import com.hsbc.fraud.rules.RuleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fraud Detection Engine Tests")
class FraudDetectionEngineTest {

    private FraudDetectionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FraudDetectionEngine(0.4);
    }

    @Test
    @DisplayName("Should detect high value fraudulent transaction")
    void shouldDetectHighValueTransaction() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX-001")
                .accountId("ACC-001")
                .amount(new BigDecimal("50000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("US")
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        assertThat(alert).isPresent();
        assertThat(alert.get().getAlertType()).isEqualTo(AlertType.HIGH_VALUE_TRANSACTION);
        assertThat(alert.get().getSeverity()).isIn(AlertSeverity.HIGH, AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should detect suspicious account transaction")
    void shouldDetectSuspiciousAccount() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX-002")
                .accountId("SUSP-001")
                .amount(new BigDecimal("100"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("US")
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        assertThat(alert).isPresent();
        assertThat(alert.get().getTriggeredRules()).contains("SUSPICIOUS_ACCOUNT");
    }

    @Test
    @DisplayName("Should detect blacklisted account transaction")
    void shouldDetectBlacklistedAccount() {
        Transaction transaction = Transaction.builder()
                .transactionId("TX-003")
                .accountId("BLACK-001")
                .amount(new BigDecimal("100"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("US")
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        assertThat(alert).isPresent();
        assertThat(alert.get().getSeverity()).isIn(AlertSeverity.HIGH, AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should not generate alert for normal transaction")
    void shouldNotAlertNormalTransaction() {
        // Use a daytime timestamp (10:00 AM UTC) to avoid time-based anomaly detection
        // Time-based anomaly rule checks for transactions between 01:00-05:00
        Instant daytimeTimestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
        Transaction transaction = Transaction.builder()
                .transactionId("TX-004")
                .accountId("ACC-001")
                .amount(new BigDecimal("100"))
                .currency("USD")
                .transactionType(TransactionType.PAYMENT)
                .timestamp(daytimeTimestamp)
                .countryCode("US")
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        assertThat(alert).isEmpty();
    }

    @Test
    @DisplayName("Should handle multiple triggered rules")
    void shouldHandleMultipleTriggeredRules() {
        // Transaction that triggers both high value and suspicious account rules
        Transaction transaction = Transaction.builder()
                .transactionId("TX-005")
                .accountId("SUSP-001")
                .amount(new BigDecimal("60000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("US")
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        assertThat(alert).isPresent();
        assertThat(alert.get().getTriggeredRules()).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("Should calculate combined risk score correctly")
    void shouldCalculateCombinedRiskScore() {
        // Multiple violations should result in higher combined score
        Transaction transaction = Transaction.builder()
                .transactionId("TX-006")
                .accountId("BLACK-001")
                .amount(new BigDecimal("100000"))
                .currency("USD")
                .transactionType(TransactionType.WIRE_TRANSFER)
                .timestamp(Instant.now())
                .countryCode("XX") // High risk country
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        assertThat(alert).isPresent();
        assertThat(alert.get().getRiskScore()).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("Should handle null transaction")
    void shouldHandleNullTransaction() {
        Optional<FraudAlert> alert = engine.evaluate(null);

        assertThat(alert).isEmpty();
    }

    @Test
    @DisplayName("Should allow adding custom rules")
    void shouldAllowAddingCustomRules() {
        // Create a custom rule
        FraudRule customRule = new FraudRule() {
            @Override
            public String getRuleName() {
                return "CUSTOM_TEST_RULE";
            }

            @Override
            public String getDescription() {
                return "Custom test rule";
            }

            @Override
            public Optional<RuleResult> evaluate(Transaction transaction) {
                if (transaction.getMerchantId() != null && 
                    transaction.getMerchantId().startsWith("BANNED-")) {
                    return Optional.of(new RuleResult(
                            getRuleName(),
                            AlertType.BLACKLISTED_MERCHANT,
                            0.8,
                            "Banned merchant detected"
                    ));
                }
                return Optional.empty();
            }
        };

        engine.addRule(customRule);

        Transaction transaction = Transaction.builder()
                .transactionId("TX-007")
                .accountId("ACC-001")
                .merchantId("BANNED-MERCH-001")
                .amount(new BigDecimal("100"))
                .currency("USD")
                .transactionType(TransactionType.PAYMENT)
                .timestamp(Instant.now())
                .countryCode("US")
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        assertThat(alert).isPresent();
        assertThat(alert.get().getTriggeredRules()).contains("CUSTOM_TEST_RULE");
    }

    @Test
    @DisplayName("Should allow removing rules")
    void shouldAllowRemovingRules() {
        boolean removed = engine.removeRule("HIGH_VALUE_TRANSACTION");
        assertThat(removed).isTrue();

        // High value transaction should no longer trigger
        Transaction transaction = Transaction.builder()
                .transactionId("TX-008")
                .accountId("ACC-001")
                .amount(new BigDecimal("100000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("US")
                .build();

        Optional<FraudAlert> alert = engine.evaluate(transaction);

        // Should not be triggered by high value rule (might still trigger other rules)
        if (alert.isPresent()) {
            assertThat(alert.get().getTriggeredRules()).doesNotContain("HIGH_VALUE_TRANSACTION");
        }
    }

    @Test
    @DisplayName("Should return correct alert severity based on risk score")
    void shouldReturnCorrectSeverity() {
        // Critical severity for blacklisted account
        Transaction criticalTx = Transaction.builder()
                .transactionId("TX-009")
                .accountId("BLACK-001")
                .amount(new BigDecimal("100000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("ZZ") // Blocked country
                .build();

        Optional<FraudAlert> alert = engine.evaluate(criticalTx);

        assertThat(alert).isPresent();
        assertThat(alert.get().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should have all default rules initialized")
    void shouldHaveDefaultRulesInitialized() {
        assertThat(engine.getRules()).hasSizeGreaterThanOrEqualTo(5);
    }
}

