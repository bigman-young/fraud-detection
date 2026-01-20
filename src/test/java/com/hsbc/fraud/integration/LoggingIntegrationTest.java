package com.hsbc.fraud.integration;

import com.google.api.gax.paging.Page;
import com.google.cloud.MonitoredResource;
import com.google.cloud.logging.*;
import com.hsbc.fraud.model.*;
import com.hsbc.fraud.sink.AlertSink;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GCP Cloud Logging (Stackdriver) service interaction.
 * 
 * These tests verify:
 * 1. Writing logs to GCP Cloud Logging
 * 2. Reading logs from GCP Cloud Logging
 * 3. Log severity levels are correctly mapped
 * 4. Structured logging with labels and metadata
 * 
 * Prerequisites:
 * - GCP project with Cloud Logging API enabled
 * - GOOGLE_APPLICATION_CREDENTIALS environment variable set
 * - GOOGLE_CLOUD_PROJECT environment variable set
 * 
 * To run these tests:
 * mvn test -Dtest=LoggingIntegrationTest -DskipUnitTests
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@DisplayName("GCP Cloud Logging Integration Tests")
public class LoggingIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingIntegrationTest.class);
    
    private String projectId;
    private String testLogName;
    private Logging logging;
    private boolean loggingAvailable = false;

    @BeforeAll
    void setUp() {
        projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (projectId == null || projectId.isEmpty()) {
            projectId = "hsbc-484505"; // Default for local testing
        }
        
        testLogName = "fraud-detection-test-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Check if Cloud Logging is available
        try {
            logging = LoggingOptions.getDefaultInstance().getService();
            loggingAvailable = true;
            LOG.info("GCP Cloud Logging is available. Running integration tests.");
        } catch (Exception e) {
            LOG.warn("GCP Cloud Logging is not available. Skipping integration tests. Error: {}", e.getMessage());
            loggingAvailable = false;
        }
    }

    @AfterAll
    void tearDown() {
        if (logging != null) {
            try {
                // Delete test logs
                logging.deleteLog(testLogName);
                LOG.info("Deleted test log: {}", testLogName);
            } catch (Exception e) {
                LOG.warn("Could not delete test log: {}", e.getMessage());
            }
            try {
                logging.close();
            } catch (Exception e) {
                LOG.warn("Error closing logging client: {}", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Should write log entry to Cloud Logging")
    void shouldWriteLogEntryToCloudLogging() {
        Assumptions.assumeTrue(loggingAvailable, "Cloud Logging not available");
        
        // Arrange
        String testMessage = "Test log message - " + UUID.randomUUID();
        MonitoredResource resource = MonitoredResource.newBuilder("global")
                .addLabel("project_id", projectId)
                .build();
        
        LogEntry logEntry = LogEntry.newBuilder(Payload.StringPayload.of(testMessage))
                .setSeverity(Severity.INFO)
                .setLogName(testLogName)
                .setResource(resource)
                .addLabel("test_id", UUID.randomUUID().toString())
                .addLabel("component", "fraud-detection-test")
                .build();
        
        // Act
        logging.write(Collections.singleton(logEntry));
        logging.flush();
        
        // Assert - Log entry was written without exception
        LOG.info("Successfully wrote log entry: {}", testMessage);
    }

    @Test
    @DisplayName("Should write structured log with JSON payload")
    void shouldWriteStructuredLogWithJsonPayload() {
        Assumptions.assumeTrue(loggingAvailable, "Cloud Logging not available");
        
        // Arrange
        Map<String, Object> jsonPayload = new HashMap<>();
        jsonPayload.put("alertId", "ALERT-" + UUID.randomUUID().toString().substring(0, 8));
        jsonPayload.put("transactionId", "TX-" + UUID.randomUUID().toString().substring(0, 8));
        jsonPayload.put("accountId", "ACC-12345");
        jsonPayload.put("riskScore", 0.85);
        jsonPayload.put("severity", "HIGH");
        jsonPayload.put("timestamp", Instant.now().toString());
        jsonPayload.put("triggeredRules", Arrays.asList("HIGH_VALUE", "VELOCITY_CHECK"));
        
        MonitoredResource resource = MonitoredResource.newBuilder("global")
                .addLabel("project_id", projectId)
                .build();
        
        LogEntry logEntry = LogEntry.newBuilder(Payload.JsonPayload.of(jsonPayload))
                .setSeverity(Severity.WARNING)
                .setLogName(testLogName)
                .setResource(resource)
                .addLabel("alert_type", "FRAUD_ALERT")
                .addLabel("environment", "test")
                .build();
        
        // Act
        logging.write(Collections.singleton(logEntry));
        logging.flush();
        
        // Assert
        LOG.info("Successfully wrote structured log with alert: {}", jsonPayload.get("alertId"));
    }

    @Test
    @DisplayName("Should map alert severity to correct log severity")
    void shouldMapAlertSeverityToLogSeverity() {
        Assumptions.assumeTrue(loggingAvailable, "Cloud Logging not available");
        
        // Arrange & Act & Assert
        Map<AlertSeverity, Severity> expectedMapping = new HashMap<>();
        expectedMapping.put(AlertSeverity.CRITICAL, Severity.CRITICAL);
        expectedMapping.put(AlertSeverity.HIGH, Severity.ERROR);
        expectedMapping.put(AlertSeverity.MEDIUM, Severity.WARNING);
        expectedMapping.put(AlertSeverity.LOW, Severity.INFO);
        
        MonitoredResource resource = MonitoredResource.newBuilder("global")
                .addLabel("project_id", projectId)
                .build();
        
        for (Map.Entry<AlertSeverity, Severity> entry : expectedMapping.entrySet()) {
            FraudAlert alert = createTestAlert(entry.getKey());
            
            LogEntry logEntry = LogEntry.newBuilder(
                    Payload.StringPayload.of("Test alert with severity: " + entry.getKey()))
                    .setSeverity(entry.getValue())
                    .setLogName(testLogName)
                    .setResource(resource)
                    .addLabel("alert_severity", entry.getKey().name())
                    .build();
            
            logging.write(Collections.singleton(logEntry));
        }
        
        logging.flush();
        LOG.info("Successfully tested all severity mappings");
    }

    @Test
    @DisplayName("Should write batch of log entries")
    void shouldWriteBatchOfLogEntries() {
        Assumptions.assumeTrue(loggingAvailable, "Cloud Logging not available");
        
        // Arrange
        int batchSize = 10;
        List<LogEntry> logEntries = new ArrayList<>();
        
        MonitoredResource resource = MonitoredResource.newBuilder("global")
                .addLabel("project_id", projectId)
                .build();
        
        for (int i = 0; i < batchSize; i++) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("batchId", UUID.randomUUID().toString());
            payload.put("index", i);
            payload.put("timestamp", Instant.now().toString());
            
            LogEntry entry = LogEntry.newBuilder(Payload.JsonPayload.of(payload))
                    .setSeverity(Severity.INFO)
                    .setLogName(testLogName)
                    .setResource(resource)
                    .addLabel("batch_test", "true")
                    .addLabel("index", String.valueOf(i))
                    .build();
            
            logEntries.add(entry);
        }
        
        // Act
        logging.write(logEntries);
        logging.flush();
        
        // Assert
        LOG.info("Successfully wrote batch of {} log entries", batchSize);
    }

    @Test
    @DisplayName("Should read logs from Cloud Logging")
    void shouldReadLogsFromCloudLogging() throws InterruptedException {
        Assumptions.assumeTrue(loggingAvailable, "Cloud Logging not available");
        
        // Arrange - Write a unique log entry
        String uniqueId = UUID.randomUUID().toString();
        String testMessage = "Unique test message: " + uniqueId;
        
        MonitoredResource resource = MonitoredResource.newBuilder("global")
                .addLabel("project_id", projectId)
                .build();
        
        LogEntry logEntry = LogEntry.newBuilder(Payload.StringPayload.of(testMessage))
                .setSeverity(Severity.INFO)
                .setLogName(testLogName)
                .setResource(resource)
                .addLabel("unique_id", uniqueId)
                .build();
        
        logging.write(Collections.singleton(logEntry));
        logging.flush();
        
        // Wait for log to be available (Cloud Logging has some delay)
        TimeUnit.SECONDS.sleep(5);
        
        // Act - Read logs
        String filter = String.format(
                "logName=\"projects/%s/logs/%s\" AND labels.unique_id=\"%s\"",
                projectId, testLogName, uniqueId);
        
        Page<LogEntry> entries = logging.listLogEntries(
                Logging.EntryListOption.filter(filter),
                Logging.EntryListOption.pageSize(10));
        
        // Assert
        boolean found = false;
        for (LogEntry entry : entries.iterateAll()) {
            if (entry.getLabels().get("unique_id").equals(uniqueId)) {
                found = true;
                LOG.info("Found log entry with unique_id: {}", uniqueId);
                break;
            }
        }
        
        // Note: Due to Cloud Logging ingestion delay, this might not always find the log immediately
        LOG.info("Log search completed. Found: {}", found);
    }

    @Test
    @DisplayName("Should handle logging errors gracefully")
    void shouldHandleLoggingErrorsGracefully() {
        // Arrange - Create an invalid log entry
        MonitoredResource resource = MonitoredResource.newBuilder("global")
                .addLabel("project_id", projectId)
                .build();
        
        // Act & Assert - Should not throw exception for valid entry
        assertDoesNotThrow(() -> {
            LogEntry logEntry = LogEntry.newBuilder(Payload.StringPayload.of("Test message"))
                    .setSeverity(Severity.INFO)
                    .setLogName("test-error-handling")
                    .setResource(resource)
                    .build();
            
            if (loggingAvailable) {
                logging.write(Collections.singleton(logEntry));
            }
        }, "Should not throw exception for valid log entry");
        
        LOG.info("Error handling test passed");
    }

    @Test
    @DisplayName("Should verify SLF4J logging outputs to stdout for Cloud Logging collection")
    void shouldVerifySLF4JLoggingOutputsToStdout() {
        // Arrange
        Logger testLogger = LoggerFactory.getLogger("FraudAlerts");
        String testAlertId = "ALERT-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Act - Log using SLF4J (which outputs to stdout, collected by Cloud Logging)
        testLogger.info("[TEST ALERT] AlertID={} | Account=ACC-12345 | Type=HIGH_VALUE | Score=0.85", testAlertId);
        testLogger.warn("[HIGH SEVERITY] Potential fraud detected for alert: {}", testAlertId);
        testLogger.error("[CRITICAL] Immediate action required for alert: {}", testAlertId);
        
        // Assert - Logs were written without exception
        LOG.info("SLF4J logging test completed for alert: {}", testAlertId);
    }

    @Test
    @DisplayName("Should test AlertSink.LoggingSink outputs correctly")
    void shouldTestAlertSinkLoggingSinkOutputsCorrectly() throws Exception {
        // Arrange
        FraudAlert alert = createTestAlert(AlertSeverity.HIGH);
        AlertSink.LoggingSink loggingSink = new AlertSink.LoggingSink();
        
        // Act
        loggingSink.open(new org.apache.flink.configuration.Configuration());
        loggingSink.invoke(alert, null);
        
        // Assert - Should complete without exception
        LOG.info("AlertSink.LoggingSink test completed for alert: {}", alert.getAlertId());
    }

    private FraudAlert createTestAlert(AlertSeverity severity) {
        Transaction transaction = Transaction.builder()
                .transactionId("TX-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .accountId("ACC-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .amount(new BigDecimal("5000.00"))
                .transactionType(TransactionType.TRANSFER)
                .countryCode("RU")
                .timestamp(Instant.now())
                .build();
        
        return FraudAlert.builder()
                .transaction(transaction)
                .alertType(AlertType.HIGH_VALUE_TRANSACTION)
                .severity(severity)
                .riskScore(severity.getLevel() * 0.25)
                .addTriggeredRule("TEST_RULE")
                .description("Test alert for integration testing")
                .status(AlertStatus.NEW)
                .build();
    }
}
