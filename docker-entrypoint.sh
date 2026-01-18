#!/bin/bash

set -e

# Function to wait for a service to be available
wait_for_service() {
    local host=$1
    local port=$2
    local timeout=${3:-60}
    
    echo "Waiting for $host:$port..."
    for i in $(seq 1 $timeout); do
        if nc -z "$host" "$port" 2>/dev/null; then
            echo "$host:$port is available"
            return 0
        fi
        sleep 1
    done
    echo "Timeout waiting for $host:$port"
    return 1
}

# Set default Flink configuration
export FLINK_CONF_DIR="${FLINK_CONF_DIR:-/opt/flink/conf}"

case "$1" in
    jobmanager)
        echo "Starting Flink JobManager..."
        exec /opt/flink/bin/jobmanager.sh start-foreground
        ;;
    taskmanager)
        echo "Starting Flink TaskManager..."
        # Wait for JobManager to be available
        wait_for_service "${JOBMANAGER_HOST:-fraud-detection-jobmanager}" 6123 120
        exec /opt/flink/bin/taskmanager.sh start-foreground
        ;;
    standalone)
        echo "Starting Flink in standalone mode..."
        /opt/flink/bin/start-cluster.sh
        echo "Submitting fraud detection job..."
        /opt/flink/bin/flink run -d \
            -c com.hsbc.fraud.FraudDetectionJob \
            /opt/flink/job/fraud-detection-system-1.0.0.jar
        echo "Job submitted. Tailing logs..."
        tail -f /opt/flink/log/*.log
        ;;
    submit)
        echo "Submitting Flink job..."
        shift
        exec /opt/flink/bin/flink run \
            -c com.hsbc.fraud.FraudDetectionJob \
            /opt/flink/job/fraud-detection-system-1.0.0.jar \
            "$@"
        ;;
    help)
        echo "Usage: docker run [image] [command]"
        echo ""
        echo "Commands:"
        echo "  jobmanager  - Start Flink JobManager"
        echo "  taskmanager - Start Flink TaskManager"
        echo "  standalone  - Start Flink cluster and submit job"
        echo "  submit      - Submit job to existing cluster"
        echo "  help        - Show this help message"
        ;;
    *)
        exec "$@"
        ;;
esac

