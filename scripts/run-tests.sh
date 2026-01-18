#!/bin/bash

# Test runner script for Fraud Detection System

set -e

echo "======================================"
echo "Running Fraud Detection System Tests"
echo "======================================"

# Navigate to project root
cd "$(dirname "$0")/.."

# Run unit tests
echo "Step 1: Running unit tests..."
mvn test

echo ""
echo "Step 2: Generating test coverage report..."
mvn jacoco:report || echo "JaCoCo not configured, skipping coverage report"

echo ""
echo "======================================"
echo "All tests passed!"
echo "======================================"

# Check if coverage report exists
if [ -f "target/site/jacoco/index.html" ]; then
    echo ""
    echo "Coverage report available at:"
    echo "  target/site/jacoco/index.html"
fi

