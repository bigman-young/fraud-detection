# Real-Time Fraud Detection System

A high-performance, real-time fraud detection system built with Apache Flink, designed to analyze financial transactions and detect fraudulent activities with low latency.

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Deployment](#deployment)
- [Testing](#testing)
- [API Reference](#api-reference)
- [Design Decisions](#design-decisions)

## 🎯 Overview

This system provides real-time fraud detection capabilities for financial transactions using Apache Flink stream processing. It employs a combination of rule-based detection and Complex Event Processing (CEP) patterns to identify suspicious activities.

### Key Capabilities

- **Real-time Processing**: Sub-second latency for transaction analysis
- **Rule-based Detection**: Configurable fraud detection rules
- **CEP Patterns**: Complex pattern detection across transaction sequences
- **Scalable Architecture**: Horizontal scaling via Kubernetes
- **High Availability**: Built-in fault tolerance with Flink checkpointing

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        FRAUD DETECTION SYSTEM ARCHITECTURE                   │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌──────────────────┐
                              │   Kafka Topic    │
                              │  (transactions)  │
                              └────────┬─────────┘
                                       │
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                            APACHE FLINK CLUSTER                               │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                           JobManager                                    │  │
│  │  • Job Scheduling    • Checkpoint Coordination    • Resource Management│  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                    │                                          │
│          ┌─────────────────────────┼─────────────────────────┐               │
│          ▼                         ▼                         ▼               │
│  ┌───────────────┐        ┌───────────────┐        ┌───────────────┐        │
│  │  TaskManager  │        │  TaskManager  │        │  TaskManager  │        │
│  │   Slot 1-2    │        │   Slot 1-2    │        │   Slot 1-2    │        │
│  └───────────────┘        └───────────────┘        └───────────────┘        │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                      FRAUD DETECTION PIPELINE                          │  │
│  │                                                                        │  │
│  │  ┌─────────────┐    ┌─────────────────┐    ┌───────────────────────┐  │  │
│  │  │   Kafka     │───▶│  Rule-Based     │───▶│    Alert Generator    │  │  │
│  │  │   Source    │    │  Processor      │    │                       │  │  │
│  │  └─────────────┘    └─────────────────┘    └───────────────────────┘  │  │
│  │         │                                              │               │  │
│  │         │           ┌─────────────────┐                │               │  │
│  │         └──────────▶│   Velocity      │────────────────┤               │  │
│  │                     │   Processor     │                │               │  │
│  │                     │   (Stateful)    │                │               │  │
│  │                     └─────────────────┘                │               │  │
│  │         │                                              │               │  │
│  │         │           ┌─────────────────┐                │               │  │
│  │         └──────────▶│   CEP Pattern   │────────────────┘               │  │
│  │                     │   Detector      │                                │  │
│  │                     └─────────────────┘                                │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
                              ┌──────────────────┐
                              │   Kafka Topic    │
                              │  (fraud-alerts)  │
                              └──────────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    ▼                  ▼                  ▼
            ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
            │   Logging     │  │   Dashboard   │  │   External    │
            │   System      │  │   (Grafana)   │  │   Webhook     │
            └───────────────┘  └───────────────┘  └───────────────┘
```

### Component Overview

| Component | Description |
|-----------|-------------|
| **Kafka Source** | Ingests transaction events from Kafka topic |
| **Rule-Based Processor** | Evaluates transactions against configurable rules |
| **Velocity Processor** | Stateful processor tracking transaction frequency |
| **CEP Pattern Detector** | Detects complex fraud patterns across events |
| **Alert Generator** | Creates and routes fraud alerts |

## ✨ Features

### Fraud Detection Rules

| Rule | Description | Default Threshold |
|------|-------------|-------------------|
| **High Value Transaction** | Flags transactions exceeding amount threshold | $10,000 / $50,000 (critical) |
| **Suspicious Account** | Detects transactions from flagged accounts | Configurable list |
| **Geographic Anomaly** | Flags transactions from high-risk countries | Configurable list |
| **Time-Based Anomaly** | Detects transactions at unusual hours | 1:00 AM - 5:00 AM |
| **Cross-Border Transfer** | Monitors international wire transfers | $5,000 |
| **Velocity Breach** | Detects excessive transaction frequency | 5 txns / 5 minutes |

### CEP Patterns

- **Small-Then-Large Pattern**: Multiple small transactions followed by large purchase
- **Multi-Country Pattern**: Transactions from different countries within short timeframe
- **Repeated Amount Pattern**: Multiple identical amount transactions
- **Increasing Amount Pattern**: Gradually increasing transaction amounts

## 🔧 Prerequisites

- **Java 11+**
- **Maven 3.6+**
- **Docker & Docker Compose** (for local development)
- **Kubernetes cluster** (for production deployment)
- **kubectl** configured for your cluster

## 🚀 Quick Start

### Local Development with Docker Compose

1. **Clone and build the project:**
```bash
cd fraud-detection-system
mvn clean package -DskipTests
```

2. **Start the system with Docker Compose:**
```bash
docker-compose up -d
```

3. **Access Flink Web UI:**
   - Open http://localhost:8081 in your browser

4. **Generate test transactions:**
```bash
docker-compose --profile testing up transaction-generator
```

5. **View fraud alerts:**
```bash
docker-compose logs -f job-submission
```

### Local Development without Docker

1. **Start demo mode (no Kafka required):**
```bash
mvn clean compile exec:java \
  -Dexec.mainClass="com.hsbc.fraud.FraudDetectionJob" \
  -Dexec.args=""
```

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `INPUT_TOPIC` | Input transactions topic | `transactions` |
| `ALERT_TOPIC` | Output alerts topic | `fraud-alerts` |
| `USE_KAFKA` | Enable Kafka mode | `false` |
| `ALERT_THRESHOLD` | Minimum risk score for alerts | `0.4` |
| `PARALLELISM` | Flink parallelism | `4` |

### Application Properties

```properties
# Fraud Detection Configuration
fraud.detection.alert.threshold=0.4
fraud.detection.high.value.threshold=10000
fraud.detection.high.value.critical.threshold=50000
fraud.detection.cross.border.threshold=5000
fraud.detection.velocity.max.transactions=5
fraud.detection.velocity.window.seconds=300
```

## 📦 Deployment

### Kubernetes Deployment

1. **Create namespaces:**
```bash
kubectl create namespace kafka
kubectl apply -f k8s/namespace.yaml
```

2. **Deploy Kafka (if not using managed Kafka):**
```bash
kubectl apply -f k8s/kafka/kafka-deployment.yaml
```

3. **Deploy fraud detection system:**
```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
```

4. **Submit Flink job:**
```bash
kubectl apply -f k8s/flink-job-submission.yaml
```

5. **Verify deployment:**
```bash
kubectl get pods -n fraud-detection
kubectl get svc -n fraud-detection
```

### Accessing Services

| Service | Access Method |
|---------|---------------|
| Flink Web UI | `kubectl port-forward svc/fraud-detection-jobmanager-rest 8081:8081 -n fraud-detection` |
| Logs | `kubectl logs -f deployment/fraud-detection-jobmanager -n fraud-detection` |

## 🧪 Testing

### Running Unit Tests

```bash
mvn test
```

### Running Integration Tests

```bash
mvn verify
```

### Test Coverage

```bash
mvn test jacoco:report
# View report at target/site/jacoco/index.html
```

### Manual Testing

1. **Send test transaction to Kafka:**
```bash
kafka-console-producer --bootstrap-server localhost:9092 --topic transactions
```

2. **Enter JSON transaction:**
```json
{
  "transaction_id": "TX-TEST-001",
  "account_id": "BLACK-001",
  "target_account_id": "ACC-999",
  "amount": 50000,
  "currency": "USD",
  "transaction_type": "TRANSFER",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "country_code": "ZZ",
  "channel": "ONLINE"
}
```

3. **View alerts:**
```bash
kafka-console-consumer --bootstrap-server localhost:9092 --topic fraud-alerts --from-beginning
```

## 📊 Transaction Format

### Input Transaction Schema

```json
{
  "transaction_id": "string",
  "account_id": "string",
  "target_account_id": "string",
  "amount": "number",
  "currency": "string",
  "transaction_type": "enum[TRANSFER, WITHDRAWAL, DEPOSIT, PAYMENT, REFUND, PURCHASE, WIRE_TRANSFER, BILL_PAYMENT]",
  "timestamp": "ISO-8601 datetime",
  "merchant_id": "string (optional)",
  "location": "string (optional)",
  "country_code": "string",
  "ip_address": "string (optional)",
  "device_id": "string (optional)",
  "channel": "string"
}
```

### Output Alert Schema

```json
{
  "alert_id": "string",
  "transaction": { /* transaction object */ },
  "alert_type": "enum[HIGH_VALUE_TRANSACTION, SUSPICIOUS_ACCOUNT, VELOCITY_BREACH, ...]",
  "severity": "enum[LOW, MEDIUM, HIGH, CRITICAL]",
  "risk_score": "number (0.0 - 1.0)",
  "triggered_rules": ["array of rule names"],
  "description": "string",
  "created_at": "ISO-8601 datetime",
  "status": "enum[NEW, INVESTIGATING, CONFIRMED, FALSE_POSITIVE, RESOLVED, ESCALATED]"
}
```

## 🎨 Design Decisions

### Why Apache Flink?

1. **True Streaming**: Unlike micro-batch systems, Flink processes events one at a time with millisecond latency
2. **Stateful Processing**: Built-in state management for velocity checks and pattern detection
3. **Exactly-Once Semantics**: Ensures no duplicate alerts or missed transactions
4. **CEP Support**: Native support for complex event processing patterns
5. **Fault Tolerance**: Automatic checkpointing and recovery

### Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| **Rule-based Engine** | Provides transparency, auditability, and easy updates |
| **Separate Processors** | Allows independent scaling and maintenance |
| **Kafka Integration** | Provides durability, replay capability, and decoupling |
| **Kubernetes Deployment** | Enables auto-scaling, rolling updates, and high availability |

### Performance Considerations

- **Parallelism**: Configurable per operator for optimal resource utilization
- **State Backend**: RocksDB for large state with efficient checkpointing
- **Watermarks**: 5-second bounded out-of-orderness for late event handling
- **Horizontal Scaling**: TaskManagers auto-scale based on CPU/memory usage

## 📈 Monitoring

### Key Metrics

- Transaction processing rate (events/second)
- Processing latency (p50, p95, p99)
- Alert generation rate
- Checkpoint duration and size
- TaskManager resource utilization

### Recommended Monitoring Stack

- **Prometheus**: Metrics collection
- **Grafana**: Visualization and dashboards
- **ELK Stack**: Log aggregation and analysis

## 🔒 Security Considerations

- Enable Kafka SSL/TLS for secure data transmission
- Use Kubernetes secrets for sensitive configuration
- Implement RBAC for Flink cluster access
- Regular rotation of suspicious/blacklisted account lists

## 📝 License

This project is licensed under the MIT License.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

---

**Built with ❤️ using Apache Flink**

