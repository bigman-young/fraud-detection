package com.hsbc.fraud.engine;

import com.hsbc.fraud.model.*;
import com.hsbc.fraud.rules.FraudRule;
import com.hsbc.fraud.rules.RuleResult;
import com.hsbc.fraud.rules.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core fraud detection engine that evaluates transactions against all registered rules.
 * Aggregates results and produces fraud alerts when violations are detected.
 */
public class FraudDetectionEngine implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionEngine.class);

    private final List<FraudRule> rules;
    private final double alertThreshold;

    /**
     * Creates a new fraud detection engine with default rules.
     */
    public FraudDetectionEngine() {
        this(0.4); // Default alert threshold
    }

    /**
     * Creates a new fraud detection engine with specified alert threshold.
     * @param alertThreshold minimum risk score to generate an alert
     */
    public FraudDetectionEngine(double alertThreshold) {
        this.alertThreshold = alertThreshold;
        this.rules = new ArrayList<>();
        initializeDefaultRules();
    }

    /**
     * Initializes the engine with default fraud detection rules.
     */
    private void initializeDefaultRules() {
        rules.add(new HighValueTransactionRule());
        rules.add(new SuspiciousAccountRule());
        rules.add(new GeographicAnomalyRule());
        rules.add(new TimeBasedAnomalyRule());
        rules.add(new CrossBorderTransactionRule());
        
        // Sort rules by priority (highest first)
        rules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        
        LOG.info("Fraud detection engine initialized with {} rules", rules.size());
    }

    /**
     * Adds a new rule to the engine.
     * @param rule the rule to add
     */
    public void addRule(FraudRule rule) {
        rules.add(rule);
        rules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        LOG.info("Added rule: {}", rule.getRuleName());
    }

    /**
     * Removes a rule from the engine by name.
     * @param ruleName the name of the rule to remove
     * @return true if the rule was removed
     */
    public boolean removeRule(String ruleName) {
        return rules.removeIf(r -> r.getRuleName().equals(ruleName));
    }

    /**
     * Evaluates a transaction against all rules and generates an alert if suspicious.
     * @param transaction the transaction to evaluate
     * @return Optional containing FraudAlert if fraud detected, empty otherwise
     */
    public Optional<FraudAlert> evaluate(Transaction transaction) {
        if (transaction == null) {
            LOG.warn("Received null transaction, skipping evaluation");
            return Optional.empty();
        }

        LOG.debug("Evaluating transaction: {}", transaction.getTransactionId());

        List<RuleResult> triggeredResults = new ArrayList<>();

        // Evaluate all enabled rules
        for (FraudRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }

            try {
                Optional<RuleResult> result = rule.evaluate(transaction);
                result.ifPresent(triggeredResults::add);
            } catch (Exception e) {
                LOG.error("Error evaluating rule {}: {}", rule.getRuleName(), e.getMessage());
            }
        }

        // If no rules triggered, no alert
        if (triggeredResults.isEmpty()) {
            LOG.debug("No fraud rules triggered for transaction: {}", transaction.getTransactionId());
            return Optional.empty();
        }

        // Calculate combined risk score
        double combinedRiskScore = calculateCombinedRiskScore(triggeredResults);

        // Check if combined score exceeds threshold
        if (combinedRiskScore < alertThreshold) {
            LOG.debug("Combined risk score {} below threshold {} for transaction: {}",
                    combinedRiskScore, alertThreshold, transaction.getTransactionId());
            return Optional.empty();
        }

        // Generate fraud alert
        FraudAlert alert = createAlert(transaction, triggeredResults, combinedRiskScore);
        
        LOG.warn("FRAUD ALERT: {} - Transaction: {}, Risk Score: {:.2f}, Triggered Rules: {}",
                alert.getAlertId(),
                transaction.getTransactionId(),
                combinedRiskScore,
                alert.getTriggeredRules());

        return Optional.of(alert);
    }

    /**
     * Calculates combined risk score using weighted average of individual scores.
     * Higher priority rules contribute more to the final score.
     */
    private double calculateCombinedRiskScore(List<RuleResult> results) {
        if (results.isEmpty()) {
            return 0.0;
        }

        // Use maximum score as the base with weighted contributions
        double maxScore = results.stream()
                .mapToDouble(RuleResult::getRiskScore)
                .max()
                .orElse(0.0);

        // Add contribution from other triggered rules
        double additionalScore = results.stream()
                .mapToDouble(r -> r.getRiskScore() * 0.1)
                .sum() - (maxScore * 0.1);

        return Math.min(1.0, maxScore + additionalScore);
    }

    /**
     * Creates a fraud alert from the evaluation results.
     */
    private FraudAlert createAlert(Transaction transaction, List<RuleResult> results, double combinedScore) {
        // Determine primary alert type (from highest risk rule)
        AlertType primaryAlertType = results.stream()
                .max(Comparator.comparingDouble(RuleResult::getRiskScore))
                .map(RuleResult::getAlertType)
                .orElse(AlertType.UNUSUAL_PATTERN);

        // Build description
        String description = results.stream()
                .map(RuleResult::getReason)
                .collect(Collectors.joining("; "));

        // Get all triggered rule names
        List<String> triggeredRules = results.stream()
                .map(RuleResult::getRuleName)
                .collect(Collectors.toList());

        return FraudAlert.builder()
                .transaction(transaction)
                .alertType(primaryAlertType)
                .severity(AlertSeverity.fromRiskScore(combinedScore))
                .riskScore(combinedScore)
                .triggeredRules(triggeredRules)
                .description(description)
                .status(AlertStatus.NEW)
                .build();
    }

    /**
     * Gets the list of all registered rules.
     * @return unmodifiable list of rules
     */
    public List<FraudRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * Gets the alert threshold.
     * @return the alert threshold
     */
    public double getAlertThreshold() {
        return alertThreshold;
    }
}

