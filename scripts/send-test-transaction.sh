#!/bin/bash

# Script to send test transactions to Kafka

set -e

KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP_SERVERS:-localhost:29092}
TOPIC=${INPUT_TOPIC:-transactions}

# Sample fraudulent transaction (blacklisted account + high value + blocked country)
FRAUD_TX='{
  "transaction_id": "TX-FRAUD-001",
  "account_id": "BLACK-001",
  "target_account_id": "ACC-999",
  "amount": 75000,
  "currency": "USD",
  "transaction_type": "WIRE_TRANSFER",
  "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'",
  "country_code": "ZZ",
  "channel": "ONLINE",
  "ip_address": "192.168.1.100",
  "device_id": "DEV-UNKNOWN"
}'

# Sample normal transaction
NORMAL_TX='{
  "transaction_id": "TX-NORMAL-001",
  "account_id": "ACC-001",
  "target_account_id": "ACC-002",
  "amount": 150,
  "currency": "USD",
  "transaction_type": "PAYMENT",
  "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'",
  "country_code": "US",
  "channel": "MOBILE",
  "merchant_id": "MERCH-001",
  "ip_address": "10.0.0.1"
}'

# Sample suspicious transaction (high value)
HIGH_VALUE_TX='{
  "transaction_id": "TX-HIGH-001",
  "account_id": "ACC-003",
  "target_account_id": "ACC-004",
  "amount": 55000,
  "currency": "USD",
  "transaction_type": "TRANSFER",
  "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'",
  "country_code": "US",
  "channel": "ONLINE"
}'

echo "Sending test transactions to $KAFKA_BOOTSTRAP topic: $TOPIC"
echo ""

send_transaction() {
    local tx=$1
    local name=$2
    echo "Sending $name..."
    echo "$tx" | docker exec -i kafka kafka-console-producer --bootstrap-server kafka:9092 --topic $TOPIC 2>/dev/null \
        || echo "$tx" | kafka-console-producer.sh --bootstrap-server $KAFKA_BOOTSTRAP --topic $TOPIC 2>/dev/null \
        || echo "Failed to send transaction. Is Kafka running?"
    echo "Sent!"
    echo ""
}

case "${1:-all}" in
    fraud)
        send_transaction "$FRAUD_TX" "Fraudulent transaction"
        ;;
    normal)
        send_transaction "$NORMAL_TX" "Normal transaction"
        ;;
    high-value)
        send_transaction "$HIGH_VALUE_TX" "High-value transaction"
        ;;
    all)
        send_transaction "$NORMAL_TX" "Normal transaction"
        sleep 1
        send_transaction "$HIGH_VALUE_TX" "High-value transaction"
        sleep 1
        send_transaction "$FRAUD_TX" "Fraudulent transaction"
        ;;
    *)
        echo "Usage: $0 [fraud|normal|high-value|all]"
        exit 1
        ;;
esac

echo "Done! Check fraud-alerts topic for alerts."

