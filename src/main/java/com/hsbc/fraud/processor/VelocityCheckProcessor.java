package com.hsbc.fraud.processor;

import com.hsbc.fraud.model.*;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateful processor that detects velocity-based fraud patterns.
 * Tracks transaction frequency per account and flags excessive transaction rates.
 */
public class VelocityCheckProcessor extends KeyedProcessFunction<String, Transaction, FraudAlert> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(VelocityCheckProcessor.class);

    private final int maxTransactionsPerWindow;
    private final Duration windowDuration;
    
    // State to track recent transaction timestamps
    private transient ValueState<List<Long>> recentTransactionsState;
    // State to track last transaction timestamp
    private transient ValueState<Long> lastTransactionState;

    public VelocityCheckProcessor() {
        this(5, Duration.ofMinutes(5));
    }

    public VelocityCheckProcessor(int maxTransactionsPerWindow, Duration windowDuration) {
        this.maxTransactionsPerWindow = maxTransactionsPerWindow;
        this.windowDuration = windowDuration;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        ValueStateDescriptor<List<Long>> recentTxDescriptor = new ValueStateDescriptor<>(
                "recentTransactions",
                Types.LIST(Types.LONG)
        );
        recentTransactionsState = getRuntimeContext().getState(recentTxDescriptor);

        ValueStateDescriptor<Long> lastTxDescriptor = new ValueStateDescriptor<>(
                "lastTransaction",
                Types.LONG
        );
        lastTransactionState = getRuntimeContext().getState(lastTxDescriptor);

        LOG.info("Velocity check processor initialized: max {} transactions per {} seconds",
                maxTransactionsPerWindow, windowDuration.getSeconds());
    }

    @Override
    public void processElement(Transaction transaction, Context ctx, Collector<FraudAlert> out) throws Exception {
        if (transaction == null || transaction.getTimestamp() == null) {
            return;
        }

        long currentTimestamp = transaction.getTimestamp().toEpochMilli();
        long windowStart = currentTimestamp - windowDuration.toMillis();

        // Get recent transactions
        List<Long> recentTransactions = recentTransactionsState.value();
        if (recentTransactions == null) {
            recentTransactions = new ArrayList<>();
        }

        // Remove old transactions outside the window
        recentTransactions.removeIf(ts -> ts < windowStart);

        // Check for rapid successive transactions
        Long lastTransaction = lastTransactionState.value();
        if (lastTransaction != null) {
            long timeSinceLastTx = currentTimestamp - lastTransaction;
            
            // Flag transactions within 1 second of each other
            if (timeSinceLastTx < 1000 && recentTransactions.size() >= 2) {
                FraudAlert alert = createRapidTransactionAlert(transaction, timeSinceLastTx);
                out.collect(alert);
                LOG.warn("Rapid successive transaction detected for account: {}", 
                        transaction.getAccountId());
            }
        }

        // Check velocity breach
        if (recentTransactions.size() >= maxTransactionsPerWindow) {
            FraudAlert alert = createVelocityBreachAlert(transaction, recentTransactions.size() + 1);
            out.collect(alert);
            LOG.warn("Velocity breach detected for account: {} - {} transactions in window",
                    transaction.getAccountId(), recentTransactions.size() + 1);
        }

        // Update state
        recentTransactions.add(currentTimestamp);
        recentTransactionsState.update(recentTransactions);
        lastTransactionState.update(currentTimestamp);

        // Set timer to clean up old state
        ctx.timerService().registerEventTimeTimer(currentTimestamp + windowDuration.toMillis() + 1000);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<FraudAlert> out) throws Exception {
        // Clean up old transactions from state
        List<Long> recentTransactions = recentTransactionsState.value();
        if (recentTransactions != null) {
            long windowStart = timestamp - windowDuration.toMillis();
            recentTransactions.removeIf(ts -> ts < windowStart);
            
            if (recentTransactions.isEmpty()) {
                recentTransactionsState.clear();
                lastTransactionState.clear();
            } else {
                recentTransactionsState.update(recentTransactions);
            }
        }
    }

    private FraudAlert createVelocityBreachAlert(Transaction transaction, int transactionCount) {
        double riskScore = Math.min(1.0, 0.6 + (transactionCount - maxTransactionsPerWindow) * 0.1);
        
        return FraudAlert.builder()
                .transaction(transaction)
                .alertType(AlertType.VELOCITY_BREACH)
                .severity(AlertSeverity.fromRiskScore(riskScore))
                .riskScore(riskScore)
                .addTriggeredRule("VELOCITY_CHECK")
                .description(String.format(
                        "Account %s has %d transactions in %d seconds (max allowed: %d)",
                        transaction.getAccountId(),
                        transactionCount,
                        windowDuration.getSeconds(),
                        maxTransactionsPerWindow))
                .status(AlertStatus.NEW)
                .build();
    }

    private FraudAlert createRapidTransactionAlert(Transaction transaction, long timeSinceLastTx) {
        return FraudAlert.builder()
                .transaction(transaction)
                .alertType(AlertType.RAPID_SUCCESSIVE_TRANSACTIONS)
                .severity(AlertSeverity.HIGH)
                .riskScore(0.8)
                .addTriggeredRule("RAPID_TRANSACTION_CHECK")
                .description(String.format(
                        "Rapid successive transaction detected for account %s - %d ms since last transaction",
                        transaction.getAccountId(),
                        timeSinceLastTx))
                .status(AlertStatus.NEW)
                .build();
    }

    public int getMaxTransactionsPerWindow() {
        return maxTransactionsPerWindow;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }
}

