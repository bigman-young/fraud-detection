package com.hsbc.fraud.rules;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import com.hsbc.fraud.rules.impl.TimeBasedAnomalyRule;
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

@DisplayName("Time Based Anomaly Rule Tests")
class TimeBasedAnomalyRuleTest {

    private TimeBasedAnomalyRule rule;

    @BeforeEach
    void setUp() {
        rule = new TimeBasedAnomalyRule(
                LocalTime.of(1, 0),
                LocalTime.of(5, 0),
                ZoneId.of("UTC")
        );
    }

    @Test
    @DisplayName("Should not trigger for daytime transaction")
    void shouldNotTriggerForDaytimeTransaction() {
        // Create transaction at 10:00 AM UTC
        Instant timestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(10)
                .withMinute(0)
                .toInstant();
        
        Transaction transaction = createTransaction(timestamp);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should trigger for transaction at 2 AM")
    void shouldTriggerForLateNightTransaction() {
        // Create transaction at 2:00 AM UTC
        Instant timestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(2)
                .withMinute(0)
                .toInstant();
        
        Transaction transaction = createTransaction(timestamp);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo(AlertType.TIME_BASED_ANOMALY);
    }

    @Test
    @DisplayName("Should trigger for transaction at 3 AM")
    void shouldTriggerForEarlyMorningTransaction() {
        // Create transaction at 3:30 AM UTC
        Instant timestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(3)
                .withMinute(30)
                .toInstant();
        
        Transaction transaction = createTransaction(timestamp);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Should not trigger for transaction at boundary")
    void shouldNotTriggerAtEndBoundary() {
        // Create transaction at 5:00 AM UTC (boundary)
        Instant timestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(5)
                .withMinute(0)
                .toInstant();
        
        Transaction transaction = createTransaction(timestamp);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle null timestamp")
    void shouldHandleNullTimestamp() {
        Transaction transaction = createTransaction(null);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should include time in result reason")
    void shouldIncludeTimeInReason() {
        Instant timestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(2)
                .withMinute(30)
                .toInstant();
        
        Transaction transaction = createTransaction(timestamp);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getReason()).contains("unusual time");
    }

    @Test
    @DisplayName("Should return correct rule name")
    void shouldReturnCorrectRuleName() {
        assertThat(rule.getRuleName()).isEqualTo("TIME_BASED_ANOMALY");
    }

    @Test
    @DisplayName("Should have lower priority than other rules")
    void shouldHaveLowerPriority() {
        assertThat(rule.getPriority()).isLessThan(50);
    }

    private Transaction createTransaction(Instant timestamp) {
        return Transaction.builder()
                .transactionId("TX-TEST-001")
                .accountId("ACC-001")
                .targetAccountId("ACC-002")
                .amount(new BigDecimal("1000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(timestamp)
                .countryCode("US")
                .channel("ONLINE")
                .build();
    }
}

