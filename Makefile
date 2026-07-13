.PHONY: help build test infra up down status logs urls prod-up prod-down ssl

help: ## Show all commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

# ─── Development ──────────────────────────────────────────────────────────────
build: ## Build all JARs (skip tests)
	mvn clean package -DskipTests --no-transfer-progress -q
	@echo "✅ All JARs built"

test: ## Run all unit tests
	mvn test --no-transfer-progress
	@echo "✅ Tests complete"

infra: ## Start dev infra (Kafka, Redis, MySQL ×5, Zipkin, MailHog)
	docker compose up -d zookeeper kafka redis zipkin mailhog kafka-ui \
	  mysql-users mysql-loans mysql-payments mysql-notifications mysql-reports
	@echo "⏳ Waiting for services to be healthy..."
	@sleep 30
	@$(MAKE) status

up: ## Start all dev services (requires built JARs + infra running)
	./scripts/start-services.sh start

migrate-users: ## Migrate users from legacy Openbravo DB to DLMP DB
	python3 scripts/migrate_users.py

down: ## Stop all dev services
	./scripts/start-services.sh stop
	docker compose down

status: ## Show health of all containers
	@docker compose ps
	@echo ""
	@./scripts/start-services.sh status 2>/dev/null || true

logs: ## Tail logs for a service (make logs SVC=loan-service)
	@tail -f /tmp/dlmp-logs/$(SVC).log

urls: ## Print all service URLs
	@echo ""
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "  🌐  DLMP v2.0 — Development URLs"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
	@echo "  API Gateway:    http://localhost:8080"
	@echo "  User Service:   http://localhost:8081/swagger-ui.html"
	@echo "  Loan Service:   http://localhost:8082/swagger-ui.html"
	@echo "  Payment Svc:    http://localhost:8083/swagger-ui.html"
	@echo "  Report Svc:     http://localhost:8085/swagger-ui.html"
	@echo "  Kafka UI:       http://localhost:9000"
	@echo "  Zipkin:         http://localhost:9411"
	@echo "  MailHog:        http://localhost:8025"
	@echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ─── Cloud deploy (Render + Aiven + Upstash — fully automated) ────────────────
deploy: ## Deploy everything to Render: fetch creds, sync env, deploy, verify
	python3 scripts/deploy/auto_deploy.py

deploy-check: ## Verify the three provider API tokens work (no changes made)
	python3 scripts/deploy/auto_deploy.py --check

deploy-smoke: ## Smoke-test the live Render stack
	python3 scripts/deploy/auto_deploy.py --smoke

deploy-frontend: ## Deploy frontend/ to Vercel (needs VERCEL_TOKEN in .deploy-secrets)
	python3 scripts/deploy/deploy_frontend.py

deploy-frontend-check: ## Verify the Vercel token works (no changes made)
	python3 scripts/deploy/deploy_frontend.py --check

update-cors: ## Add an origin to the gateway's CORS allowlist and redeploy (make update-cors ORIGIN=https://x.vercel.app)
	python3 scripts/deploy/update_cors.py $(ORIGIN)

# ─── Production ───────────────────────────────────────────────────────────────
prod-build: ## Build production Docker images
	docker compose -f docker-compose.prod.yml build --parallel

prod-up: ## Start production stack (requires .env file)
	@if [ ! -f .env ]; then echo "❌ .env missing. Copy .env.example → .env"; exit 1; fi
	chmod +x scripts/deploy-prod.sh
	./scripts/deploy-prod.sh

prod-down: ## Stop production stack
	docker compose -f docker-compose.prod.yml down

prod-logs: ## Tail production logs (make prod-logs SVC=loan-service)
	docker compose -f docker-compose.prod.yml logs -f $(SVC)

prod-status: ## Check production container status
	docker compose -f docker-compose.prod.yml ps

ssl: ## Obtain/renew Let's Encrypt SSL certificate
	@echo "Obtaining SSL for $${DOMAIN:-yourdomain.com}..."
	docker compose -f docker-compose.prod.yml run --rm certbot \
	  certonly --webroot --webroot-path=/var/www/certbot \
	  --email admin@$${DOMAIN:-yourdomain.com} \
	  --agree-tos --no-eff-email \
	  -d $${DOMAIN:-yourdomain.com} -d www.$${DOMAIN:-yourdomain.com}

# ─── Secrets generation ───────────────────────────────────────────────────────
gen-secrets: ## Generate secure secrets for .env
	@echo "JWT_SECRET=$$(openssl rand -base64 64 | tr -d '\n')"
	@echo "MYSQL_ROOT_PASSWORD=$$(openssl rand -hex 16)"
	@echo "MYSQL_PASSWORD=$$(openssl rand -hex 16)"
	@echo "REDIS_PASSWORD=$$(openssl rand -hex 16)"
	@echo "GRAFANA_ADMIN_PASSWORD=$$(openssl rand -hex 12)"
