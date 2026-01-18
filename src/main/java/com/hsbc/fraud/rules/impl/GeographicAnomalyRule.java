package com.hsbc.fraud.rules.impl;

import com.hsbc.fraud.model.AlertType;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.rules.FraudRule;
import com.hsbc.fraud.rules.RuleResult;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Rule to detect transactions from high-risk geographic locations.
 * Flags transactions originating from countries with high fraud rates.
 */
public class GeographicAnomalyRule implements FraudRule {

    private static final long serialVersionUID = 1L;

    private final Set<String> highRiskCountries;
    private final Set<String> blockedCountries;

    public GeographicAnomalyRule() {
        this.highRiskCountries = new HashSet<>();
        this.blockedCountries = new HashSet<>();
        initializeDefaultLists();
    }

    public GeographicAnomalyRule(Set<String> highRiskCountries, Set<String> blockedCountries) {
        this.highRiskCountries = new HashSet<>(highRiskCountries);
        this.blockedCountries = new HashSet<>(blockedCountries);
    }

    private void initializeDefaultLists() {
        // Sample high-risk countries (for demonstration only)
        highRiskCountries.add("XX"); // Fictional country code
        highRiskCountries.add("YY");
        
        // Sample blocked countries
        blockedCountries.add("ZZ"); // Fictional blocked country
    }

    @Override
    public String getRuleName() {
        return "GEOGRAPHIC_ANOMALY";
    }

    @Override
    public String getDescription() {
        return "Detects transactions from high-risk or blocked geographic locations";
    }

    @Override
    public Optional<RuleResult> evaluate(Transaction transaction) {
        if (transaction == null || transaction.getCountryCode() == null) {
            return Optional.empty();
        }

        String countryCode = transaction.getCountryCode().toUpperCase();

        // Check blocked countries first
        if (blockedCountries.contains(countryCode)) {
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.GEOGRAPHIC_ANOMALY,
                    0.98,
                    String.format("Transaction from BLOCKED country: %s", countryCode)
            ));
        }

        // Check high-risk countries
        if (highRiskCountries.contains(countryCode)) {
            return Optional.of(new RuleResult(
                    getRuleName(),
                    AlertType.GEOGRAPHIC_ANOMALY,
                    0.6,
                    String.format("Transaction from HIGH-RISK country: %s", countryCode)
            ));
        }

        return Optional.empty();
    }

    @Override
    public int getPriority() {
        return 80;
    }

    public void addHighRiskCountry(String countryCode) {
        highRiskCountries.add(countryCode.toUpperCase());
    }

    public void addBlockedCountry(String countryCode) {
        blockedCountries.add(countryCode.toUpperCase());
    }

    public boolean isHighRisk(String countryCode) {
        return countryCode != null && highRiskCountries.contains(countryCode.toUpperCase());
    }

    public boolean isBlocked(String countryCode) {
        return countryCode != null && blockedCountries.contains(countryCode.toUpperCase());
    }
}

