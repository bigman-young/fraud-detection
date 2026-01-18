package com.hsbc.fraud.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.model.TransactionType;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone Transaction Producer that generates simulated transactions and sends to Kafka.
 * Can be run independently on the Kafka server or any machine with Kafka connectivity.
 * 
 * Usage:
 *   java -cp fraud-detection-system-1.0.0.jar com.hsbc.fraud.producer.TransactionProducer
 * 
 * Environment variables:
 *   KAFKA_BOOTSTRAP_SERVERS - Kafka broker address (default: 10.113.192.45:9092)
 *   KAFKA_TOPIC - Target topic (default: transactions)
 *   TPS - Transactions per second (default: 10)
 *   FRAUD_RATE - Percentage of fraudulent transactions (default: 15)
 */
public class TransactionProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionProducer.class);

    // Configuration with defaults
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "10.113.192.45:9092";
    private static final String DEFAULT_TOPIC = "transactions";
    private static final int DEFAULT_TPS = 10;  // transactions per second
    private static final int DEFAULT_FRAUD_RATE = 15;  // percentage

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
            "XX", "YY"  // Fictional high-risk countries
    };

    private static final String[] BLOCKED_COUNTRIES = {
            "ZZ"  // Fictional blocked country
    };

    private static final String[] CHANNELS = {"ONLINE", "ATM", "POS", "MOBILE"};
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "CNY", "JPY"};
    private static final TransactionType[] TX_TYPES = TransactionType.values();

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final int tps;
    private final int fraudRate;
    private final Random random;
    private final AtomicBoolean running;
    private final AtomicLong totalSent;
    private final AtomicLong fraudSent;

    public TransactionProducer(String bootstrapServers, String topic, int tps, int fraudRate) {
        this.topic = topic;
        this.tps = tps;
        this.fraudRate = fraudRate;
        this.random = new Random();
        this.running = new AtomicBoolean(true);
        this.totalSent = new AtomicLong(0);
        this.fraudSent = new AtomicLong(0);

        // Configure Kafka producer
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        // 减少超时时间以便快速发现连接问题
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);  // 10 seconds
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000); // 30 seconds
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);        // 10 seconds

        this.producer = new KafkaProducer<>(props);
        
        // 测试连接
        LOG.info("Testing Kafka connection to {}...", bootstrapServers);
        testConnection(bootstrapServers, topic);

        // Configure JSON serializer
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LOG.info("TransactionProducer initialized - Kafka: {}, Topic: {}, TPS: {}, FraudRate: {}%",
                bootstrapServers, topic, tps, fraudRate);
    }

    /**
     * Start producing transactions at the configured rate.
     */
    public void start() {
        LOG.info("Starting transaction producer...");
        
        long intervalMs = 1000 / tps;  // interval between transactions in ms
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

                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, 
                        transaction.getAccountId(),  // Use account as key for partitioning
                        json
                );

                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        LOG.error("Failed to send transaction: {}", exception.getMessage());
                    } else {
                        totalSent.incrementAndGet();
                        if (isFraud) {
                            fraudSent.incrementAndGet();
                        }
                    }
                });

                // Log stats every 10 seconds
                long now = System.currentTimeMillis();
                if (now - lastLogTime >= 10000) {
                    LOG.info("Stats - Total sent: {}, Fraudulent: {} ({}%), Rate: {} tx/s",
                            totalSent.get(),
                            fraudSent.get(),
                            totalSent.get() > 0 ? (fraudSent.get() * 100 / totalSent.get()) : 0,
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
        BigDecimal amount = new BigDecimal(random.nextInt(5000) + 10);  // $10 - $5010
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
                // High-value transaction
                return generateHighValueTransaction();
            case 1:
                // Suspicious account transaction
                return generateSuspiciousAccountTransaction();
            case 2:
                // Blacklisted account transaction
                return generateBlacklistedAccountTransaction();
            case 3:
                // High-risk country transaction
                return generateHighRiskCountryTransaction();
            case 4:
                // Blocked country transaction
                return generateBlockedCountryTransaction();
            default:
                return generateHighValueTransaction();
        }
    }

    private Transaction generateHighValueTransaction() {
        String accountId = NORMAL_ACCOUNTS[random.nextInt(NORMAL_ACCOUNTS.length)];
        String targetAccountId = "ACC-" + String.format("%03d", random.nextInt(1000));
        // High value: $10,000 - $200,000
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

    /**
     * Test connection to Kafka and create topic if needed.
     */
    private void testConnection(String bootstrapServers, String topic) {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        adminProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000);

        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            // 尝试列出主题以测试连接
            ListTopicsResult topicsResult = adminClient.listTopics();
            Set<String> topics = topicsResult.names().get(10, TimeUnit.SECONDS);
            
            LOG.info("Successfully connected to Kafka! Found {} topics", topics.size());
            
            // 检查目标主题是否存在
            if (!topics.contains(topic)) {
                LOG.warn("Topic '{}' does not exist. Creating it...", topic);
                try {
                    NewTopic newTopic = new NewTopic(topic, 4, (short) 1);
                    adminClient.createTopics(Collections.singletonList(newTopic)).all().get(10, TimeUnit.SECONDS);
                    LOG.info("Topic '{}' created successfully!", topic);
                } catch (Exception e) {
                    LOG.warn("Could not create topic (may already exist or auto-create is enabled): {}", e.getMessage());
                }
            } else {
                LOG.info("Topic '{}' exists.", topic);
            }
            
        } catch (TimeoutException e) {
            LOG.error("==============================================");
            LOG.error("KAFKA CONNECTION TIMEOUT!");
            LOG.error("Cannot connect to Kafka at: {}", bootstrapServers);
            LOG.error("Please check:");
            LOG.error("  1. Kafka broker is running");
            LOG.error("  2. Network connectivity (try: telnet {} {})", 
                    bootstrapServers.split(":")[0], 
                    bootstrapServers.contains(":") ? bootstrapServers.split(":")[1] : "9092");
            LOG.error("  3. Firewall settings");
            LOG.error("  4. Kafka advertised.listeners configuration");
            LOG.error("==============================================");
            throw new RuntimeException("Cannot connect to Kafka: " + e.getMessage(), e);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Failed to connect to Kafka: {}", e.getMessage());
            throw new RuntimeException("Cannot connect to Kafka: " + e.getMessage(), e);
        }
    }

    public void close() {
        LOG.info("Closing producer... Total transactions sent: {}, Fraudulent: {}",
                totalSent.get(), fraudSent.get());
        producer.flush();
        producer.close();
    }

    public static void main(String[] args) {
        // Get configuration from environment variables or command line
        String bootstrapServers = getConfig("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_BOOTSTRAP_SERVERS, args, 0);
        String topic = getConfig("KAFKA_TOPIC", DEFAULT_TOPIC, args, 1);
        int tps = Integer.parseInt(getConfig("TPS", String.valueOf(DEFAULT_TPS), args, 2));
        int fraudRate = Integer.parseInt(getConfig("FRAUD_RATE", String.valueOf(DEFAULT_FRAUD_RATE), args, 3));

        System.out.println("==============================================");
        System.out.println("       Transaction Producer Starting          ");
        System.out.println("==============================================");
        System.out.println("Kafka Brokers: " + bootstrapServers);
        System.out.println("Topic: " + topic);
        System.out.println("Target TPS: " + tps);
        System.out.println("Fraud Rate: " + fraudRate + "%");
        System.out.println("==============================================");
        System.out.println("Press Ctrl+C to stop");
        System.out.println();

        TransactionProducer producer = new TransactionProducer(bootstrapServers, topic, tps, fraudRate);
        producer.start();
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
