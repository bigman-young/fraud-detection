package com.hsbc.fraud.sink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hsbc.fraud.model.AlertSeverity;
import com.hsbc.fraud.model.FraudAlert;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Alert sink implementations for outputting fraud alerts.
 * Supports Kafka, logging, and custom sink implementations.
 */
public class AlertSink {

    private static final Logger LOG = LoggerFactory.getLogger(AlertSink.class);

    /**
     * Creates a Kafka sink for fraud alerts.
     */
    public static KafkaSink<FraudAlert> createKafkaSink(String bootstrapServers, String topic) {
        return KafkaSink.<FraudAlert>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(new AlertKafkaSerializer(topic))
                .build();
    }

    /**
     * Kafka serialization schema for FraudAlert.
     */
    public static class AlertKafkaSerializer implements KafkaRecordSerializationSchema<FraudAlert> {

        private static final long serialVersionUID = 1L;
        private final String topic;
        private transient ObjectMapper objectMapper;

        public AlertKafkaSerializer(String topic) {
            this.topic = topic;
        }

        @Override
        public void open(SerializationSchema.InitializationContext context, KafkaSinkContext sinkContext) {
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        @Override
        public ProducerRecord<byte[], byte[]> serialize(FraudAlert alert, KafkaSinkContext context, Long timestamp) {
            try {
                if (objectMapper == null) {
                    objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                }
                
                byte[] key = alert.getTransaction().getAccountId().getBytes(StandardCharsets.UTF_8);
                byte[] value = objectMapper.writeValueAsBytes(alert);
                
                return new ProducerRecord<>(topic, null, timestamp, key, value);
            } catch (JsonProcessingException e) {
                LOG.error("Failed to serialize alert: {}", alert.getAlertId(), e);
                return null;
            }
        }
    }

    /**
     * Sink function that logs fraud alerts with appropriate severity levels.
     */
    public static class LoggingSink extends RichSinkFunction<FraudAlert> {

        private static final long serialVersionUID = 1L;
        private static final Logger ALERT_LOG = LoggerFactory.getLogger("FraudAlerts");
        private transient ObjectMapper objectMapper;

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
            super.open(parameters);
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        @Override
        public void invoke(FraudAlert alert, Context context) throws Exception {
            String alertJson = objectMapper.writeValueAsString(alert);

            // Log with appropriate severity
            switch (alert.getSeverity()) {
                case CRITICAL:
                    ALERT_LOG.error("[CRITICAL FRAUD ALERT] {}\n{}", formatAlertSummary(alert), alertJson);
                    break;
                case HIGH:
                    ALERT_LOG.warn("[HIGH SEVERITY ALERT] {}\n{}", formatAlertSummary(alert), alertJson);
                    break;
                case MEDIUM:
                    ALERT_LOG.warn("[MEDIUM SEVERITY ALERT] {}", formatAlertSummary(alert));
                    break;
                case LOW:
                    ALERT_LOG.info("[LOW SEVERITY ALERT] {}", formatAlertSummary(alert));
                    break;
            }
        }

        private String formatAlertSummary(FraudAlert alert) {
            return String.format(
                    "AlertID=%s | TxID=%s | Account=%s | Type=%s | Score=%.2f | Rules=%s",
                    alert.getAlertId(),
                    alert.getTransaction().getTransactionId(),
                    alert.getTransaction().getAccountId(),
                    alert.getAlertType(),
                    alert.getRiskScore(),
                    alert.getTriggeredRules()
            );
        }
    }

    /**
     * Sink function that sends alerts to an external webhook/API.
     */
    public static class WebhookSink extends RichSinkFunction<FraudAlert> {

        private static final long serialVersionUID = 1L;
        private static final Logger LOG = LoggerFactory.getLogger(WebhookSink.class);
        
        private final String webhookUrl;
        private final AlertSeverity minSeverity;
        private transient ObjectMapper objectMapper;

        public WebhookSink(String webhookUrl) {
            this(webhookUrl, AlertSeverity.MEDIUM);
        }

        public WebhookSink(String webhookUrl, AlertSeverity minSeverity) {
            this.webhookUrl = webhookUrl;
            this.minSeverity = minSeverity;
        }

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
            super.open(parameters);
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }

        @Override
        public void invoke(FraudAlert alert, Context context) throws Exception {
            // Only send alerts meeting minimum severity
            if (alert.getSeverity().getLevel() < minSeverity.getLevel()) {
                return;
            }

            // In production, this would make an HTTP call to the webhook
            // For demo purposes, we just log
            LOG.info("Would send alert {} to webhook {}", alert.getAlertId(), webhookUrl);
            
            // Example webhook call (commented out for demo):
            /*
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(alert)))
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            LOG.error("Failed to send alert: HTTP {}", response.statusCode());
                        }
                    });
            */
        }
    }
}

