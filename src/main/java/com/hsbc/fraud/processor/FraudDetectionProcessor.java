package com.hsbc.fraud.processor;

import com.hsbc.fraud.engine.FraudDetectionEngine;
import com.hsbc.fraud.model.FraudAlert;
import com.hsbc.fraud.model.Transaction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Flink processor function that applies fraud detection logic to transactions.
 * Uses RichFlatMapFunction for initialization and lifecycle management.
 */
public class FraudDetectionProcessor extends RichFlatMapFunction<Transaction, FraudAlert> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(FraudDetectionProcessor.class);

    private transient FraudDetectionEngine engine;
    private final double alertThreshold;

    public FraudDetectionProcessor() {
        this(0.4);
    }

    public FraudDetectionProcessor(double alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        this.engine = new FraudDetectionEngine(alertThreshold);
        LOG.info("Fraud detection processor initialized with threshold: {}", alertThreshold);
    }

    @Override
    public void flatMap(Transaction transaction, Collector<FraudAlert> out) throws Exception {
        if (transaction == null) {
            return;
        }

        long startTime = System.nanoTime();

        try {
            Optional<FraudAlert> alert = engine.evaluate(transaction);

            if (alert.isPresent()) {
                out.collect(alert.get());
                LOG.info("Fraud alert generated for transaction: {}", transaction.getTransactionId());
            }
        } catch (Exception e) {
            LOG.error("Error processing transaction {}: {}", 
                    transaction.getTransactionId(), e.getMessage(), e);
        } finally {
            long processingTime = (System.nanoTime() - startTime) / 1_000_000;
            if (processingTime > 100) {
                LOG.warn("Slow processing detected: {} ms for transaction {}", 
                        processingTime, transaction.getTransactionId());
            }
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        LOG.info("Fraud detection processor closed");
    }
}

