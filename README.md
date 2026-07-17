# DLMP v2.0 — Distributed Loan Management Platform

> **Enterprise-grade microservices architecture** for financial loan workflows.  
> 6 independent Spring Boot 3.3 services, event-driven via Apache Kafka, with SAGA orchestration, CQRS, and Transactional Outbox pattern.

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      API Gateway :8080                       │
│         JWT Auth · Circuit Breaker · Trace Injection         │
└──────┬──────┬──────┬──────┬──────┬──────────────────────────┘
       │      │      │      │      │
  User  Loan  Pay   Notif  Report
  :8081 :8082 :8083 :8084  :8085
       │      │      │      │
       └──────┴──────┴──────┴──────►  Apache Kafka (3 topics)
                                        dlmp.loan.events
                                        dlmp.payment.events
                                        dlmp.user.events
```

**Design Patterns Implemented:**
- **SAGA Orchestration** — `LoanDisbursementSaga` (5-step with compensation)
- **Transactional Outbox** — atomic DB write + Kafka publish, relay with dead-letter
- **CQRS** — command side (MySQL) + query side (read models via Kafka materialization)
- **Idempotency Key** — Redis-backed deduplication in payment-service
- **Optimistic Locking** — `@Version` on Loan aggregate root
- **Circuit Breaker** — Resilience4j protecting user-service calls from loan-service
- **Double-Entry Ledger** — every payment produces balanced DEBIT/CREDIT entries
- **Database-per-Service** — 5 isolated MySQL databases

---

## 📦 Services

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| `api-gateway` | 8080 | — | JWT validation, routing, rate-limiting |
| `user-service` | 8081 | `dlmp_users` | Registration, login, refresh token rotation |
| `loan-service` | 8082 | `dlmp_loans` | Loan lifecycle, EMI calculation, credit scoring |
| `payment-service` | 8083 | `dlmp_payments` | Payments with idempotency + ledger |
| `notification-service` | 8084 | `dlmp_notifications` | Event-driven emails + in-app notifications |
| `report-service` | 8085 | `dlmp_reports` | CQRS materialized views, portfolio analytics |

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21, Maven 3.9+

### 1. Start infrastructure
```bash
make infra
```

### 2. Start all services
```bash
make up
```

### 3. Verify health
```bash
make status
```

### 4. Open URLs
```bash
make urls
```

**Key endpoints:**
- API Gateway:      http://localhost:8090
- Kafka UI:         http://localhost:9000
- Zipkin Tracing:   http://localhost:9411
- MailHog UI:       http://localhost:8025
- Swagger (User):   http://localhost:8081/swagger-ui.html
- Swagger (Loan):   http://localhost:8082/swagger-ui.html
- Swagger (Payment):http://localhost:8083/swagger-ui.html

### 5. With Monitoring
```bash
make monitoring
# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3000 (admin / dlmp_grafana_2026)
```

---

## 🔐 Authentication Flow

```
POST /api/v1/auth/register   → returns accessToken + refreshToken
POST /api/v1/auth/login      → returns accessToken + refreshToken
POST /api/v1/auth/refresh    → X-Refresh-Token header → new tokens (rotation)
POST /api/v1/auth/logout     → Bearer token required → revokes all refresh tokens
```

Include `Authorization: Bearer <accessToken>` on all protected requests.

---

## 💳 Loan Lifecycle

```
DRAFT → PENDING_REVIEW → [UNDER_REVIEW] → APPROVED → ACTIVE → CLOSED
                                        ↘ REJECTED
                                                        ↘ DEFAULTED / NPA
```

1. **Apply** `POST /api/v1/loans/apply` — credit score calculated, EMI quoted
2. **Approve** `PUT /api/v1/loans/{id}/approve` — requires ROLE_LOAN_OFFICER / ROLE_ADMIN
3. **Disburse** `PUT /api/v1/loans/{id}/disburse` — requires officer/admin; generates EMI schedule
4. **Pay EMI** `POST /api/v1/payments/initiate` — with idempotency key
5. **Auto-close** — payment events flow back via Kafka; the loan closes itself when fully repaid

**Notifications:** `GET /api/v1/notifications/my`, `GET /api/v1/notifications/unread-count`,
`PUT /api/v1/notifications/{id}/read`, `PUT /api/v1/notifications/read-all`

---

## 💰 Payment Idempotency

```
POST /api/v1/payments/initiate
Headers:
  X-Idempotency-Key: <uuid>    ← prevent duplicate charges on retry
  X-User-Id: <userId>

Response includes: idempotent: true/false
```

---

## 🧪 Tests

```bash
make build-test           # Build + run all tests
mvn test -pl loan-service # Test single module
```

**Test Coverage:**

| Module | Tests | Patterns Covered |
|--------|-------|-----------------|
| `common` | 4 | JWT generation, validation, tampering, refresh |
| `user-service` | 6 | Registration, duplicate email, wrong password, lockout, active check |
| `loan-service` | 13 | EMI formula, zero-rate, credit scoring, DTI/LTI bounds, score clamping |
| `payment-service` | 6 | Standard payment, idempotency, penalty, not-found, balance correction |
| **Total** | **29** | **All pass ✅** |

---

## ⚙️ Environment Variables

All defaults target local development; production overrides everything via env vars.

| Variable | Default | Notes |
|----------|---------|-------|
| `JWT_SECRET` | — (required) | identical on ALL services |
| `INTERNAL_API_KEY` | `local-internal-key` | user↔loan service auth — override in prod |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | `localhost` / `3306` / per-service | Aiven values in prod |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `dlmp_user` / `dlmp_password` | |
| `KAFKA_SERVERS` | `localhost:9092` | Aiven SASL_SSL in prod |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | `SASL_SSL` + `KAFKA_SASL_JAAS` + `AIVEN_CA_CERT` in prod |
| `REDIS_HOST` / `REDIS_PASSWORD` | `localhost` / empty | Upstash + `REDIS_SSL_ENABLED=true` in prod |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | unset | bootstrap ROLE_ADMIN on startup (optional) |
| `MAIL_ENABLED` / `MAIL_*` | `false` | SMTP email sends (optional) |

**Cloud deployment (fully automated):**

```bash
# one-time: paste 3 API tokens (Aiven, Upstash, Render) into .deploy-secrets
make deploy        # fetches creds → configures Render → deploys → verifies live
make deploy-smoke  # re-verify the live stack any time
```

See [docs/RENDER_DEPLOYMENT.md](docs/RENDER_DEPLOYMENT.md) for details.

---

## 🏗️ Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.2 |
| Messaging | Apache Kafka (Confluent 7.5) |
| Cache | Redis 7.2 |
| Database | MySQL 8.0 (5 isolated DBs) |
| Migration | Flyway |
| Gateway | Spring Cloud Gateway (reactive) |
| Security | JWT HS512, BCrypt cost 12 |
| Resilience | Resilience4j (CB + Retry) |
| Tracing | Micrometer + Zipkin (Brave) |
| Metrics | Prometheus + Grafana |
| Docs | Springdoc OpenAPI 3 |
| Mapping | MapStruct |
| Containerization | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Security Scan | Trivy |
