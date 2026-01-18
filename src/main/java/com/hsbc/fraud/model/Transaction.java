package com.hsbc.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a financial transaction in the fraud detection system.
 * This is the core data model that flows through the Flink pipeline.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("target_account_id")
    private String targetAccountId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("transaction_type")
    private TransactionType transactionType;

    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("location")
    private String location;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("channel")
    private String channel; // ONLINE, ATM, POS, MOBILE

    // Default constructor for Jackson
    public Transaction() {}

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(String targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", targetAccountId='" + targetAccountId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", transactionType=" + transactionType +
                ", timestamp=" + timestamp +
                ", merchantId='" + merchantId + '\'' +
                ", location='" + location + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", channel='" + channel + '\'' +
                '}';
    }

    // Builder class
    public static class Builder {
        private final Transaction transaction = new Transaction();

        public Builder transactionId(String transactionId) {
            transaction.setTransactionId(transactionId);
            return this;
        }

        public Builder accountId(String accountId) {
            transaction.setAccountId(accountId);
            return this;
        }

        public Builder targetAccountId(String targetAccountId) {
            transaction.setTargetAccountId(targetAccountId);
            return this;
        }

        public Builder amount(BigDecimal amount) {
            transaction.setAmount(amount);
            return this;
        }

        public Builder currency(String currency) {
            transaction.setCurrency(currency);
            return this;
        }

        public Builder transactionType(TransactionType type) {
            transaction.setTransactionType(type);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            transaction.setTimestamp(timestamp);
            return this;
        }

        public Builder merchantId(String merchantId) {
            transaction.setMerchantId(merchantId);
            return this;
        }

        public Builder location(String location) {
            transaction.setLocation(location);
            return this;
        }

        public Builder countryCode(String countryCode) {
            transaction.setCountryCode(countryCode);
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            transaction.setIpAddress(ipAddress);
            return this;
        }

        public Builder deviceId(String deviceId) {
            transaction.setDeviceId(deviceId);
            return this;
        }

        public Builder channel(String channel) {
            transaction.setChannel(channel);
            return this;
        }

        public Transaction build() {
            return transaction;
        }
    }
}

