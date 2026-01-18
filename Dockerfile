# Multi-stage Dockerfile for Fraud Detection System

# Stage 1: Build Stage
FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies first (for better caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime Stage
FROM flink:1.18.1-java11

# Set working directory
WORKDIR /opt/flink

# Create job directory
RUN mkdir -p /opt/flink/job /opt/flink/logs

# Copy the built JAR from builder stage
COPY --from=builder /app/target/fraud-detection-system-1.0.0.jar /opt/flink/job/

# Copy configuration files
COPY src/main/resources/log4j2.xml /opt/flink/conf/
COPY src/main/resources/application.properties /opt/flink/conf/

# Set environment variables
ENV FLINK_HOME=/opt/flink
ENV PATH="${FLINK_HOME}/bin:${PATH}"

# Expose ports
# - 8081: Web UI
# - 6123: JobManager RPC
# - 6124: Blob Server
# - 6125: Query State Server
EXPOSE 8081 6123 6124 6125

# Create entrypoint script
COPY --chmod=755 docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["help"]

