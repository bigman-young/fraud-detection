package com.hsbc.fraud.cep;

import com.hsbc.fraud.model.*;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Complex Event Processing (CEP) based fraud pattern detector.
 * Uses Flink CEP to detect complex fraud patterns across multiple transactions.
 */
public class FraudPatternDetector {

    private static final Logger LOG = LoggerFactory.getLogger(FraudPatternDetector.class);

    /**
     * Detects rapid small transactions followed by a large transaction.
     * This pattern is common in testing stolen cards before making large purchases.
     */
    public static DataStream<FraudAlert> detectSmallThenLargePattern(
            DataStream<Transaction> transactions) {

        Pattern<Transaction, ?> pattern = Pattern.<Transaction>begin("small")
                .where(new SimpleCondition<Transaction>() {
                    @Override
                    public boolean filter(Transaction tx) {
                        return tx.getAmount() != null && 
                               tx.getAmount().compareTo(new BigDecimal("50")) < 0;
                    }
                })
                .timesOrMore(3)
                .within(Time.minutes(5))
                .followedBy("large")
                .where(new SimpleCondition<Transaction>() {
                    @Override
                    public boolean filter(Transaction tx) {
                        return tx.getAmount() != null && 
                               tx.getAmount().compareTo(new BigDecimal("1000")) >= 0;
                    }
                });

        PatternStream<Transaction> patternStream = CEP.pattern(
                transactions.keyBy(Transaction::getAccountId), 
                pattern
        );

        return patternStream.select(new PatternSelectFunction<Transaction, FraudAlert>() {
            @Override
            public FraudAlert select(Map<String, List<Transaction>> pattern) {
                List<Transaction> smallTransactions = pattern.get("small");
                Transaction largeTransaction = pattern.get("large").get(0);

                return FraudAlert.builder()
                        .transaction(largeTransaction)
                        .alertType(AlertType.UNUSUAL_PATTERN)
                        .severity(AlertSeverity.HIGH)
                        .riskScore(0.85)
                        .addTriggeredRule("SMALL_THEN_LARGE_PATTERN")
                        .description(String.format(
                                "Pattern detected: %d small transactions followed by large transaction of %s %s",
                                smallTransactions.size(),
                                largeTransaction.getAmount(),
                                largeTransaction.getCurrency()))
                        .status(AlertStatus.NEW)
                        .build();
            }
        });
    }

    /**
     * Detects transactions from multiple countries in a short time period.
     * This is physically impossible and indicates compromised credentials.
     */
    public static DataStream<FraudAlert> detectMultiCountryPattern(
            DataStream<Transaction> transactions) {

        Pattern<Transaction, ?> pattern = Pattern.<Transaction>begin("first")
                .where(new SimpleCondition<Transaction>() {
                    @Override
                    public boolean filter(Transaction tx) {
                        return tx.getCountryCode() != null;
                    }
                })
                .followedBy("second")
                .where(new SimpleCondition<Transaction>() {
                    @Override
                    public boolean filter(Transaction tx) {
                        return tx.getCountryCode() != null;
                    }
                })
                .within(Time.minutes(10));

        PatternStream<Transaction> patternStream = CEP.pattern(
                transactions.keyBy(Transaction::getAccountId),
                pattern
        );

        return patternStream.select(new PatternSelectFunction<Transaction, FraudAlert>() {
            @Override
            public FraudAlert select(Map<String, List<Transaction>> pattern) {
                Transaction first = pattern.get("first").get(0);
                Transaction second = pattern.get("second").get(0);

                // Only alert if countries are different
                if (first.getCountryCode().equals(second.getCountryCode())) {
                    return null;
                }

                return FraudAlert.builder()
                        .transaction(second)
                        .alertType(AlertType.GEOGRAPHIC_ANOMALY)
                        .severity(AlertSeverity.CRITICAL)
                        .riskScore(0.95)
                        .addTriggeredRule("MULTI_COUNTRY_PATTERN")
                        .description(String.format(
                                "Impossible travel detected: transaction from %s followed by %s within 10 minutes",
                                first.getCountryCode(),
                                second.getCountryCode()))
                        .status(AlertStatus.NEW)
                        .build();
            }
        }).filter(alert -> alert != null);
    }

