#!/bin/bash
# DLMP v2.0 — Start all services locally (Docker infra must be running)
# Usage: ./scripts/start-services.sh [start|stop|status|logs <svc>]

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="/tmp/dlmp-logs"
PID_DIR="/tmp/dlmp-pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

# ─── Common env ──────────────────────────────────────────────────────────────
export MYSQL_USER=dlmp_user
export MYSQL_PASSWORD=dlmp_password
export MYSQL_SSL=false
export KAFKA_SERVERS=localhost:9092
export KAFKA_SECURITY_PROTOCOL=PLAINTEXT
export KAFKA_SASL_MECHANISM=PLAIN
export KAFKA_SASL_JAAS=""
export AIVEN_CA_CERT=""
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=""
export REDIS_SSL_ENABLED=false
export ZIPKIN_HOST=localhost
export MAIL_HOST=localhost
export MAIL_PORT=1025
export JWT_SECRET=dlmp-enterprise-jwt-secret-minimum-64-bytes-for-hs512-xxxxxxxxxxxxxxxxxxx
export INTERNAL_API_KEY=local-internal-key
export USER_SERVICE_URL=http://localhost:8081

JVM_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

declare -A SERVICES=(
  [user-service]="8081:dlmp_users:localhost:3306"
  [loan-service]="8082:dlmp_loans:localhost:3307"
  [payment-service]="8083:dlmp_payments:localhost:3308"
  [notification-service]="8084:dlmp_notifications:localhost:3309"
  [report-service]="8085:dlmp_reports:localhost:3310"
  [api-gateway]="8080:::"
)

# Service startup order (dependencies first)
START_ORDER=(user-service loan-service payment-service notification-service report-service api-gateway)

start_service() {
  local svc=$1
  local config="${SERVICES[$svc]}"
  local port="${config%%:*}"
  local rest="${config#*:}"
  local db="${rest%%:*}"
  local rest2="${rest#*:}"
  local dbhost="${rest2%%:*}"
  local dbport="${rest2#*:}"

  JAR="$BASE_DIR/$svc/target/$svc-2.0.0.jar"
  if [ ! -f "$JAR" ]; then
    echo "❌ JAR not found: $JAR — run: mvn package -pl $svc -am -DskipTests -q"
    return 1
  fi

  DB_URL=""
  if [ -n "$db" ]; then
    DB_URL="--spring.datasource.url=jdbc:mysql://$dbhost:$dbport/$db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
  fi

  echo "🚀 Starting $svc on :$port ..."
  nohup java $JVM_OPTS -jar "$JAR" \
    --server.port=$port \
    $DB_URL \
    > "$LOG_DIR/$svc.log" 2>&1 &

  local pid=$!
  echo $pid > "$PID_DIR/$svc.pid"
  echo "   PID=$pid | log: $LOG_DIR/$svc.log"
}

stop_service() {
  local svc=$1
  local pid_file="$PID_DIR/$svc.pid"
  if [ -f "$pid_file" ]; then
    local pid=$(cat "$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid"
      echo "🛑 Stopped $svc (PID=$pid)"
    fi
    rm -f "$pid_file"
  else
    pkill -f "$svc-2.0.0.jar" 2>/dev/null && echo "🛑 Stopped $svc" || echo "   $svc not running"
  fi
}

check_health() {
  local svc=$1
  local port="${SERVICES[$svc]%%:*}"
  local status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health" 2>/dev/null)
  if [ "$status" = "200" ]; then
    echo "  ✅ $svc (:$port) — UP"
  else
    echo "  ❌ $svc (:$port) — DOWN (HTTP $status)"
  fi
}

case "${1:-start}" in
  start)
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  DLMP v2.0 — Starting Services"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    for svc in "${START_ORDER[@]}"; do
      start_service "$svc"
      # Wait extra for user-service (others depend on it)
      [ "$svc" = "user-service" ] && echo "   ⏳ Waiting 25s for user-service to initialize..." && sleep 25
      sleep 3
    done
    echo ""
    echo "⏳ Waiting 40s for all services to initialize..."
    sleep 40
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  Health Check"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    for svc in "${START_ORDER[@]}"; do check_health "$svc"; done
    ;;
  stop)
    for svc in "${START_ORDER[@]}"; do stop_service "$svc"; done
    ;;
  status)
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    for svc in "${START_ORDER[@]}"; do check_health "$svc"; done
    ;;
  logs)
    svc="${2:-user-service}"
    tail -f "$LOG_DIR/$svc.log"
    ;;
  *)
    echo "Usage: $0 [start|stop|status|logs <service>]"
    ;;
esac
