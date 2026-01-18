package com.hsbc.fraud.rules;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import com.hsbc.fraud.rules.impl.GeographicAnomalyRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Geographic Anomaly Rule Tests")
class GeographicAnomalyRuleTest {

    private GeographicAnomalyRule rule;

    @BeforeEach
    void setUp() {
        Set<String> highRiskCountries = new HashSet<>();
        highRiskCountries.add("XX");
        highRiskCountries.add("YY");

        Set<String> blockedCountries = new HashSet<>();
        blockedCountries.add("ZZ");

        rule = new GeographicAnomalyRule(highRiskCountries, blockedCountries);
    }

    @Test
    @DisplayName("Should not trigger for normal countries")
    void shouldNotTriggerForNormalCountries() {
        Transaction transaction = createTransaction("US");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should trigger for high-risk country")
    void shouldTriggerForHighRiskCountry() {
        Transaction transaction = createTransaction("XX");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo(AlertType.GEOGRAPHIC_ANOMALY);
        assertThat(result.get().getRiskScore()).isEqualTo(0.6);
    }

    @Test
    @DisplayName("Should trigger with higher risk for blocked country")
    void shouldTriggerHighRiskForBlockedCountry() {
        Transaction transaction = createTransaction("ZZ");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getRiskScore()).isEqualTo(0.98);
        assertThat(result.get().getReason()).contains("BLOCKED");
    }

    @Test
    @DisplayName("Should handle case-insensitive country codes")
    void shouldHandleCaseInsensitiveCountryCodes() {
        Transaction transaction = createTransaction("xx");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Should handle null country code")
    void shouldHandleNullCountryCode() {
        Transaction transaction = createTransaction(null);

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should allow adding high-risk countries dynamically")
    void shouldAllowAddingHighRiskCountries() {
        rule.addHighRiskCountry("NEW");
        
        assertThat(rule.isHighRisk("NEW")).isTrue();
    }

    @Test
    @DisplayName("Should return correct rule name")
    void shouldReturnCorrectRuleName() {
        assertThat(rule.getRuleName()).isEqualTo("GEOGRAPHIC_ANOMALY");
    }

    private Transaction createTransaction(String countryCode) {
        return Transaction.builder()
                .transactionId("TX-TEST-001")
                .accountId("ACC-001")
                .targetAccountId("ACC-002")
                .amount(new BigDecimal("1000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode(countryCode)
                .channel("ONLINE")
                .build();
    }
}

