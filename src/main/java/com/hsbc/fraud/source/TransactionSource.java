package com.hsbc.fraud.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transaction data sources for the fraud detection system.
 * Provides Pub/Sub, Kafka, and demo (simulated) data sources.
 */
public class TransactionSource {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionSource.class);

    /**
     * Supported source types for the fraud detection system.
     */
    public enum SourceType {
        PUBSUB,  // Google Cloud Pub/Sub (default)
        KAFKA,   // Apache Kafka
        DEMO     // Simulated data for testing
    }

    /**
     * Creates a Pub/Sub source for transactions.
     *
     * @param env           Flink execution environment
     * @param projectId     GCP project ID
     * @param subscriptionId Pub/Sub subscription ID
     * @return DataStream of transactions
     */
    public static DataStream<Transaction> createPubSubSource(
            StreamExecutionEnvironment env,
            String projectId,
            String subscriptionId) {

        LOG.info("Creating Pub/Sub source - Project: {}, Subscription: {}", projectId, subscriptionId);

        DataStream<Transaction> transactions = env
                .addSource(new PubSubTransactionSource(projectId, subscriptionId))
                .name("Pub/Sub Transaction Source");

        // Add watermarks for event time processing
        return transactions
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Transaction>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((tx, timestamp) ->
                                        tx.getTimestamp() != null ? tx.getTimestamp().toEpochMilli() : System.currentTimeMillis())
                );
    }

    /**
     * Creates a Kafka source for transactions.
     */
    public static DataStream<Transaction> createKafkaSource(
            StreamExecutionEnvironment env,
            String bootstrapServers,
            String topic) {

        KafkaSource<Transaction> kafkaSource = KafkaSource.<Transaction>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId("fraud-detection-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TransactionDeserializer())
                .build();

        return env.fromSource(
                kafkaSource,
                WatermarkStrategy.<Transaction>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((tx, timestamp) -> 
                                tx.getTimestamp() != null ? tx.getTimestamp().toEpochMilli() : System.currentTimeMillis()),
                "Kafka Transaction Source"
        );
    }

    /**
     * JSON deserializer for Transaction objects.
     */
    public static class TransactionDeserializer extends AbstractDeserializationSchema<Transaction> {

        private static final long serialVersionUID = 1L;
        private transient ObjectMapper objectMapper;

        @Override
        public void open(InitializationContext context) throws Exception {
            super.open(context);
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
        }

        @Override
        public Transaction deserialize(byte[] message) {
            try {
                if (objectMapper == null) {
                    objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                }
                return objectMapper.readValue(message, Transaction.class);
            } catch (Exception e) {
                LOG.error("Failed to deserialize transaction: {}", new String(message), e);
                return null;
            }
        }
    }

    /**
     * Demo source that generates simulated transactions for testing.
     * Generates a mix of normal and suspicious transactions.
     */
    public static class DemoTransactionSource extends RichSourceFunction<Transaction> {

        private static final long serialVersionUID = 1L;
        private volatile boolean running = true;
        private transient Random random;

        private static final String[] ACCOUNT_IDS = {
                "ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005",
                "SUSP-001", "SUSP-002", "BLACK-001" // Suspicious/blacklisted accounts
        };

        private static final String[] COUNTRIES = {
                "US", "UK", "DE", "FR", "JP", "XX", "YY", "ZZ" // XX, YY are high-risk, ZZ is blocked
        };

        private static final String[] CHANNELS = {
                "ONLINE", "ATM", "POS", "MOBILE"
        };

        private static final TransactionType[] TX_TYPES = TransactionType.values();

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
            super.open(parameters);
            random = new Random();
        }

        @Override
        public void run(SourceContext<Transaction> ctx) throws Exception {
            LOG.info("Starting demo transaction source");
            
            int counter = 0;
            while (running) {
                Transaction transaction = generateTransaction(counter++);
                
                synchronized (ctx.getCheckpointLock()) {
                    ctx.collect(transaction);
                }

                // Generate transactions at varying rates
                int sleepTime = random.nextInt(1000) + 100; // 100ms to 1.1s
                
                // Occasionally generate burst of transactions (simulating fraud pattern)
                if (random.nextInt(100) < 5) { // 5% chance
                    sleepTime = random.nextInt(100) + 10; // Rapid transactions
                }

                Thread.sleep(sleepTime);
            }
        }

        private Transaction generateTransaction(int counter) {
            String accountId = ACCOUNT_IDS[random.nextInt(ACCOUNT_IDS.length)];
            String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
            
            // Generate amounts - occasionally generate high-value transactions
            BigDecimal amount;
            if (random.nextInt(100) < 10) { // 10% chance of high-value
                amount = new BigDecimal(random.nextInt(100000) + 10000);
            } else if (random.nextInt(100) < 5) { // 5% chance of very high value
                amount = new BigDecimal(random.nextInt(500000) + 50000);
            } else {
                amount = new BigDecimal(random.nextInt(5000) + 10);
            }

            // Generate timestamp - occasionally generate off-hours transactions
            Instant timestamp = Instant.now();
            if (random.nextInt(100) < 10) { // 10% chance of unusual time
                // Set time to 2-4 AM
                timestamp = timestamp.minusSeconds(random.nextInt(7200) + 7200);
            }

            return Transaction.builder()
                    .transactionId("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .accountId(accountId)
                    .targetAccountId(targetAccountId)
                    .amount(amount)
                    .currency("USD")
                    .transactionType(TX_TYPES[random.nextInt(TX_TYPES.length)])
                    .timestamp(timestamp)
                    .merchantId("MERCH-" + String.format("%04d", random.nextInt(10000)))
                    .location("City-" + random.nextInt(100))
                    .countryCode(COUNTRIES[random.nextInt(COUNTRIES.length)])
                    .ipAddress(generateIpAddress())
                    .deviceId("DEV-" + String.format("%06d", random.nextInt(1000000)))
                    .channel(CHANNELS[random.nextInt(CHANNELS.length)])
                    .build();
        }

        private String generateIpAddress() {
            return random.nextInt(256) + "." + 
                   random.nextInt(256) + "." + 
                   random.nextInt(256) + "." + 
                   random.nextInt(256);
        }

        @Override
        public void cancel() {
            LOG.info("Stopping demo transaction source");
            running = false;
        }
    }

    /**
     * Pub/Sub source that reads transactions from Google Cloud Pub/Sub.
     * Uses a pull subscription to receive messages.
     */
    public static class PubSubTransactionSource extends RichSourceFunction<Transaction> {

        private static final long serialVersionUID = 1L;
        private static final Logger LOG = LoggerFactory.getLogger(PubSubTransactionSource.class);

        private final String projectId;
        private final String subscriptionId;
        private volatile boolean running = true;
        private transient Subscriber subscriber;
        private transient BlockingQueue<Transaction> messageQueue;
        private transient ObjectMapper objectMapper;

        public PubSubTransactionSource(String projectId, String subscriptionId) {
            this.projectId = projectId;
            this.subscriptionId = subscriptionId;
        }

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
            super.open(parameters);
            
            LOG.info("Opening Pub/Sub source - Project: {}, Subscription: {}", projectId, subscriptionId);
            
            // Initialize object mapper
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            // Initialize message queue
            messageQueue = new LinkedBlockingQueue<>(10000);

            // Create subscription name
            ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);

            // Create message receiver
            MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
                try {
                    String json = message.getData().toStringUtf8();
                    Transaction transaction = objectMapper.readValue(json, Transaction.class);
                    
                    if (transaction != null) {
                        // Try to add to queue, if full, drop the oldest
                        if (!messageQueue.offer(transaction)) {
                            messageQueue.poll(); // Remove oldest
                            messageQueue.offer(transaction);
                            LOG.warn("Message queue full, dropped oldest message");
                        }
                    }
                    
                    // Acknowledge the message
                    consumer.ack();
                    
                } catch (Exception e) {
                    LOG.error("Failed to process Pub/Sub message: {}", e.getMessage(), e);
                    // Negative acknowledgment - message will be redelivered
                    consumer.nack();
                }
            };

            // Build and start subscriber
            subscriber = Subscriber.newBuilder(subscriptionName, receiver)
                    .build();
            
            subscriber.startAsync().awaitRunning();
            LOG.info("Pub/Sub subscriber started successfully");
        }

        @Override
        public void run(SourceContext<Transaction> ctx) throws Exception {
            LOG.info("Starting Pub/Sub transaction source");
            
            while (running) {
                try {
                    // Poll from queue with timeout
                    Transaction transaction = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    
                    if (transaction != null) {
                        synchronized (ctx.getCheckpointLock()) {
                            ctx.collect(transaction);
                        }
                    }
                    
                } catch (InterruptedException e) {
                    LOG.info("Pub/Sub source interrupted");
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }

        @Override
        public void cancel() {
            LOG.info("Cancelling Pub/Sub transaction source");
            running = false;
            
            if (subscriber != null) {
                try {
                    subscriber.stopAsync().awaitTerminated(30, TimeUnit.SECONDS);
                    LOG.info("Pub/Sub subscriber stopped");
                } catch (TimeoutException e) {
                    LOG.warn("Timeout while stopping Pub/Sub subscriber", e);
                }
            }
        }

        @Override
        public void close() throws Exception {
            cancel();
            super.close();
        }
    }
}

