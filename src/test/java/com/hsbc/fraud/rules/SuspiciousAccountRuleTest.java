package com.hsbc.fraud.rules;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import com.hsbc.fraud.rules.impl.SuspiciousAccountRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Suspicious Account Rule Tests")
class SuspiciousAccountRuleTest {

    private SuspiciousAccountRule rule;

    @BeforeEach
    void setUp() {
        Set<String> suspiciousAccounts = new HashSet<>();
        suspiciousAccounts.add("SUSP-001");
        suspiciousAccounts.add("SUSP-002");

        Set<String> blacklistedAccounts = new HashSet<>();
        blacklistedAccounts.add("BLACK-001");
        blacklistedAccounts.add("BLACK-002");

        rule = new SuspiciousAccountRule(suspiciousAccounts, blacklistedAccounts);
    }

    @Test
    @DisplayName("Should not trigger for normal accounts")
    void shouldNotTriggerForNormalAccounts() {
        Transaction transaction = createTransaction("ACC-001", "ACC-002");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should trigger for suspicious source account")
    void shouldTriggerForSuspiciousSourceAccount() {
        Transaction transaction = createTransaction("SUSP-001", "ACC-002");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo(AlertType.SUSPICIOUS_ACCOUNT);
        assertThat(result.get().getRiskScore()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("Should trigger for suspicious target account")
    void shouldTriggerForSuspiciousTargetAccount() {
        Transaction transaction = createTransaction("ACC-001", "SUSP-002");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo(AlertType.SUSPICIOUS_ACCOUNT);
        assertThat(result.get().getRiskScore()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("Should trigger with higher risk for blacklisted source account")
    void shouldTriggerHighRiskForBlacklistedSourceAccount() {
        Transaction transaction = createTransaction("BLACK-001", "ACC-002");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getRiskScore()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Should trigger with higher risk for blacklisted target account")
    void shouldTriggerHighRiskForBlacklistedTargetAccount() {
        Transaction transaction = createTransaction("ACC-001", "BLACK-002");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().getRiskScore()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Should prioritize blacklisted over suspicious")
    void shouldPrioritizeBlacklistedOverSuspicious() {
        // Source is blacklisted, target is suspicious
        Transaction transaction = createTransaction("BLACK-001", "SUSP-001");

        Optional<RuleResult> result = rule.evaluate(transaction);

        assertThat(result).isPresent();
        // Should return blacklist result (higher risk)
        assertThat(result.get().getRiskScore()).isEqualTo(0.95);
        assertThat(result.get().getReason()).contains("BLACKLISTED");
    }

    @Test
    @DisplayName("Should handle null transaction gracefully")
    void shouldHandleNullTransaction() {
        Optional<RuleResult> result = rule.evaluate(null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should allow adding suspicious accounts dynamically")
    void shouldAllowAddingSuspiciousAccounts() {
        rule.addSuspiciousAccount("NEW-SUSP");
        
        assertThat(rule.isSuspicious("NEW-SUSP")).isTrue();
        
        Transaction transaction = createTransaction("NEW-SUSP", "ACC-002");
        Optional<RuleResult> result = rule.evaluate(transaction);
        
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("Should allow removing suspicious accounts")
    void shouldAllowRemovingSuspiciousAccounts() {
        rule.removeSuspiciousAccount("SUSP-001");
        
        assertThat(rule.isSuspicious("SUSP-001")).isFalse();
        
        Transaction transaction = createTransaction("SUSP-001", "ACC-002");
        Optional<RuleResult> result = rule.evaluate(transaction);
        
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return correct rule name")
    void shouldReturnCorrectRuleName() {
        assertThat(rule.getRuleName()).isEqualTo("SUSPICIOUS_ACCOUNT");
    }

    private Transaction createTransaction(String sourceAccount, String targetAccount) {
        return Transaction.builder()
                .transactionId("TX-TEST-001")
                .accountId(sourceAccount)
                .targetAccountId(targetAccount)
                .amount(new BigDecimal("1000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(Instant.now())
                .countryCode("US")
                .channel("ONLINE")
                .build();
    }
}

