FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY common/pom.xml common/pom.xml
COPY common/src common/src
COPY report-service/pom.xml report-service/pom.xml
COPY report-service/src report-service/src
RUN chmod +x mvnw && ./mvnw install -pl report-service -am --no-transfer-progress -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
VOLUME /tmp
RUN addgroup -S dlmp && adduser -S dlmp -G dlmp && apk add --no-cache curl
USER dlmp
COPY --from=builder /workspace/report-service/target/*.jar app.jar
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=10s --retries=5 CMD curl -f http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-XX:+UseG1GC","-Xms256m","-Xmx512m","-jar","app.jar"]
