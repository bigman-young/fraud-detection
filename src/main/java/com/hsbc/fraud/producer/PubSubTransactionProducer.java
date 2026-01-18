package com.hsbc.fraud.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transaction Producer that generates simulated transactions and sends to Google Cloud Pub/Sub.
 * 
 * Prerequisites:
 *   1. Set up Google Cloud authentication:
 *      - Set GOOGLE_APPLICATION_CREDENTIALS environment variable to your service account key file
 *      - Or run on GCP with appropriate IAM permissions
 *   2. Create the Pub/Sub topic in your GCP project
 * 
 * Usage:
 *   java -cp fraud-detection-system-1.0.0.jar com.hsbc.fraud.producer.PubSubTransactionProducer
 * 
 * Environment variables:
 *   GOOGLE_CLOUD_PROJECT - GCP project ID (required)
 *   PUBSUB_TOPIC - Pub/Sub topic name (default: transactions)
 *   TPS - Transactions per second (default: 10)
 *   FRAUD_RATE - Percentage of fraudulent transactions (default: 15)
 *   GOOGLE_APPLICATION_CREDENTIALS - Path to service account key JSON file
 */
public class PubSubTransactionProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubTransactionProducer.class);

    // Configuration defaults
    private static final String DEFAULT_TOPIC = "transactions";
    private static final int DEFAULT_TPS = 10;
    private static final int DEFAULT_FRAUD_RATE = 15;

    // Account pools
    private static final String[] NORMAL_ACCOUNTS = {
            "ACC-001", "ACC-002", "ACC-003", "ACC-004", "ACC-005",
            "ACC-006", "ACC-007", "ACC-008", "ACC-009", "ACC-010",
            "ACC-011", "ACC-012", "ACC-013", "ACC-014", "ACC-015"
    };

    private static final String[] SUSPICIOUS_ACCOUNTS = {
            "SUSP-001", "SUSP-002", "WATCH-001"
    };

    private static final String[] BLACKLISTED_ACCOUNTS = {
            "BLACK-001", "BLACK-002", "FRAUD-001"
    };

    private static final String[] NORMAL_COUNTRIES = {
            "US", "UK", "DE", "FR", "JP", "CA", "AU", "SG", "HK", "CN"
    };

    private static final String[] HIGH_RISK_COUNTRIES = {
            "XX", "YY"
    };

    private static final String[] BLOCKED_COUNTRIES = {
            "ZZ"
    };

    private static final String[] CHANNELS = {"ONLINE", "ATM", "POS", "MOBILE"};
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "CNY", "JPY"};
    private static final TransactionType[] TX_TYPES = TransactionType.values();

    private final Publisher publisher;
    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String topicId;
    private final int tps;
    private final int fraudRate;
    private final Random random;
    private final AtomicBoolean running;
    private final AtomicLong totalSent;
    private final AtomicLong fraudSent;
    private final AtomicLong failedCount;

    public PubSubTransactionProducer(String projectId, String topicId, int tps, int fraudRate) throws IOException {
        this.projectId = projectId;
        this.topicId = topicId;
        this.tps = tps;
        this.fraudRate = fraudRate;
        this.random = new Random();
        this.running = new AtomicBoolean(true);
        this.totalSent = new AtomicLong(0);
        this.fraudSent = new AtomicLong(0);
        this.failedCount = new AtomicLong(0);

        // Configure JSON serializer
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create Pub/Sub publisher
        TopicName topicName = TopicName.of(projectId, topicId);
        LOG.info("Initializing Pub/Sub publisher for topic: {}", topicName);
        
        this.publisher = Publisher.newBuilder(topicName)
                .build();

        LOG.info("PubSubTransactionProducer initialized - Project: {}, Topic: {}, TPS: {}, FraudRate: {}%",
                projectId, topicId, tps, fraudRate);
    }

    /**
     * Start producing transactions at the configured rate.
     */
    public void start() {
        LOG.info("Starting Pub/Sub transaction producer...");

        long intervalMs = 1000 / tps;
        long lastLogTime = System.currentTimeMillis();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received, stopping producer...");
            running.set(false);
        }));

        while (running.get()) {
            try {
                // Decide if this should be a fraudulent transaction
                boolean isFraud = random.nextInt(100) < fraudRate;

                Transaction transaction = isFraud ? generateFraudulentTransaction() : generateNormalTransaction();
                String json = objectMapper.writeValueAsString(transaction);

                // Create Pub/Sub message
                ByteString data = ByteString.copyFromUtf8(json);
                PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                        .setData(data)
                        .putAttributes("account_id", transaction.getAccountId())
                        .putAttributes("transaction_type", transaction.getTransactionType().name())
                        .putAttributes("country_code", transaction.getCountryCode())
                        .putAttributes("is_suspicious", String.valueOf(isFraud))
                        .build();

                // Publish asynchronously
                ApiFuture<String> future = publisher.publish(pubsubMessage);
                
                final boolean finalIsFraud = isFraud;
                ApiFutures.addCallback(future, new ApiFutureCallback<String>() {
                    @Override
                    public void onSuccess(String messageId) {
                        totalSent.incrementAndGet();
                        if (finalIsFraud) {
                            fraudSent.incrementAndGet();
                        }
                        LOG.debug("Published message ID: {}", messageId);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        failedCount.incrementAndGet();
                        if (t instanceof ApiException) {
                            ApiException apiException = (ApiException) t;
                            LOG.error("Failed to publish message. Status: {}, Error: {}", 
                                    apiException.getStatusCode().getCode(), 
                                    apiException.getMessage());
                        } else {
                            LOG.error("Failed to publish message: {}", t.getMessage());
                        }
                    }
                }, MoreExecutors.directExecutor());

                // Log stats every 10 seconds
                long now = System.currentTimeMillis();
                if (now - lastLogTime >= 10000) {
                    LOG.info("Stats - Total sent: {}, Fraudulent: {} ({}%), Failed: {}, Rate: {} tx/s",
                            totalSent.get(),
                            fraudSent.get(),
                            totalSent.get() > 0 ? (fraudSent.get() * 100 / totalSent.get()) : 0,
                            failedCount.get(),
                            tps);
                    lastLogTime = now;
                }

                // Sleep to maintain the target TPS
                Thread.sleep(intervalMs);

            } catch (InterruptedException e) {
                LOG.info("Producer interrupted");
                running.set(false);
            } catch (Exception e) {
                LOG.error("Error producing transaction: {}", e.getMessage(), e);
            }
        }

        close();
    }

    /**
     * Generate a normal (non-fraudulent) transaction.
     */
    private Transaction generateNormalTransaction() {
        String accountId = NORMAL_ACCOUNTS[random.nextInt(NORMAL_ACCOUNTS.length)];
        String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
        BigDecimal amount = new BigDecimal(random.nextInt(5000) + 10);
        String country = NORMAL_COUNTRIES[random.nextInt(NORMAL_COUNTRIES.length)];

        return buildTransaction(accountId, targetAccountId, amount, country);
    }

    /**
     * Generate a fraudulent transaction with various fraud patterns.
     */
    private Transaction generateFraudulentTransaction() {
        int fraudType = random.nextInt(5);

        switch (fraudType) {
            case 0:
                return generateHighValueTransaction();
            case 1:
                return generateSuspiciousAccountTransaction();
            case 2:
                return generateBlacklistedAccountTransaction();
            case 3:
                return generateHighRiskCountryTransaction();
            case 4:
                return generateBlockedCountryTransaction();
            default:
                return generateHighValueTransaction();
        }
    }

    private Transaction generateHighValueTransaction() {
        String accountId = NORMAL_ACCOUNTS[random.nextInt(NORMAL_ACCOUNTS.length)];
        String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
        BigDecimal amount = new BigDecimal(random.nextInt(190000) + 10000);
        String country = NORMAL_COUNTRIES[random.nextInt(NORMAL_COUNTRIES.length)];

        Transaction tx = buildTransaction(accountId, targetAccountId, amount, country);
        LOG.debug("Generated HIGH VALUE transaction: {} - Amount: {}", tx.getTransactionId(), amount);
        return tx;
    }

    private Transaction generateSuspiciousAccountTransaction() {
        String accountId = SUSPICIOUS_ACCOUNTS[random.nextInt(SUSPICIOUS_ACCOUNTS.length)];
        String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
        BigDecimal amount = new BigDecimal(random.nextInt(10000) + 100);
        String country = NORMAL_COUNTRIES[random.nextInt(NORMAL_COUNTRIES.length)];

        Transaction tx = buildTransaction(accountId, targetAccountId, amount, country);
        LOG.debug("Generated SUSPICIOUS ACCOUNT transaction: {} - Account: {}", tx.getTransactionId(), accountId);
        return tx;
    }

    private Transaction generateBlacklistedAccountTransaction() {
        String accountId = BLACKLISTED_ACCOUNTS[random.nextInt(BLACKLISTED_ACCOUNTS.length)];
        String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
        BigDecimal amount = new BigDecimal(random.nextInt(50000) + 100);
        String country = NORMAL_COUNTRIES[random.nextInt(NORMAL_COUNTRIES.length)];

        Transaction tx = buildTransaction(accountId, targetAccountId, amount, country);
        LOG.debug("Generated BLACKLISTED ACCOUNT transaction: {} - Account: {}", tx.getTransactionId(), accountId);
        return tx;
    }

    private Transaction generateHighRiskCountryTransaction() {
        String accountId = NORMAL_ACCOUNTS[random.nextInt(NORMAL_ACCOUNTS.length)];
        String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
        BigDecimal amount = new BigDecimal(random.nextInt(20000) + 500);
        String country = HIGH_RISK_COUNTRIES[random.nextInt(HIGH_RISK_COUNTRIES.length)];

        Transaction tx = buildTransaction(accountId, targetAccountId, amount, country);
        LOG.debug("Generated HIGH-RISK COUNTRY transaction: {} - Country: {}", tx.getTransactionId(), country);
        return tx;
    }

    private Transaction generateBlockedCountryTransaction() {
        String accountId = NORMAL_ACCOUNTS[random.nextInt(NORMAL_ACCOUNTS.length)];
        String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
        BigDecimal amount = new BigDecimal(random.nextInt(30000) + 1000);
        String country = BLOCKED_COUNTRIES[random.nextInt(BLOCKED_COUNTRIES.length)];

        Transaction tx = buildTransaction(accountId, targetAccountId, amount, country);
        LOG.debug("Generated BLOCKED COUNTRY transaction: {} - Country: {}", tx.getTransactionId(), country);
        return tx;
    }

    private Transaction buildTransaction(String accountId, String targetAccountId,
                                         BigDecimal amount, String countryCode) {
        return Transaction.builder()
                .transactionId("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .accountId(accountId)
                .targetAccountId(targetAccountId)
                .amount(amount)
                .currency(CURRENCIES[random.nextInt(CURRENCIES.length)])
                .transactionType(TX_TYPES[random.nextInt(TX_TYPES.length)])
                .timestamp(Instant.now())
                .merchantId("MERCH-" + String.format("%04d", random.nextInt(10000)))
                .location("City-" + random.nextInt(100))
                .countryCode(countryCode)
                .ipAddress(generateRandomIp())
                .deviceId("DEV-" + String.format("%06d", random.nextInt(1000000)))
                .channel(CHANNELS[random.nextInt(CHANNELS.length)])
                .build();
    }

    private String generateRandomIp() {
        return random.nextInt(256) + "." +
               random.nextInt(256) + "." +
               random.nextInt(256) + "." +
               random.nextInt(256);
    }

    public void close() {
        LOG.info("Closing Pub/Sub producer... Total sent: {}, Fraudulent: {}, Failed: {}",
                totalSent.get(), fraudSent.get(), failedCount.get());
        
        if (publisher != null) {
            try {
                publisher.shutdown();
                publisher.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.warn("Publisher shutdown interrupted");
            }
        }
    }

    public static void main(String[] args) {
        // Get configuration from environment variables or command line
        String projectId = getConfig("GOOGLE_CLOUD_PROJECT", null, args, 0);
        String topicId = getConfig("PUBSUB_TOPIC", DEFAULT_TOPIC, args, 1);
        int tps = Integer.parseInt(getConfig("TPS", String.valueOf(DEFAULT_TPS), args, 2));
        int fraudRate = Integer.parseInt(getConfig("FRAUD_RATE", String.valueOf(DEFAULT_FRAUD_RATE), args, 3));

        // Validate required configuration
        if (projectId == null || projectId.isEmpty()) {
            System.err.println("ERROR: GOOGLE_CLOUD_PROJECT environment variable is required!");
            System.err.println();
            System.err.println("Usage:");
            System.err.println("  Set environment variables:");
            System.err.println("    GOOGLE_CLOUD_PROJECT=your-gcp-project-id");
            System.err.println("    GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json");
            System.err.println("    PUBSUB_TOPIC=transactions (optional, default: transactions)");
            System.err.println("    TPS=10 (optional, default: 10)");
            System.err.println("    FRAUD_RATE=15 (optional, default: 15)");
            System.err.println();
            System.err.println("  Or pass as command line arguments:");
            System.err.println("    java -cp ... PubSubTransactionProducer <project-id> <topic> <tps> <fraud-rate>");
            System.exit(1);
        }

        System.out.println("==============================================");
        System.out.println("   Pub/Sub Transaction Producer Starting      ");
        System.out.println("==============================================");
        System.out.println("GCP Project: " + projectId);
        System.out.println("Pub/Sub Topic: " + topicId);
        System.out.println("Target TPS: " + tps);
        System.out.println("Fraud Rate: " + fraudRate + "%");
        System.out.println("==============================================");
        System.out.println("Press Ctrl+C to stop");
        System.out.println();

        try {
            PubSubTransactionProducer producer = new PubSubTransactionProducer(projectId, topicId, tps, fraudRate);
            producer.start();
        } catch (IOException e) {
            System.err.println("Failed to initialize Pub/Sub producer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getConfig(String envName, String defaultValue, String[] args, int argIndex) {
        // Priority: command line args > environment variable > default
        if (args != null && args.length > argIndex && args[argIndex] != null && !args[argIndex].isEmpty()) {
            return args[argIndex];
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return defaultValue;
    }
}
