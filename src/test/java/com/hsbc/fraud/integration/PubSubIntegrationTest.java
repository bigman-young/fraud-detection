package com.hsbc.fraud.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Google Cloud Pub/Sub message queue interaction.
 * 
 * These tests verify:
 * 1. Publishing messages to Pub/Sub topics
 * 2. Subscribing and receiving messages from Pub/Sub
 * 3. Message serialization/deserialization
 * 4. Error handling for Pub/Sub operations
 * 
 * Prerequisites:
 * - GCP project with Pub/Sub API enabled
 * - GOOGLE_APPLICATION_CREDENTIALS environment variable set
 * - GOOGLE_CLOUD_PROJECT environment variable set
 * 
 * To run these tests:
 * mvn test -Dtest=PubSubIntegrationTest -DskipUnitTests
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@DisplayName("Pub/Sub Integration Tests")
public class PubSubIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubIntegrationTest.class);
    
    private String projectId;
    private String testTopicId;
    private String testSubscriptionId;
    private ObjectMapper objectMapper;
    private boolean pubSubAvailable = false;

    @BeforeAll
    void setUp() {
        projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (projectId == null || projectId.isEmpty()) {
            projectId = "hsbc-484505"; // Default for local testing
        }
        
        testTopicId = "test-transactions-" + UUID.randomUUID().toString().substring(0, 8);
        testSubscriptionId = "test-sub-" + UUID.randomUUID().toString().substring(0, 8);
        
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Check if Pub/Sub is available
        try {
            createTestTopicAndSubscription();
            pubSubAvailable = true;
            LOG.info("Pub/Sub is available. Running integration tests.");
        } catch (Exception e) {
            LOG.warn("Pub/Sub is not available. Skipping integration tests. Error: {}", e.getMessage());
            pubSubAvailable = false;
        }
    }

    @AfterAll
    void tearDown() {
        if (pubSubAvailable) {
            cleanupTestResources();
        }
    }

    private void createTestTopicAndSubscription() throws Exception {
        // Create test topic
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
            TopicName topicName = TopicName.of(projectId, testTopicId);
            topicAdminClient.createTopic(topicName);
            LOG.info("Created test topic: {}", topicName);
        }

        // Create test subscription
        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
            TopicName topicName = TopicName.of(projectId, testTopicId);
            SubscriptionName subscriptionName = SubscriptionName.of(projectId, testSubscriptionId);
            
            Subscription subscription = subscriptionAdminClient.createSubscription(
                    subscriptionName, topicName, PushConfig.getDefaultInstance(), 10);
            LOG.info("Created test subscription: {}", subscription.getName());
        }
    }

    private void cleanupTestResources() {
        try {
            // Delete subscription first
            try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create()) {
                SubscriptionName subscriptionName = SubscriptionName.of(projectId, testSubscriptionId);
                subscriptionAdminClient.deleteSubscription(subscriptionName);
                LOG.info("Deleted test subscription: {}", subscriptionName);
            }
            
            // Delete topic
            try (TopicAdminClient topicAdminClient = TopicAdminClient.create()) {
                TopicName topicName = TopicName.of(projectId, testTopicId);
                topicAdminClient.deleteTopic(topicName);
                LOG.info("Deleted test topic: {}", topicName);
            }
        } catch (Exception e) {
            LOG.error("Error cleaning up test resources", e);
        }
    }

    @Test
    @DisplayName("Should publish transaction message to Pub/Sub")
    void shouldPublishTransactionMessage() throws Exception {
        Assumptions.assumeTrue(pubSubAvailable, "Pub/Sub not available");
        
        // Arrange
        Transaction transaction = createTestTransaction();
        String messageJson = objectMapper.writeValueAsString(transaction);
        
        TopicName topicName = TopicName.of(projectId, testTopicId);
        Publisher publisher = null;
        
        try {
            // Act
            publisher = Publisher.newBuilder(topicName).build();
            
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(messageJson))
                    .putAttributes("transactionId", transaction.getTransactionId())
                    .putAttributes("accountId", transaction.getAccountId())
                    .build();
            
            ApiFuture<String> future = publisher.publish(message);
            String messageId = future.get(10, TimeUnit.SECONDS);
            
            // Assert
            assertNotNull(messageId, "Message ID should not be null");
            LOG.info("Published message with ID: {}", messageId);
            
        } finally {
            if (publisher != null) {
                publisher.shutdown();
                publisher.awaitTermination(30, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    @DisplayName("Should receive transaction message from Pub/Sub subscription")
    void shouldReceiveTransactionMessage() throws Exception {
        Assumptions.assumeTrue(pubSubAvailable, "Pub/Sub not available");
        
        // Arrange
        Transaction sentTransaction = createTestTransaction();
        String messageJson = objectMapper.writeValueAsString(sentTransaction);
        
        // Publish a message first
        TopicName topicName = TopicName.of(projectId, testTopicId);
        Publisher publisher = Publisher.newBuilder(topicName).build();
        
        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(messageJson))
                .build();
        
        publisher.publish(message).get(10, TimeUnit.SECONDS);
        publisher.shutdown();
        
        // Act - Subscribe and receive the message
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Transaction> receivedTransaction = new AtomicReference<>();
        
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, testSubscriptionId);
        
        Subscriber subscriber = Subscriber.newBuilder(subscriptionName, (msg, consumer) -> {
            try {
                String data = msg.getData().toStringUtf8();
                Transaction tx = objectMapper.readValue(data, Transaction.class);
                receivedTransaction.set(tx);
                consumer.ack();
                latch.countDown();
            } catch (Exception e) {
                LOG.error("Error processing message", e);
                consumer.nack();
            }
        }).build();
        
        subscriber.startAsync().awaitRunning();
        
        // Wait for message
        boolean received = latch.await(30, TimeUnit.SECONDS);
        subscriber.stopAsync().awaitTerminated();
        
        // Assert
        assertTrue(received, "Should receive message within timeout");
        assertNotNull(receivedTransaction.get(), "Received transaction should not be null");
        assertEquals(sentTransaction.getTransactionId(), receivedTransaction.get().getTransactionId());
        assertEquals(sentTransaction.getAccountId(), receivedTransaction.get().getAccountId());
        assertEquals(0, sentTransaction.getAmount().compareTo(receivedTransaction.get().getAmount()));
        
        LOG.info("Successfully received transaction: {}", receivedTransaction.get().getTransactionId());
    }

    @Test
    @DisplayName("Should handle multiple messages in batch")
    void shouldHandleMultipleMessagesInBatch() throws Exception {
        Assumptions.assumeTrue(pubSubAvailable, "Pub/Sub not available");
        
        // Arrange
        int messageCount = 5;
        TopicName topicName = TopicName.of(projectId, testTopicId);
        Publisher publisher = Publisher.newBuilder(topicName).build();
        
        // Act - Publish multiple messages
        for (int i = 0; i < messageCount; i++) {
            Transaction transaction = createTestTransaction();
            String messageJson = objectMapper.writeValueAsString(transaction);
            
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(messageJson))
                    .putAttributes("index", String.valueOf(i))
                    .build();
            
            publisher.publish(message);
        }
        
        publisher.shutdown();
        publisher.awaitTermination(30, TimeUnit.SECONDS);
        
        // Act - Receive messages
        CountDownLatch latch = new CountDownLatch(messageCount);
        
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, testSubscriptionId);
        
        Subscriber subscriber = Subscriber.newBuilder(subscriptionName, (msg, consumer) -> {
            consumer.ack();
            latch.countDown();
            LOG.info("Received message {}", msg.getAttributesMap().get("index"));
        }).build();
        
        subscriber.startAsync().awaitRunning();
        
        boolean allReceived = latch.await(60, TimeUnit.SECONDS);
        subscriber.stopAsync().awaitTerminated();
        
        // Assert
        assertTrue(allReceived, "Should receive all " + messageCount + " messages");
        LOG.info("Successfully received all {} messages", messageCount);
    }

    @Test
    @DisplayName("Should serialize and deserialize Transaction correctly")
    void shouldSerializeAndDeserializeTransaction() throws Exception {
        // Arrange
        Transaction original = createTestTransaction();
        
        // Act
        String json = objectMapper.writeValueAsString(original);
        Transaction deserialized = objectMapper.readValue(json, Transaction.class);
        
        // Assert
        assertEquals(original.getTransactionId(), deserialized.getTransactionId());
        assertEquals(original.getAccountId(), deserialized.getAccountId());
        assertEquals(0, original.getAmount().compareTo(deserialized.getAmount()));
        assertEquals(original.getTransactionType(), deserialized.getTransactionType());
        assertEquals(original.getSourceCountry(), deserialized.getSourceCountry());
        assertEquals(original.getDestinationCountry(), deserialized.getDestinationCountry());
        
        LOG.info("Serialization/Deserialization test passed");
    }

    @Test
    @DisplayName("Should handle connection errors gracefully")
    void shouldHandleConnectionErrorsGracefully() {
        // Arrange - Use invalid project ID
        String invalidProjectId = "invalid-project-" + UUID.randomUUID();
        TopicName invalidTopic = TopicName.of(invalidProjectId, "invalid-topic");
        
        // Act & Assert
        assertThrows(Exception.class, () -> {
            Publisher publisher = Publisher.newBuilder(invalidTopic).build();
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8("test"))
                    .build();
            publisher.publish(message).get(5, TimeUnit.SECONDS);
        }, "Should throw exception for invalid project");
        
        LOG.info("Connection error handling test passed");
    }

    private Transaction createTestTransaction() {
        return Transaction.builder()
                .transactionId("TX-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .accountId("ACC-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .amount(new BigDecimal("1000.00"))
                .transactionType(TransactionType.TRANSFER)
                .sourceCountry("US")
                .destinationCountry("GB")
                .timestamp(Instant.now())
                .merchantId("MERCHANT-001")
                .merchantCategory("RETAIL")
                .build();
    }
}
