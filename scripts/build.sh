#!/bin/bash

# Build script for Fraud Detection System

set -e

echo "======================================"
echo "Building Fraud Detection System"
echo "======================================"

# Navigate to project root
cd "$(dirname "$0")/.."

# Clean and build
echo "Step 1: Running Maven build..."
mvn clean package -DskipTests

echo ""
echo "Step 2: Building Docker image..."
docker build -t fraud-detection-system:1.0.0 .

echo ""
echo "======================================"
echo "Build completed successfully!"
echo "======================================"
echo ""
echo "To run locally:"
echo "  docker-compose up -d"
echo ""
echo "To deploy to Kubernetes:"
echo "  kubectl apply -f k8s/"

