package com.hsbc.fraud;

import com.hsbc.fraud.model.FraudAlert;
import com.hsbc.fraud.model.Transaction;
import com.hsbc.fraud.processor.FraudDetectionProcessor;
import com.hsbc.fraud.processor.VelocityCheckProcessor;
import com.hsbc.fraud.sink.AlertSink;
import com.hsbc.fraud.source.TransactionSource;
import com.hsbc.fraud.source.TransactionSource.SourceType;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Main Flink job for real-time fraud detection.
 * This job processes transaction streams and generates fraud alerts in real-time.
 * 
 * Supported data sources (configured via SOURCE_TYPE environment variable):
 * - PUBSUB: Google Cloud Pub/Sub (default)
 * - KAFKA: Apache Kafka
 * - DEMO: Simulated data for testing
 */
public class FraudDetectionJob {

    private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionJob.class);

    // Default configuration values
    private static final String DEFAULT_SOURCE_TYPE = "PUBSUB";
    private static final String DEFAULT_GCP_PROJECT = "hsbc-484505";
    private static final String DEFAULT_PUBSUB_SUBSCRIPTION = "transactions-sub";
    private static final String DEFAULT_KAFKA_SERVERS = "10.113.192.45:9092";
    private static final String DEFAULT_INPUT_TOPIC = "transactions";
    private static final String DEFAULT_ALERT_TOPIC = "fraud-alerts";
    private static final String DEFAULT_ALERT_THRESHOLD = "0.4";

    public static void main(String[] args) throws Exception {
        LOG.info("Starting Fraud Detection Job...");

        // Get source type configuration
        String sourceTypeStr = getEnvOrDefault("SOURCE_TYPE", DEFAULT_SOURCE_TYPE);
        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(sourceTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid SOURCE_TYPE '{}', defaulting to PUBSUB", sourceTypeStr);
            sourceType = SourceType.PUBSUB;
        }

        // Get common configuration
        double alertThreshold = Double.parseDouble(getEnvOrDefault("ALERT_THRESHOLD", DEFAULT_ALERT_THRESHOLD));

        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Configure environment
        configureEnvironment(env);

        // Build and execute the pipeline based on source type
        FraudDetectionJob job = new FraudDetectionJob();

        LOG.info("Using source type: {}", sourceType);

        switch (sourceType) {
            case PUBSUB:
                String gcpProject = getEnvOrDefault("GOOGLE_CLOUD_PROJECT", DEFAULT_GCP_PROJECT);
                String subscription = getEnvOrDefault("PUBSUB_SUBSCRIPTION", DEFAULT_PUBSUB_SUBSCRIPTION);
                String alertTopic = getEnvOrDefault("PUBSUB_ALERT_TOPIC", "fraud-alerts");
                job.buildPubSubPipeline(env, gcpProject, subscription, alertTopic, alertThreshold);
                break;

            case KAFKA:
                String kafkaServers = getEnvOrDefault("KAFKA_BOOTSTRAP_SERVERS", DEFAULT_KAFKA_SERVERS);
                String inputTopic = getEnvOrDefault("INPUT_TOPIC", DEFAULT_INPUT_TOPIC);
                String kafkaAlertTopic = getEnvOrDefault("ALERT_TOPIC", DEFAULT_ALERT_TOPIC);
                job.buildKafkaPipeline(env, kafkaServers, inputTopic, kafkaAlertTopic, alertThreshold);
                break;

            case DEMO:
            default:
                job.buildDemoPipeline(env, alertThreshold);
                break;
        }

        // Execute
        env.execute("Real-Time Fraud Detection System");
    }

    /**
     * Configures the Flink execution environment.
     */
    private static void configureEnvironment(StreamExecutionEnvironment env) {
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(60000); // Checkpoint every 60 seconds
        
        // Set parallelism based on environment
        int parallelism = Integer.parseInt(getEnvOrDefault("PARALLELISM", "4"));
        env.setParallelism(parallelism);

        LOG.info("Environment configured with parallelism: {}", parallelism);
    }

    /**
     * Builds the fraud detection pipeline with Google Cloud Pub/Sub source.
     * This is the default pipeline configuration.
     */
    public void buildPubSubPipeline(StreamExecutionEnvironment env,
                                     String projectId,
                                     String subscriptionId,
                                     String alertTopic,
                                     double alertThreshold) {

        LOG.info("Building Pub/Sub pipeline: project={}, subscription={}, alertTopic={}",
                projectId, subscriptionId, alertTopic);

        // Create Pub/Sub source
        DataStream<Transaction> transactions = TransactionSource.createPubSubSource(
                env, projectId, subscriptionId);

        // Process transactions
        DataStream<FraudAlert> alerts = processTransactions(transactions, alertThreshold);

        // Output alerts to log (Pub/Sub sink can be added later if needed)
        alerts.addSink(new AlertSink.LoggingSink())
              .name("Logging Sink");

        // Print to console for monitoring
        alerts.print().name("Console Output");

        // TODO: Add Pub/Sub sink for alerts if needed
        // alerts.addSink(new PubSubAlertSink(projectId, alertTopic))
        //       .name("Pub/Sub Alert Sink");
    }

    /**
     * Builds the fraud detection pipeline with Kafka source and sink.
     */
    public void buildKafkaPipeline(StreamExecutionEnvironment env, 
                                    String bootstrapServers,
                                    String inputTopic, 
                                    String alertTopic,
                                    double alertThreshold) {

        LOG.info("Building Kafka pipeline: input={}, alerts={}", inputTopic, alertTopic);

        // Create Kafka source
        DataStream<Transaction> transactions = TransactionSource.createKafkaSource(
                env, bootstrapServers, inputTopic);

        // Process transactions
        DataStream<FraudAlert> alerts = processTransactions(transactions, alertThreshold);

        // Output to Kafka
        alerts.sinkTo(AlertSink.createKafkaSink(bootstrapServers, alertTopic))
              .name("Kafka Alert Sink");

        // Also log to console for monitoring
        alerts.addSink(new AlertSink.LoggingSink())
              .name("Logging Sink");
    }

    /**
     * Builds a demo pipeline with generated transaction data.
     */
    public void buildDemoPipeline(StreamExecutionEnvironment env, double alertThreshold) {
        LOG.info("Building demo pipeline with simulated transactions");

        // Create demo source that generates random transactions
        DataStream<Transaction> transactions = env
                .addSource(new TransactionSource.DemoTransactionSource())
                .name("Demo Transaction Source");

        // Add watermarks for event time processing
        transactions = transactions
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Transaction>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((tx, timestamp) -> 
                                        tx.getTimestamp() != null ? tx.getTimestamp().toEpochMilli() : System.currentTimeMillis())
                );

        // Process transactions
        DataStream<FraudAlert> alerts = processTransactions(transactions, alertThreshold);

        // Output alerts to log
        alerts.addSink(new AlertSink.LoggingSink())
              .name("Logging Sink");

        // Print to console
        alerts.print().name("Console Output");
    }

    /**
     * Processes transactions through the fraud detection pipeline.
     * This is the core processing logic shared by all pipeline configurations.
     */
    public DataStream<FraudAlert> processTransactions(DataStream<Transaction> transactions, 
                                                       double alertThreshold) {
        // Rule-based fraud detection
        SingleOutputStreamOperator<FraudAlert> ruleBasedAlerts = transactions
                .flatMap(new FraudDetectionProcessor(alertThreshold))
                .name("Rule-Based Fraud Detection");

        // Velocity-based fraud detection (stateful)
        SingleOutputStreamOperator<FraudAlert> velocityAlerts = transactions
                .keyBy(Transaction::getAccountId)
                .process(new VelocityCheckProcessor())
                .name("Velocity Check");

        // Union all alert streams
        return ruleBasedAlerts.union(velocityAlerts);
    }

    /**
     * Gets environment variable or returns default value.
     */
    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
}

