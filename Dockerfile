# Multi-stage Docker build for Columnar Format Application

# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-11 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src
COPY SPEC.md .

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:11-jre-alpine

# Set working directory
WORKDIR /app

# Copy the JAR from build stage
COPY --from=build /app/target/columnar-format.jar /app/columnar-format.jar

# Copy SPEC.md for reference
COPY --from=build /app/SPEC.md /app/SPEC.md

# Create directory for data files
RUN mkdir -p /data

# Set volume for data files
VOLUME /data

# Default command shows help
ENTRYPOINT ["java", "-jar", "/app/columnar-format.jar"]
CMD ["--help"]

# Metadata
LABEL maintainer="GPP Task 2"
LABEL description="Columnar File Format Converter"
LABEL version="1.0.0"

# Health check (optional - checks if JAR is accessible)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD java -jar /app/columnar-format.jar --version || exit 1