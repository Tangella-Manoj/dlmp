#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  DLMP v2.0 — Production Deployment Script
#  Run on your VPS/server after SSH access.
#  Prerequisites: Docker, Docker Compose, Git installed on server.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DOMAIN="${DOMAIN:-yourdomain.com}"
APP_DIR="${APP_DIR:-/opt/dlmp}"
COMPOSE_FILE="docker-compose.prod.yml"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  DLMP v2.0 — Production Deploy"
echo "  Domain: $DOMAIN"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ─── 1. Pre-flight checks ────────────────────────────────────────────────────
if [ ! -f ".env" ]; then
  echo "❌ .env file not found. Copy .env.example → .env and fill values."
  exit 1
fi

if ! grep -q "SENDGRID_API_KEY=SG\." .env 2>/dev/null; then
  echo "⚠️  WARNING: SENDGRID_API_KEY looks empty or invalid."
fi

JWT_LEN=$(grep "JWT_SECRET=" .env | cut -d= -f2 | wc -c)
if [ "$JWT_LEN" -lt 64 ]; then
  echo "❌ JWT_SECRET too short. Run: openssl rand -base64 64"
  exit 1
fi

# ─── 2. Obtain SSL certificate (first time only) ─────────────────────────────
obtain_ssl() {
  echo "▶ Obtaining Let's Encrypt SSL certificate..."
  # Temporarily start nginx on port 80 for ACME challenge
  docker compose -f $COMPOSE_FILE up -d nginx
  sleep 5

  docker run --rm \
    -v "$(pwd)/nginx/certbot-webroot:/var/www/certbot" \
    -v dlmp_nginx-certs:/etc/letsencrypt \
    certbot/certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email "admin@$DOMAIN" \
    --agree-tos \
    --no-eff-email \
    -d "$DOMAIN" \
    -d "www.$DOMAIN"

  echo "✅ SSL certificate obtained"
}

# ─── 3. Build all service JARs ───────────────────────────────────────────────
echo ""
echo "▶ Building all service JARs..."
mvn clean package -DskipTests --no-transfer-progress -q
echo "✅ Build complete"

# ─── 4. Build Docker images ──────────────────────────────────────────────────
echo ""
echo "▶ Building Docker images..."
docker compose -f $COMPOSE_FILE build --parallel --no-cache
echo "✅ Images built"

# ─── 5. Pull infrastructure images ───────────────────────────────────────────
echo ""
echo "▶ Pulling latest infra images..."
docker compose -f $COMPOSE_FILE pull kafka redis prometheus grafana nginx certbot
echo "✅ Images pulled"

# ─── 6. Start infrastructure first ───────────────────────────────────────────
echo ""
echo "▶ Starting infrastructure..."
docker compose -f $COMPOSE_FILE up -d kafka redis \
  mysql-users mysql-loans mysql-payments mysql-notifications mysql-reports
echo "  ⏳ Waiting 45s for databases to initialize..."
sleep 45

# ─── 7. Start application services ───────────────────────────────────────────
echo ""
echo "▶ Starting application services..."
docker compose -f $COMPOSE_FILE up -d \
  user-service loan-service payment-service notification-service report-service
echo "  ⏳ Waiting 60s for Spring Boot apps to start..."
sleep 60

# ─── 8. Start gateway + monitoring ───────────────────────────────────────────
echo ""
echo "▶ Starting API Gateway + monitoring..."
docker compose -f $COMPOSE_FILE up -d api-gateway prometheus grafana

# ─── 9. SSL certificate (first deploy) ───────────────────────────────────────
if [ ! -d "./nginx/certs/live/$DOMAIN" ]; then
  obtain_ssl
fi

# ─── 10. Start Nginx ──────────────────────────────────────────────────────────
echo ""
echo "▶ Starting Nginx reverse proxy..."
docker compose -f $COMPOSE_FILE up -d nginx certbot

# ─── 11. Health check ─────────────────────────────────────────────────────────
echo ""
echo "▶ Running health checks..."
sleep 15

ALL_OK=true
for svc_port in "user-service:8081" "loan-service:8082" "payment-service:8083" \
                "notification-service:8084" "report-service:8085" "api-gateway:8080"; do
  svc="${svc_port%%:*}"; port="${svc_port##*:}"
  code=$(docker exec "dlmp-${svc}" curl -sf -o /dev/null -w "%{http_code}" \
    "http://localhost:$port/actuator/health" 2>/dev/null || echo "000")
  if [ "$code" = "200" ]; then
    echo "  ✅ $svc"
  else
    echo "  ❌ $svc (HTTP=$code)"
    ALL_OK=false
  fi
done

echo ""
if $ALL_OK; then
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  ✅ DLMP v2.0 DEPLOYED SUCCESSFULLY"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  🌐  API:       https://$DOMAIN/api/v1"
  echo "  📊  Grafana:   https://$DOMAIN/grafana"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
  echo "⚠️  Some services are down. Check logs:"
  echo "     docker compose -f $COMPOSE_FILE logs --tail=50 <service>"
  exit 1
fi
