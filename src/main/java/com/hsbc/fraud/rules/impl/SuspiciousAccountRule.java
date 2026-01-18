package com.hsbc.fraud.rules.impl;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.rules.FraudRule;
import com.hsbc.fraud.rules.RuleResult;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Rule to detect transactions involving suspicious/blacklisted accounts.
 * Maintains a set of suspicious account IDs that are flagged.
 */
public class SuspiciousAccountRule implements FraudRule {

    private static final long serialVersionUID = 1L;

    private final Set<String> suspiciousAccounts;
    private final Set<String> blacklistedAccounts;

    public SuspiciousAccountRule() {
        this.suspiciousAccounts = new HashSet<>();
        this.blacklistedAccounts = new HashSet<>();
        initializeDefaultLists();
    }

    public SuspiciousAccountRule(Set<String> suspiciousAccounts, Set<String> blacklistedAccounts) {
        this.suspiciousAccounts = new HashSet<>(suspiciousAccounts);
        this.blacklistedAccounts = new HashSet<>(blacklistedAccounts);
    }

    private void initializeDefaultLists() {
        // Sample suspicious accounts for demonstration
        suspiciousAccounts.add("SUSP-001");
        suspiciousAccounts.add("SUSP-002");
        suspiciousAccounts.add("WATCH-001");
        
        // Sample blacklisted accounts
        blacklistedAccounts.add("BLACK-001");
        blacklistedAccounts.add("BLACK-002");
        blacklistedAccounts.add("FRAUD-001");
    }

    @Override
    public String getRuleName() {
        return "SUSPICIOUS_ACCOUNT";
    }

    @Override
    public String getDescription() {
        return "Detects transactions involving suspicious or blacklisted accounts";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction transaction) {
        if (transaction == null) {
            return Optional.empty();
        }

        String sourceAccount = transaction.getAccountId();
        String targetAccount = transaction.getTargetAccountId();

        // Check blacklisted accounts first (highest risk)
        if (isBlacklisted(sourceAccount)) {
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.SUSPICIOUS_ACCOUNT,
                    0.95,
                    String.format("Source account %s is BLACKLISTED", sourceAccount)
            ));
        }

        if (isBlacklisted(targetAccount)) {
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.SUSPICIOUS_ACCOUNT,
                    0.95,
                    String.format("Target account %s is BLACKLISTED", targetAccount)
            ));
        }

        // Check suspicious accounts
        if (isSuspicious(sourceAccount)) {
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.SUSPICIOUS_ACCOUNT,
                    0.7,
                    String.format("Source account %s is flagged as SUSPICIOUS", sourceAccount)
            ));
        }

        if (isSuspicious(targetAccount)) {
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.SUSPICIOUS_ACCOUNT,
                    0.7,
                    String.format("Target account %s is flagged as SUSPICIOUS", targetAccount)
            ));
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return 90;
    }

    public boolean isSuspicious(String accountId) {
        return accountId != null && suspiciousAccounts.contains(accountId);
    }

    public boolean isBlacklisted(String accountId) {
        return accountId != null && blacklistedAccounts.contains(accountId);
    }

    public void addSuspiciousAccount(String accountId) {
        suspiciousAccounts.add(accountId);
    }

    public void addBlacklistedAccount(String accountId) {
        blacklistedAccounts.add(accountId);
    }

    public void removeSuspiciousAccount(String accountId) {
        suspiciousAccounts.remove(accountId);
    }

    public void removeBlacklistedAccount(String accountId) {
        blacklistedAccounts.remove(accountId);
    }
}

