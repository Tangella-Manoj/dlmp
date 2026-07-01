# Multi-stage — user-service
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY user-service/pom.xml user-service/pom.xml
COPY user-service/src user-service/src
ENV MAVEN_OPTS="-Xmx384m -Xms128m"
RUN chmod +x mvnw && ./mvnw install -pl user-service -am --no-transfer-progress -DskipTests -e

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
VOLUME /tmp
RUN addgroup -S dlmp && adduser -S dlmp -G dlmp && apk add --no-cache curl
USER dlmp
COPY --from=builder /workspace/user-service/target/*.jar app.jar
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=10s --retries=5 CMD curl -f http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-XX:+UseG1GC","-Xms64m","-Xmx256m","-jar","app.jar"]