    /**
     * Detects multiple failed transactions followed by a successful one.
     * Common pattern when fraudsters are guessing card details.
     */
    public static DataStream<FraudAlert> detectRepeatedAmountPattern(
            DataStream<Transaction> transactions) {

        Pattern<Transaction, ?> pattern = Pattern.<Transaction>begin("repeated")
                .where(new SimpleCondition<Transaction>() {
                    @Override
                    public boolean filter(Transaction tx) {
                        return tx.getAmount() != null;
                    }
                })
                .timesOrMore(3)
                .consecutive()
                .within(Time.minutes(2));

        PatternStream<Transaction> patternStream = CEP.pattern(
                transactions.keyBy(Transaction::getAccountId),
                pattern
        );

        return patternStream.select(new PatternSelectFunction<Transaction, FraudAlert>() {
            @Override
            public FraudAlert select(Map<String, List<Transaction>> pattern) {
                List<Transaction> repeatedTx = pattern.get("repeated");
                
                // Check if amounts are all the same (suspicious pattern)
                BigDecimal firstAmount = repeatedTx.get(0).getAmount();
                boolean allSameAmount = repeatedTx.stream()
                        .allMatch(tx -> tx.getAmount().compareTo(firstAmount) == 0);

                if (!allSameAmount) {
                    return null;
                }

                Transaction lastTx = repeatedTx.get(repeatedTx.size() - 1);

                return FraudAlert.builder()
                        .transaction(lastTx)
                        .alertType(AlertType.UNUSUAL_PATTERN)
                        .severity(AlertSeverity.MEDIUM)
                        .riskScore(0.65)
                        .addTriggeredRule("REPEATED_AMOUNT_PATTERN")
                        .description(String.format(
                                "Repeated same amount transactions detected: %d transactions of %s %s",
                                repeatedTx.size(),
                                firstAmount,
                                lastTx.getCurrency()))
                        .status(AlertStatus.NEW)
                        .build();
            }
        }).filter(alert -> alert != null);
    }

    /**
     * Detects gradual increase in transaction amounts.
     * Pattern used to test account limits.
     */
    public static DataStream<FraudAlert> detectIncreasingAmountPattern(
            DataStream<Transaction> transactions) {

        Pattern<Transaction, ?> pattern = Pattern.<Transaction>begin("increasing")
                .where(new SimpleCondition<Transaction>() {
                    @Override
                    public boolean filter(Transaction tx) {
                        return tx.getAmount() != null;
                    }
                })
                .timesOrMore(4)
                .within(Time.hours(1));

        PatternStream<Transaction> patternStream = CEP.pattern(
                transactions.keyBy(Transaction::getAccountId),
                pattern
        );

        return patternStream.select(new PatternSelectFunction<Transaction, FraudAlert>() {
            @Override
            public FraudAlert select(Map<String, List<Transaction>> pattern) {
                List<Transaction> txList = pattern.get("increasing");

                // Check if amounts are consistently increasing
                boolean isIncreasing = true;
                for (int i = 1; i < txList.size(); i++) {
                    if (txList.get(i).getAmount().compareTo(txList.get(i - 1).getAmount()) <= 0) {
                        isIncreasing = false;
                        break;
                    }
                }

                if (!isIncreasing) {
                    return null;
                }

                Transaction lastTx = txList.get(txList.size() - 1);
                BigDecimal totalAmount = txList.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                return FraudAlert.builder()
                        .transaction(lastTx)
                        .alertType(AlertType.UNUSUAL_PATTERN)
                        .severity(AlertSeverity.MEDIUM)
                        .riskScore(0.6)
                        .addTriggeredRule("INCREASING_AMOUNT_PATTERN")
                        .description(String.format(
                                "Increasing amount pattern detected: %d transactions totaling %s %s",
                                txList.size(),
                                totalAmount,
                                lastTx.getCurrency()))
                        .status(AlertStatus.NEW)
                        .build();
            }
        }).filter(alert -> alert != null);
    }
}

