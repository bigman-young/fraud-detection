#!/bin/bash

# Kubernetes deployment script for Fraud Detection System

set -e

echo "======================================"
echo "Deploying Fraud Detection System to Kubernetes"
echo "======================================"

# Navigate to project root
cd "$(dirname "$0")/.."

# Check if kubectl is configured
if ! kubectl cluster-info &> /dev/null; then
    echo "Error: kubectl is not configured or cluster is not accessible"
    exit 1
fi

echo "Step 1: Creating namespaces..."
kubectl create namespace kafka --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/namespace.yaml

echo ""
echo "Step 2: Deploying Kafka (if needed)..."
kubectl apply -f k8s/kafka/kafka-deployment.yaml

echo ""
echo "Step 3: Waiting for Kafka to be ready..."
kubectl wait --for=condition=ready pod -l app=kafka -n kafka --timeout=120s || true

echo ""
echo "Step 4: Deploying fraud detection system..."
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

echo ""
echo "Step 5: Waiting for Flink cluster to be ready..."
kubectl wait --for=condition=ready pod -l app=fraud-detection-system,component=jobmanager -n fraud-detection --timeout=180s

echo ""
echo "Step 6: Submitting Flink job..."
kubectl apply -f k8s/flink-job-submission.yaml

echo ""
echo "======================================"
echo "Deployment completed!"
echo "======================================"
echo ""
echo "To access Flink Web UI:"
echo "  kubectl port-forward svc/fraud-detection-jobmanager-rest 8081:8081 -n fraud-detection"
echo "  Then open: http://localhost:8081"
echo ""
echo "To view logs:"
echo "  kubectl logs -f deployment/fraud-detection-jobmanager -n fraud-detection"
echo ""
echo "To check status:"
echo "  kubectl get pods -n fraud-detection"

