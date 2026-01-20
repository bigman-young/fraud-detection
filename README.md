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
- [CI/CD with GitHub Actions](#cicd-with-github-actions)
- [Testing](#testing)
- [Monitoring](#monitoring)
- [Distributed Logging with GCP Cloud Logging](#distributed-logging-with-gcp-cloud-logging-stackdriver)
- [API Reference](#api-reference)
- [Design Decisions](#design-decisions)
- [Links & Resources](#links--resources)

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
                              │ Google Pub/Sub   │
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
│  │  │  Pub/Sub    │───▶│  Rule-Based     │───▶│    Alert Generator    │  │  │
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
                              │ Google Pub/Sub   │
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
| **Pub/Sub Source** | Ingests transaction events from Google Cloud Pub/Sub subscription |
| **Rule-Based Processor** | Evaluates transactions against configurable rules |
| **Velocity Processor** | Stateful processor tracking transaction frequency |
| **CEP Pattern Detector** | Detects complex fraud patterns across events |
| **Alert Generator** | Creates and routes fraud alerts to Pub/Sub topic |

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

- **Java 17+**
- **Maven 3.6+**
- **Docker & Docker Compose** (for local development)
- **Google Cloud Platform (GCP)** account with:
  - Google Cloud Pub/Sub enabled
  - Google Kubernetes Engine (GKE) cluster
  - Artifact Registry for Docker images
- **kubectl** configured for your GKE cluster
- **gcloud** CLI installed and configured

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
   - Open [Flink Web UI](http://34.81.167.161:8081/#/overview) in your browser

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
| `SOURCE_TYPE` | Data source type: `PUBSUB`, `KAFKA`, or `DEMO` | `PUBSUB` |
| `GOOGLE_CLOUD_PROJECT` | GCP Project ID | `hsbc-484505` |
| `PUBSUB_SUBSCRIPTION` | Pub/Sub subscription for transactions | `transactions-sub` |
| `PUBSUB_ALERT_TOPIC` | Pub/Sub topic for fraud alerts | `fraud-alerts` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses (if using Kafka) | `10.113.192.45:9092` |
| `INPUT_TOPIC` | Input transactions topic (if using Kafka) | `transactions` |
| `ALERT_TOPIC` | Output alerts topic (if using Kafka) | `fraud-alerts` |
| `ALERT_THRESHOLD` | Minimum risk score for alerts | `0.4` |
| `PARALLELISM` | Flink parallelism | `1` |

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

### GKE Deployment

#### Prerequisites

1. **Create Pub/Sub topics and subscriptions:**
```bash
# Create transactions topic
gcloud pubsub topics create transactions --project=hsbc-484505

# Create transactions subscription
gcloud pubsub subscriptions create transactions-sub \
  --topic=transactions \
  --project=hsbc-484505

# Create fraud-alerts topic
gcloud pubsub topics create fraud-alerts --project=hsbc-484505
```

2. **Deploy Flink Cluster:**
```bash
# Deploy Flink cluster (JobManager + TaskManager)
kubectl apply -f k8s/gke/namespace.yaml
kubectl apply -f k8s/gke/flink/flink-configmap.yaml
kubectl apply -f k8s/gke/flink/flink-serviceaccount.yaml
kubectl apply -f k8s/gke/flink/flink-jobmanager.yaml
kubectl apply -f k8s/gke/flink/flink-taskmanager.yaml
```

3. **Deploy Transaction Producer:**
```bash
kubectl apply -f k8s/gke/configmap.yaml
kubectl apply -f k8s/gke/workload-identity.yaml
kubectl apply -f k8s/gke/deployment.yaml
```

4. **Submit Flink Job:**
```bash
kubectl apply -f k8s/gke/flink-job-configmap.yaml
kubectl apply -f k8s/gke/flink/flink-job-submit.yaml
```

5. **Verify deployment:**
```bash
kubectl get pods -n fraud
kubectl get svc -n fraud
```

### Accessing Services

| Service | Access Method |
|---------|---------------|
| Flink Web UI | See [Flink Web UI](#flink-web-ui) section below |
| Transaction Producer Logs | `kubectl logs -f deployment/transaction-producer -n fraud` |
| Flink JobManager Logs | `kubectl logs -f deployment/flink-jobmanager -n fraud` |
| Flink TaskManager Logs | `kubectl logs -f deployment/flink-taskmanager -n fraud` |

## 🔄 CI/CD with GitHub Actions

This project includes comprehensive GitHub Actions workflows for automated CI/CD:

### Available Workflows

| Workflow | Description | Trigger |
|----------|-------------|---------|
| **test-reports.yml** | Runs unit tests, generates test reports and code coverage, deploys to GitHub Pages | Push to main/master, PR |
| **deploy-producer.yml** | Builds and deploys Transaction Producer to GKE | Manual trigger |
| **deploy-flink-cluster.yml** | Deploys Flink cluster (JobManager + TaskManager) to GKE | Manual trigger |
| **submit-flink-job.yml** | Submits Flink job to existing Flink cluster | Manual trigger |
| **deploy-all.yml** | Deploys both Producer and Flink Job | Manual trigger |
| **integration-tests.yml** | Runs integration tests (Pub/Sub, Logging, E2E) | Manual trigger |
| **deploy-to-gke.yml** | Legacy workflow for producer deployment | Manual trigger |

### Workflow Details

#### Test Reports & Coverage
- **Location**: `.github/workflows/test-reports.yml`
- **Functionality**:
  - Runs Maven unit tests
  - Generates HTML test reports (Surefire)
  - Generates code coverage reports (JaCoCo)
  - Publishes reports to GitHub Pages
- **Access**: Reports are available at [Test Reports](#links--resources)

#### Deployment Workflows
- **Prerequisites**: 
  - GCP Project ID: `hsbc-484505`
  - GKE Cluster: `flink-cluster` (for Flink) or `hsbc-cluster-1` (for Producer)
  - Required GitHub Secrets configured
- **Manual Trigger**: Go to Actions → Select workflow → Run workflow

### Required GitHub Secrets

| Secret Name | Description |
|-------------|-------------|
| `GCP_PROJECT_ID` | GCP Project ID (e.g., `hsbc-484505`) |
| `GKE_CLUSTER` | GKE Cluster name |
| `GKE_REGION` | GKE Cluster region (e.g., `asia-east1`) |
| `PRODUCER_SA_KEY` | GCP Service Account Key JSON for Producer |
| `FLINK_SA_KEY` | GCP Service Account Key JSON for Flink |

### Workflow Usage

1. **View all workflows**: Go to repository → Actions tab
2. **Run a workflow**: Actions → Select workflow → Run workflow → Configure inputs → Run
3. **View workflow logs**: Actions → Select workflow run → View logs
4. **Download artifacts**: Actions → Select workflow run → Artifacts section

For detailed workflow documentation, see the workflow files in `.github/workflows/`.

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

#### Using Pub/Sub (Production)

1. **Publish test transaction to Pub/Sub:**
```bash
gcloud pubsub topics publish transactions \
  --message='{"transaction_id":"TX-TEST-001","account_id":"BLACK-001","target_account_id":"ACC-999","amount":50000,"currency":"USD","transaction_type":"TRANSFER","timestamp":"2024-01-15T10:30:00.000Z","country_code":"ZZ","channel":"ONLINE"}' \
  --project=hsbc-484505
```

2. **View alerts from Pub/Sub:**
```bash
gcloud pubsub subscriptions pull fraud-alerts-sub \
  --limit=10 \
  --project=hsbc-484505
```

#### Using Demo Mode (Local)

1. **Run Flink job in demo mode:**
```bash
mvn clean compile exec:java \
  -Dexec.mainClass="com.hsbc.fraud.FraudDetectionJob" \
  -Dexec.args=""
```

The demo mode generates test transactions automatically.

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
| **Google Pub/Sub Integration** | Provides durability, replay capability, decoupling, and cloud-native integration |
| **Kubernetes Deployment** | Enables auto-scaling, rolling updates, and high availability |
| **GKE Deployment** | Leverages Google Cloud's managed Kubernetes for reliability and scalability |

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
- **GCP Cloud Logging**: Distributed logging and log analysis

## 📝 Distributed Logging with GCP Cloud Logging (Stackdriver)

This system implements a cloud-native distributed logging mechanism using **Google Cloud Logging** (formerly Stackdriver Logging) for centralized log collection, storage, and analysis across all components.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FRAUD DETECTION SYSTEM                    │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Transaction │  │   Flink      │  │   Flink      │     │
│  │  Producer    │  │ JobManager   │  │ TaskManager │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                 │                 │              │
│         └─────────────────┼─────────────────┘              │
│                           │                                │
│                    stdout/stderr                          │
└───────────────────────────┼────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  GKE Logging    │
                    │    Agent        │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ GCP Cloud       │
                    │ Logging         │
                    │ (Stackdriver)   │
                    └─────────────────┘
                             │
                ┌────────────┼────────────┐
                ▼            ▼            ▼
        ┌───────────┐ ┌───────────┐ ┌───────────┐
        │  Logs     │ │  Logs     │ │  Logs     │
        │ Explorer  │ │  Viewer   │ │  Queries  │
        └───────────┘ └───────────┘ └───────────┘
```

### How It Works

#### 1. Automatic Log Collection

When deployed on **Google Kubernetes Engine (GKE)**, logs are automatically collected:

- **Application Logs**: All `stdout` and `stderr` output from containers
- **Kubernetes Logs**: Pod events, container lifecycle events
- **System Logs**: Node-level logs and events

**No additional configuration required!** GKE's logging agent automatically collects and forwards logs to Cloud Logging.

#### 2. Log Output Configuration

The system uses **SLF4J** with **Log4j2** for logging:

- **Console Appender**: Outputs to `stdout` (collected by GKE)
- **Pattern Layout**: Structured log format with timestamps, levels, and context
- **Log Levels**: Configurable per component (INFO, WARN, ERROR)

**Log4j Configuration** (`log4j-console.properties`):
```properties
rootLogger.level = INFO
rootLogger.appenderRef.console.ref = ConsoleAppender

appender.console.name = ConsoleAppender
appender.console.type = CONSOLE
appender.console.layout.type = PatternLayout
appender.console.layout.pattern = %d{yyyy-MM-dd HH:mm:ss,SSS} %-5p %-60c %x - %m%n
```

#### 3. Structured Logging

The system implements structured logging with:

- **Log Levels**: INFO, WARN, ERROR for different severity levels
- **Context Information**: Transaction IDs, Account IDs, Alert IDs
- **Structured Metadata**: Labels and fields for filtering and analysis

**Example Log Entry**:
```
2026-01-19 10:30:45,123 INFO  com.hsbc.fraud.processor.FraudDetectionProcessor - 
Fraud alert generated: alertId=ALERT-001, accountId=ACC-123, 
riskScore=0.85, alertType=HIGH_VALUE_TRANSACTION
```

### Viewing Logs

#### Method 1: GCP Cloud Console

1. **Navigate to Cloud Logging**:
   ```
   https://console.cloud.google.com/logs/query?project=hsbc-484505
   ```

2. **Filter by Resource**:
   ```
   resource.type="k8s_container"
   resource.labels.namespace_name="fraud"
   resource.labels.container_name="transaction-producer"
   ```

3. **Filter by Component**:
   ```
   resource.type="k8s_container"
   resource.labels.pod_name=~"flink-jobmanager.*"
   ```

#### Method 2: Using gcloud CLI

```bash
# View Transaction Producer logs
gcloud logging read "resource.type=k8s_container AND \
  resource.labels.namespace_name=fraud AND \
  resource.labels.container_name=transaction-producer" \
  --project=hsbc-484505 \
  --limit=50 \
  --format=json

# View Flink JobManager logs
gcloud logging read "resource.type=k8s_container AND \
  resource.labels.namespace_name=fraud AND \
  resource.labels.pod_name=~'flink-jobmanager.*'" \
  --project=hsbc-484505 \
  --limit=50

# View fraud alerts only
gcloud logging read "resource.type=k8s_container AND \
  resource.labels.namespace_name=fraud AND \
  jsonPayload.alertId:*" \
  --project=hsbc-484505 \
  --limit=50
```

#### Method 3: Using kubectl

```bash
# View Transaction Producer logs
kubectl logs -f deployment/transaction-producer -n fraud

# View Flink JobManager logs
kubectl logs -f deployment/flink-jobmanager -n fraud -c jobmanager

# View Flink TaskManager logs
kubectl logs -f deployment/flink-taskmanager -n fraud -c taskmanager

# View logs from all pods
kubectl logs -f -l app=flink -n fraud --all-containers=true
```

### Log Filtering and Queries

#### Common Log Queries

**1. Fraud Alerts Only**:
```
resource.type="k8s_container"
resource.labels.namespace_name="fraud"
jsonPayload.alertId:*
severity>=WARNING
```

**2. High Severity Errors**:
```
resource.type="k8s_container"
resource.labels.namespace_name="fraud"
severity="ERROR"
```

**3. Specific Transaction**:
```
resource.type="k8s_container"
resource.labels.namespace_name="fraud"
jsonPayload.transactionId="TX-12345"
```

**4. Flink Checkpoint Issues**:
```
resource.type="k8s_container"
resource.labels.namespace_name="fraud"
textPayload=~"checkpoint"
severity>=WARNING
```

**5. Recent Fraud Alerts (Last Hour)**:
```
resource.type="k8s_container"
resource.labels.namespace_name="fraud"
jsonPayload.alertId:*
timestamp>="2026-01-19T10:00:00Z"
```

### Log Retention and Export

#### Retention Policy

- **Default Retention**: 30 days (GCP Cloud Logging default)
- **Log Storage**: Automatically stored in Cloud Logging
- **Cost**: First 50 GB/month free, then pay-per-use

#### Export to BigQuery (Optional)

For long-term analysis and advanced queries:

```bash
# Create log sink to BigQuery
gcloud logging sinks create fraud-detection-logs \
  bigquery.googleapis.com/projects/hsbc-484505/datasets/logs \
  --log-filter='resource.type="k8s_container" AND \
    resource.labels.namespace_name="fraud"' \
  --project=hsbc-484505
```

### Log Levels and Configuration

| Component | Default Level | Description |
|-----------|--------------|-------------|
| **Application** | INFO | General application logs |
| **Fraud Alerts** | INFO | All fraud alert generation |
| **Flink Framework** | WARN | Flink internal logs (reduced verbosity) |
| **Kafka/Pub/Sub** | WARN | Connection and error logs only |

**Configure Log Levels**:

```bash
# Via environment variable
kubectl set env deployment/transaction-producer \
  LOG_LEVEL=DEBUG -n fraud

# Via ConfigMap (for Flink)
kubectl edit configmap flink-config -n fraud
# Edit log4j-console.properties section
```

### Integration Testing

The system includes integration tests for Cloud Logging:

```bash
# Run logging integration tests
mvn test -Dtest=LoggingIntegrationTest -Pintegration-tests

# Or via GitHub Actions
# Actions → integration-tests → Run workflow → Select "logging" tests
```

**Test Coverage**:
- ✅ Writing logs to Cloud Logging
- ✅ Reading logs from Cloud Logging
- ✅ Log severity level mapping
- ✅ Structured logging with labels
- ✅ Error handling and graceful degradation

### Best Practices

1. **Use Structured Logging**: Include context (transaction IDs, account IDs) in log messages
2. **Appropriate Log Levels**: Use INFO for normal operations, WARN for warnings, ERROR for errors
3. **Avoid Sensitive Data**: Never log passwords, credit card numbers, or PII
4. **Log Aggregation**: Use Cloud Logging for centralized log management
5. **Log Queries**: Create saved queries for common log searches
6. **Alerts**: Set up log-based alerts for critical errors

### Troubleshooting

#### Logs Not Appearing

1. **Check GKE Logging Agent**:
   ```bash
   kubectl get daemonset -n kube-system | grep fluentd
   ```

2. **Verify Service Account Permissions**:
   ```bash
   gcloud projects get-iam-policy hsbc-484505 \
     --flatten="bindings[].members" \
     --filter="bindings.members:*compute@developer.gserviceaccount.com"
   ```

3. **Check Log Export**:
   ```bash
   gcloud logging read "resource.type=k8s_container" \
     --project=hsbc-484505 \
     --limit=1
   ```

#### High Log Volume

- Use log sampling for verbose components
- Adjust log levels to reduce verbosity
- Consider log export to BigQuery for archival
- Set up log-based metrics for monitoring

### Additional Resources

- **GCP Cloud Logging Documentation**: [Cloud Logging Docs](https://cloud.google.com/logging/docs)
- **Log Queries Guide**: [Log Query Syntax](https://cloud.google.com/logging/docs/view/logging-query-language)
- **Integration Tests**: `src/test/java/com/hsbc/fraud/integration/LoggingIntegrationTest.java`

## 🔒 Security Considerations

- Use Google Cloud IAM for Pub/Sub access control
- Use Kubernetes secrets for sensitive configuration
- Implement RBAC for Flink cluster access
- Use Workload Identity for GKE service account authentication
- Regular rotation of suspicious/blacklisted account lists
- Enable Pub/Sub message encryption in transit

## 🔗 Links & Resources

### Test Reports & Coverage
- **Test Reports**: [View Test Reports](https://bigman-young.github.io/fraud-detection/test-reports/)
- **Code Coverage**: Available in the test reports page

### Flink Web UI
- **Access Method**: 
  ```bash
  # Get LoadBalancer external IP
  kubectl get svc flink-jobmanager-webui -n fraud
  
  # Access via: http://<EXTERNAL-IP>:8081
  # Or use port-forward:
  kubectl port-forward svc/flink-jobmanager 8081:8081 -n fraud
  # Then access: http://localhost:8081
  ```
- **Features**: Job monitoring, task metrics, checkpoint status, backpressure monitoring

### Resilience Testing
- **Documentation**: [Resilience Testing Guide](./resilience%20testing.md)
- **Covers**: Pod restarts, component failures, infrastructure instability, recovery scenarios

### Additional Resources
- **GitHub Actions**: [View Workflows](https://github.com/bigman-young/fraud-detection/actions)
- **GCP Console**: [Pub/Sub Topics](https://console.cloud.google.com/cloudpubsub/topic/list?project=hsbc-484505)
- **GKE Console**: [Cluster Management](https://console.cloud.google.com/kubernetes/clusters?project=hsbc-484505)

## 📝 License

This project is licensed under the MIT License.

---

## 📚 Additional Documentation

- [Resilience Testing](./resilience%20testing.md) - Comprehensive resilience testing guide
- [Test Reports](https://bigman-young.github.io/fraud-detection/test-reports/) - Latest test execution and coverage reports
- [GitHub Actions Workflows](https://github.com/bigman-young/fraud-detection/actions) - CI/CD pipeline status

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

---

**Built with ❤️ using Apache Flink**

