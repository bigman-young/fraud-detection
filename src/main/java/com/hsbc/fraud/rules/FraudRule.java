package com.hsbc.fraud.rules;

import com.hsbc.fraud.model.Transaction;

import java.io.Serializable;
import java.util.Optional;

/**
 * Interface for fraud detection rules.
 * Each rule evaluates a transaction and returns a detection result.
 */
public interface FraudRule extends Serializable {
    
    /**
     * Gets the unique name of this rule.
     * @return the rule name
     */
    String getRuleName();

    /**
     * Gets the description of what this rule detects.
     * @return the rule description
     */
    String getDescription();

    /**
     * Evaluates a transaction against this rule.
     * @param transaction the transaction to evaluate
     * @return Optional containing RuleResult if rule triggered, empty otherwise
     */
    Optional<RuleResult> evaluate(Transaction transaction);

    /**
     * Gets the priority of this rule (higher = more important).
     * @return the priority value
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Checks if this rule is enabled.
     * @return true if enabled
     */
    default boolean isEnabled() {
        return true;
    }
}

