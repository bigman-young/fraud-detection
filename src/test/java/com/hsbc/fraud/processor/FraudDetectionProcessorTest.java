package com.hsbc.fraud.processor;

import com.hsbc.fraud.model.FraudAlert;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import org.apache.flink.api.common.functions.util.ListCollector;
import org.apache.flink.configuration.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fraud Detection Processor Tests")
class FraudDetectionProcessorTest {

    private FraudDetectionProcessor processor;
    private List<FraudAlert> collectedAlerts;
    private ListCollector<FraudAlert> collector;

    @BeforeEach
    void setUp() throws Exception {
        processor = new FraudDetectionProcessor(0.4);
        processor.open(new Configuration());
        collectedAlerts = new ArrayList<>();
        collector = new ListCollector<>(collectedAlerts);
    }

    @AfterEach
    void tearDown() throws Exception {
        processor.close();
    }

    @Test
    @DisplayName("Should generate alert for high value transaction")
    void shouldGenerateAlertForHighValueTransaction() throws Exception {
        // Use a daytime timestamp (10:00 AM UTC) for consistent test results
        Instant daytimeTimestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
        
        Transaction transaction = Transaction.builder()
                .transactionId("TX-001")
                .accountId("ACC-001")
                .amount(new BigDecimal("50000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(daytimeTimestamp)
                .countryCode("US")
                .build();

        processor.flatMap(transaction, collector);

        assertThat(collectedAlerts).hasSize(1);
        assertThat(collectedAlerts.get(0).getTransaction().getTransactionId()).isEqualTo("TX-001");
    }

    @Test
    @DisplayName("Should not generate alert for normal transaction")
    void shouldNotGenerateAlertForNormalTransaction() throws Exception {
        // Use a daytime timestamp (10:00 AM UTC) to avoid time-based anomaly detection
        Instant daytimeTimestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
        
        Transaction transaction = Transaction.builder()
                .transactionId("TX-002")
                .accountId("ACC-001")
                .amount(new BigDecimal("100"))
                .currency("USD")
                .transactionType(TransactionType.PAYMENT)
                .timestamp(daytimeTimestamp)
                .countryCode("US")
                .build();

        processor.flatMap(transaction, collector);

        assertThat(collectedAlerts).isEmpty();
    }

    @Test
    @DisplayName("Should handle null transaction gracefully")
    void shouldHandleNullTransaction() throws Exception {
        processor.flatMap(null, collector);

        assertThat(collectedAlerts).isEmpty();
    }

    @Test
    @DisplayName("Should generate alert for suspicious account")
    void shouldGenerateAlertForSuspiciousAccount() throws Exception {
        // Use a daytime timestamp (10:00 AM UTC) for consistent test results
        Instant daytimeTimestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
        
        Transaction transaction = Transaction.builder()
                .transactionId("TX-003")
                .accountId("BLACK-001")
                .amount(new BigDecimal("100"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(daytimeTimestamp)
                .countryCode("US")
                .build();

        processor.flatMap(transaction, collector);

        assertThat(collectedAlerts).hasSize(1);
    }

    @Test
    @DisplayName("Should generate alert for blocked country")
    void shouldGenerateAlertForBlockedCountry() throws Exception {
        // Use a daytime timestamp (10:00 AM UTC) for consistent test results
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
                .transactionType(TransactionType.TRANSFER)
                .timestamp(daytimeTimestamp)
                .countryCode("ZZ") // Blocked country
                .build();

        processor.flatMap(transaction, collector);

        assertThat(collectedAlerts).hasSize(1);
    }

    @Test
    @DisplayName("Should process multiple transactions")
    void shouldProcessMultipleTransactions() throws Exception {
        // Use a daytime timestamp (10:00 AM UTC) to avoid time-based anomaly detection
        Instant daytimeTimestamp = ZonedDateTime.now(ZoneId.of("UTC"))
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();
        
        // Normal transaction
        processor.flatMap(Transaction.builder()
                .transactionId("TX-005")
                .accountId("ACC-001")
                .amount(new BigDecimal("100"))
                .currency("USD")
                .transactionType(TransactionType.PAYMENT)
                .timestamp(daytimeTimestamp)
                .countryCode("US")
                .build(), collector);

        // Fraudulent transaction
        processor.flatMap(Transaction.builder()
                .transactionId("TX-006")
                .accountId("BLACK-001")
                .amount(new BigDecimal("50000"))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .timestamp(daytimeTimestamp)
                .countryCode("ZZ")
                .build(), collector);

        // Normal transaction
        processor.flatMap(Transaction.builder()
                .transactionId("TX-007")
                .accountId("ACC-002")
                .amount(new BigDecimal("200"))
                .currency("EUR")
                .transactionType(TransactionType.PAYMENT)
                .timestamp(daytimeTimestamp)
                .countryCode("DE")
                .build(), collector);

        // Only the fraudulent transaction should generate alert
        assertThat(collectedAlerts).hasSize(1);
        assertThat(collectedAlerts.get(0).getTransaction().getTransactionId()).isEqualTo("TX-006");
    }
}

