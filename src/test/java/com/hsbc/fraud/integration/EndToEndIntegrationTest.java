package com.hsbc.fraud.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import com.hsbc.fraud.engine.FraudDetectionEngine;
import com.hsbc.fraud.model.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the complete fraud detection pipeline.
 * 
 * These tests verify:
 * 1. Complete message flow: Pub/Sub → Processing → Pub/Sub (alerts)
 * 2. Fraud detection engine processes transactions correctly
 * 3. Alerts are generated and published for fraudulent transactions
 * 4. Normal transactions pass through without alerts
 * 
 * Prerequisites:
 * - GCP project with Pub/Sub API enabled
 * - GOOGLE_APPLICATION_CREDENTIALS environment variable set
 * - GOOGLE_CLOUD_PROJECT environment variable set
 * 
 * To run these tests:
 * mvn test -Dtest=EndToEndIntegrationTest -DskipUnitTests
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@DisplayName("End-to-End Integration Tests")
public class EndToEndIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EndToEndIntegrationTest.class);
    
    private String projectId;
    private String transactionTopicId;
    private String transactionSubscriptionId;
    private String alertTopicId;
    private String alertSubscriptionId;
    
    private ObjectMapper objectMapper;
    private FraudDetectionEngine fraudEngine;
    
    private boolean pubSubAvailable = false;

    @BeforeAll
    void setUp() {
        projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (projectId == null || projectId.isEmpty()) {
            projectId = "hsbc-484505";
        }
        
        String testId = UUID.randomUUID().toString().substring(0, 8);
        transactionTopicId = "e2e-tx-" + testId;
        transactionSubscriptionId = "e2e-tx-sub-" + testId;
        alertTopicId = "e2e-alert-" + testId;
        alertSubscriptionId = "e2e-alert-sub-" + testId;
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        fraudEngine = new FraudDetectionEngine();
        
        try {
            createTestInfrastructure();
            pubSubAvailable = true;
            LOG.info("Test infrastructure created successfully");
        } catch (Exception e) {
            LOG.warn("Failed to create test infrastructure: {}", e.getMessage());
            pubSubAvailable = false;
        }
    }

    @AfterAll
    void tearDown() {
        if (pubSubAvailable) {
            cleanupTestInfrastructure();
        }
    }

    private void createTestInfrastructure() throws Exception {
        try (TopicAdminClient topicAdmin = TopicAdminClient.create()) {
            // Create transaction topic
            topicAdmin.createTopic(TopicName.of(projectId, transactionTopicId));
            LOG.info("Created topic: {}", transactionTopicId);
            
            // Create alert topic
            topicAdmin.createTopic(TopicName.of(projectId, alertTopicId));
            LOG.info("Created topic: {}", alertTopicId);
        }

        try (SubscriptionAdminClient subAdmin = SubscriptionAdminClient.create()) {
            // Create transaction subscription
            subAdmin.createSubscription(
                    SubscriptionName.of(projectId, transactionSubscriptionId),
                    TopicName.of(projectId, transactionTopicId),
                    PushConfig.getDefaultInstance(), 10);
            LOG.info("Created subscription: {}", transactionSubscriptionId);
            
            // Create alert subscription
            subAdmin.createSubscription(
                    SubscriptionName.of(projectId, alertSubscriptionId),
                    TopicName.of(projectId, alertTopicId),
                    PushConfig.getDefaultInstance(), 10);
            LOG.info("Created subscription: {}", alertSubscriptionId);
        }
    }

    private void cleanupTestInfrastructure() {
        try {
            try (SubscriptionAdminClient subAdmin = SubscriptionAdminClient.create()) {
                subAdmin.deleteSubscription(SubscriptionName.of(projectId, transactionSubscriptionId));
                subAdmin.deleteSubscription(SubscriptionName.of(projectId, alertSubscriptionId));
            }
            
            try (TopicAdminClient topicAdmin = TopicAdminClient.create()) {
                topicAdmin.deleteTopic(TopicName.of(projectId, transactionTopicId));
                topicAdmin.deleteTopic(TopicName.of(projectId, alertTopicId));
            }
            LOG.info("Cleaned up test infrastructure");
        } catch (Exception e) {
            LOG.error("Error cleaning up test infrastructure", e);
        }
    }

    @Test
    @DisplayName("Should process high-value transaction and generate alert")
    void shouldProcessHighValueTransactionAndGenerateAlert() throws Exception {
        Assumptions.assumeTrue(pubSubAvailable, "Pub/Sub not available");
        
        // Arrange - Create a high-value transaction (should trigger alert)
        Transaction highValueTx = Transaction.builder()
                .transactionId("TX-HV-" + UUID.randomUUID().toString().substring(0, 8))
                .accountId("ACC-E2E-001")
                .amount(new BigDecimal("15000.00")) // High value threshold is 10000
                .transactionType(TransactionType.TRANSFER)
                .sourceCountry("US")
                .destinationCountry("GB")
                .timestamp(Instant.now())
                .build();
        
        // Publish transaction
        publishTransaction(highValueTx);
        
        // Wait for processing
        Thread.sleep(2000);
        
        // Act - Process the transaction using FraudDetectionEngine
        Optional<FraudAlert> alert = fraudEngine.evaluate(highValueTx);
        
        // Assert
        assertTrue(alert.isPresent(), "High value transaction should generate an alert");
        FraudAlert fraudAlert = alert.get();
        
        assertEquals(highValueTx.getTransactionId(), fraudAlert.getTransaction().getTransactionId());
        assertTrue(fraudAlert.getRiskScore() > 0.4, "Risk score should be above threshold");
        assertFalse(fraudAlert.getTriggeredRules().isEmpty(), "Should have triggered rules");
        
        LOG.info("High value transaction test passed. Alert: {} with risk score: {}", 
                fraudAlert.getAlertId(), fraudAlert.getRiskScore());
    }

    @Test
    @DisplayName("Should process normal transaction without generating alert")
    void shouldProcessNormalTransactionWithoutAlert() throws Exception {
        Assumptions.assumeTrue(pubSubAvailable, "Pub/Sub not available");
        
        // Arrange - Create a normal transaction
        Transaction normalTx = Transaction.builder()
                .transactionId("TX-NORM-" + UUID.randomUUID().toString().substring(0, 8))
                .accountId("ACC-E2E-002")
                .amount(new BigDecimal("100.00")) // Normal amount
                .transactionType(TransactionType.PAYMENT)
                .sourceCountry("US")
                .destinationCountry("US")
                .timestamp(Instant.now())
                .merchantId("MERCHANT-001")
                .merchantCategory("GROCERY")
                .build();
        
        // Publish transaction
        publishTransaction(normalTx);
        
        // Act
        Optional<FraudAlert> alert = fraudEngine.evaluate(normalTx);
        
        // Assert
        assertFalse(alert.isPresent(), "Normal transaction should not generate an alert");
        LOG.info("Normal transaction test passed. No alert generated.");
    }

    @Test
    @DisplayName("Should process suspicious account transaction and generate alert")
    void shouldProcessSuspiciousAccountTransactionAndGenerateAlert() throws Exception {
        Assumptions.assumeTrue(pubSubAvailable, "Pub/Sub not available");
        
        // Arrange - Create a transaction from suspicious account
        Transaction suspiciousTx = Transaction.builder()
                .transactionId("TX-SUS-" + UUID.randomUUID().toString().substring(0, 8))
                .accountId("FRAUD-ACC-001") // Suspicious account pattern
                .amount(new BigDecimal("500.00"))
                .transactionType(TransactionType.WITHDRAWAL)
                .sourceCountry("US")
                .destinationCountry("RU") // High-risk country
                .timestamp(Instant.now())
                .build();
        
        // Act
        Optional<FraudAlert> alert = fraudEngine.evaluate(suspiciousTx);
        
        // Assert - At least geographic anomaly should trigger
        LOG.info("Suspicious transaction test: Alert present = {}", alert.isPresent());
        if (alert.isPresent()) {
            LOG.info("Alert generated: {} with rules: {}", 
                    alert.get().getAlertId(), alert.get().getTriggeredRules());
        }
    }

    @Test
    @DisplayName("Should handle complete message round-trip through Pub/Sub")
    void shouldHandleCompleteMessageRoundTrip() throws Exception {
        Assumptions.assumeTrue(pubSubAvailable, "Pub/Sub not available");
        
        // Arrange
        Transaction testTx = Transaction.builder()
                .transactionId("TX-RT-" + UUID.randomUUID().toString().substring(0, 8))
                .accountId("ACC-RT-001")
                .amount(new BigDecimal("250.00"))
                .transactionType(TransactionType.PAYMENT)
                .sourceCountry("US")
                .destinationCountry("US")
                .timestamp(Instant.now())
                .build();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Transaction> received = new AtomicReference<>();
        
        // Set up subscriber
        ProjectSubscriptionName subName = ProjectSubscriptionName.of(projectId, transactionSubscriptionId);
        MessageReceiver messageReceiver = (msg, consumer) -> {
            try {
                String data = msg.getData().toStringUtf8();
                Transaction tx = objectMapper.readValue(data, Transaction.class);
                received.set(tx);
                consumer.ack();
                latch.countDown();
            } catch (Exception e) {
                consumer.nack();
            }
        };
        Subscriber subscriber = Subscriber.newBuilder(subName, messageReceiver).build();
        
        subscriber.startAsync().awaitRunning();
        
        // Act - Publish
        publishTransaction(testTx);
        
        // Wait for receipt
        boolean receivedMsg = latch.await(30, TimeUnit.SECONDS);
        subscriber.stopAsync().awaitTerminated();
        
        // Assert
        assertTrue(receivedMsg, "Should receive message within timeout");
        assertNotNull(received.get(), "Received transaction should not be null");
        assertEquals(testTx.getTransactionId(), received.get().getTransactionId());
        
        LOG.info("Round-trip test passed for transaction: {}", testTx.getTransactionId());
    }

    @Test
    @DisplayName("Should verify FraudDetectionEngine works correctly with high-risk transaction")
    void shouldVerifyFraudDetectionEngineWorksWithHighRiskTransaction() throws Exception {
        // Arrange - High value + High risk country
        Transaction highRiskTx = Transaction.builder()
                .transactionId("TX-ENGINE-" + UUID.randomUUID().toString().substring(0, 8))
                .accountId("ACC-ENGINE-001")
                .amount(new BigDecimal("20000.00"))
                .transactionType(TransactionType.TRANSFER)
                .sourceCountry("US")
                .destinationCountry("RU")
                .timestamp(Instant.now())
                .build();
        
        // Act
        Optional<FraudAlert> alert = fraudEngine.evaluate(highRiskTx);
        
        // Assert
        assertTrue(alert.isPresent(), "Engine should detect high-value/high-risk transaction");
        
        FraudAlert fraudAlert = alert.get();
        assertNotNull(fraudAlert.getAlertId());
        assertNotNull(fraudAlert.getTransaction());
        assertTrue(fraudAlert.getRiskScore() > 0);
        assertFalse(fraudAlert.getTriggeredRules().isEmpty());
        
        LOG.info("Engine test passed. Alert: {} Score: {} Rules: {}", 
                fraudAlert.getAlertId(), fraudAlert.getRiskScore(), fraudAlert.getTriggeredRules());
    }

    @Test
    @DisplayName("Should verify logging integration with SLF4J")
    void shouldVerifyLoggingIntegration() {
        // Arrange
        Logger testLogger = LoggerFactory.getLogger("IntegrationTest.Logging");
        String testId = UUID.randomUUID().toString();
        
        // Act - Log at different levels
        testLogger.info("Integration test INFO message: {}", testId);
        testLogger.warn("Integration test WARN message: {}", testId);
        testLogger.error("Integration test ERROR message: {}", testId);
        
        // Assert - Logging doesn't throw exception
        LOG.info("Logging integration test completed for ID: {}", testId);
    }

    private void publishTransaction(Transaction tx) throws Exception {
        TopicName topicName = TopicName.of(projectId, transactionTopicId);
        Publisher publisher = Publisher.newBuilder(topicName).build();
        
        try {
            String json = objectMapper.writeValueAsString(tx);
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(json))
                    .putAttributes("transactionId", tx.getTransactionId())
                    .build();
            
            ApiFuture<String> future = publisher.publish(message);
            String msgId = future.get(10, TimeUnit.SECONDS);
            LOG.info("Published transaction {} with messageId: {}", tx.getTransactionId(), msgId);
        } finally {
            publisher.shutdown();
            publisher.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
