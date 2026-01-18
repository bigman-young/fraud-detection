package com.hsbc.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a fraud alert generated when suspicious activity is detected.
 * Contains detailed information about the fraud detection result.
 */
public class FraudAlert implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("transaction")
    private Transaction transaction;

    @JsonProperty("alert_type")
    private AlertType alertType;

    @JsonProperty("severity")
    private AlertSeverity severity;

    @JsonProperty("risk_score")
    private double riskScore;

    @JsonProperty("triggered_rules")
    private List<String> triggeredRules;

    @JsonProperty("description")
    private String description;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty("status")
    private AlertStatus status;

    public FraudAlert() {
        this.alertId = UUID.randomUUID().toString();
        this.triggeredRules = new ArrayList<>();
        this.createdAt = Instant.now();
        this.status = AlertStatus.NEW;
    }

    public FraudAlert(Transaction transaction, AlertType alertType, AlertSeverity severity, 
                      double riskScore, String description) {
        this();
        this.transaction = transaction;
        this.alertType = alertType;
        this.severity = severity;
        this.riskScore = riskScore;
        this.description = description;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public List<String> getTriggeredRules() {
        return triggeredRules;
    }

    public void setTriggeredRules(List<String> triggeredRules) {
        this.triggeredRules = triggeredRules;
    }

    public void addTriggeredRule(String rule) {
        this.triggeredRules.add(rule);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FraudAlert that = (FraudAlert) o;
        return Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId);
    }

    @Override
    public String toString() {
        return "FraudAlert{" +
                "alertId='" + alertId + '\'' +
                ", transactionId='" + (transaction != null ? transaction.getTransactionId() : "null") + '\'' +
                ", alertType=" + alertType +
                ", severity=" + severity +
                ", riskScore=" + riskScore +
                ", triggeredRules=" + triggeredRules +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", status=" + status +
                '}';
    }

    // Builder class
    public static class Builder {
        private final FraudAlert alert = new FraudAlert();

        public Builder transaction(Transaction transaction) {
            alert.setTransaction(transaction);
            return this;
        }

        public Builder alertType(AlertType alertType) {
            alert.setAlertType(alertType);
            return this;
        }

        public Builder severity(AlertSeverity severity) {
            alert.setSeverity(severity);
            return this;
        }

        public Builder riskScore(double riskScore) {
            alert.setRiskScore(riskScore);
            return this;
        }

        public Builder triggeredRules(List<String> rules) {
            alert.setTriggeredRules(rules);
            return this;
        }

        public Builder addTriggeredRule(String rule) {
            alert.addTriggeredRule(rule);
            return this;
        }

        public Builder description(String description) {
            alert.setDescription(description);
            return this;
        }

        public Builder status(AlertStatus status) {
            alert.setStatus(status);
            return this;
        }

        public FraudAlert build() {
            return alert;
        }
    }
}

