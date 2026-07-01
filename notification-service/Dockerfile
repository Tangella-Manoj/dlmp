# Multi-stage — notification-service
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY notification-service/pom.xml notification-service/pom.xml
COPY notification-service/src notification-service/src
ENV MAVEN_OPTS="-Xmx384m -Xms128m"
RUN mvn install -pl notification-service -am --no-transfer-progress -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache curl && addgroup -S dlmp && adduser -S dlmp -G dlmp
USER dlmp
COPY --from=builder /workspace/notification-service/target/*.jar app.jar
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=10s --retries=5 CMD curl -f http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=60.0","-jar","app.jar"]
