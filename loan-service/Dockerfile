# Multi-stage — loan-service
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copy root pom and ALL module pom.xml files (required by Maven multi-module)
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY loan-service/pom.xml loan-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY report-service/pom.xml report-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

# Download dependencies using pom structure (cached layer)
RUN mvn dependency:go-offline -pl loan-service -am --no-transfer-progress -q

# Copy only the source needed to build this service
COPY common/src common/src
COPY loan-service/src loan-service/src

ENV MAVEN_OPTS="-Xmx384m -Xms128m"
RUN mvn install -pl loan-service -am --no-transfer-progress -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache curl && addgroup -S dlmp && adduser -S dlmp -G dlmp
USER dlmp
COPY --from=builder /workspace/loan-service/target/*.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=10s --retries=5 CMD curl -f http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=60.0","-jar","app.jar"]
