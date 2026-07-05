# Deploying DLMP to Render (free tier)

End-to-end runbook. Follow the steps in order — the platform needs three
external managed services (Aiven MySQL, Aiven Kafka, Upstash Redis) before the
Render services can boot.

---

## 1. Provision external services

### 1a. Aiven MySQL (free plan)
1. https://console.aiven.io → Create service → **MySQL** → Free plan.
2. From *Overview*, note: **host**, **port**, user `avnadmin`, **password**.
3. Leave the database as `defaultdb` — all six services share it; each keeps
   its own Flyway history table so migrations don't collide.

### 1b. Aiven Kafka (free plan)
1. Create service → **Apache Kafka** → Free plan.
2. In *Service settings*, enable **SASL** authentication.
3. **Create the three topics** (Topics tab — auto-creation is disabled on Aiven):
   - `dlmp.loan.events`
   - `dlmp.payment.events`
   - `dlmp.user.events`
4. Note: **host:port** (SASL port), user, password.
5. Download the **CA certificate** (*Overview → CA Certificate*) — you'll paste
   the full PEM text into the `AIVEN_CA_CERT` env var.
6. Build the JAAS string (one line, keep the trailing semicolon):
   ```
   org.apache.kafka.common.security.scram.ScramLoginModule required username="avnadmin" password="KAFKA_PASSWORD";
   ```

### 1c. Upstash Redis (free plan)
1. https://console.upstash.com → Create database (TLS on by default).
2. Note: **endpoint host**, port `6379`, **password**.

## 2. Generate secrets (run locally)

```bash
# Same JWT_SECRET on ALL six services — the gateway signs nothing, everyone validates
openssl rand -base64 64 | tr -d '\n'

# Same INTERNAL_API_KEY on user-service AND loan-service
openssl rand -hex 32
```

## 3. Deploy the Blueprint

1. Push this repository to GitHub.
2. Render dashboard → **New → Blueprint** → pick the repo. Render reads
   `render.yaml` and creates all six services.
3. Before the first deploy finishes, open **each service → Environment** and
   fill in every `sync: false` variable:

   | Variable | Services | Value |
   |---|---|---|
   | `MYSQL_PASSWORD` | all except gateway | Aiven MySQL password |
   | `REDIS_PASSWORD` | user, loan, payment, report, gateway | Upstash password |
   | `KAFKA_SASL_JAAS` | user, loan, payment, notification, report | JAAS string from 1b |
   | `AIVEN_CA_CERT` | user, loan, payment, notification, report | full PEM text (multi-line OK) |
   | `JWT_SECRET` | **all six — identical value** | from step 2 |
   | `INTERNAL_API_KEY` | user-service + loan-service — identical | from step 2 |
   | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | user-service (optional) | bootstrap admin login |
   | `MAIL_PASSWORD` / `MAIL_FROM` | notification-service (optional) | SendGrid key + verified sender; also set `MAIL_ENABLED=true` |

4. If your Render service names differ from `dlmp-*`, update the
   `USER_SERVICE_URL`/`*_SERVICE_URL` values on the gateway and loan-service,
   and `CORS_ALLOWED_ORIGINS` on the gateway.
5. Trigger **Manual Deploy → Deploy latest commit** on any service that
   started before its env vars were saved.

## 4. Verify

```bash
BASE=https://dlmp-gateway.onrender.com

# 1. Gateway healthy
curl $BASE/actuator/health          # → {"status":"UP"}

# 2. Register (goes through gateway → user-service)
curl -s -X POST $BASE/api/v1/auth/register -H 'Content-Type: application/json' -d '{
  "firstName":"Test","lastName":"User","email":"test@example.com",
  "password":"Passw0rd@123","monthlyIncome":85000}'
# → 201 with accessToken

# 3. Authenticated call
TOKEN=...   # accessToken from above
curl -s $BASE/api/v1/users/me -H "Authorization: Bearer $TOKEN"

# 4. Apply for a loan
curl -s -X POST $BASE/api/v1/loans/apply -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
  "loanType":"PERSONAL","principalAmount":200000,"tenureMonths":24,
  "monthlyIncome":85000,"purpose":"testing"}'

# 5. In-app notification arrived via Kafka (~10s for the outbox relay)
curl -s $BASE/api/v1/notifications/my -H "Authorization: Bearer $TOKEN"
```

To exercise officer flows (approve/disburse), log in with the `ADMIN_EMAIL`
account — customers cannot approve their own loans.

## 5. Free-tier realities

- **Cold starts**: free services sleep after ~15 min idle; first request takes
  ~50 s. The gateway's circuit-breaker time limit is set to 60 s to tolerate
  this, but wake the target service (hit its own `/actuator/health`) if a call
  times out.
- **Kafka consumers sleep too**: notification/report/loan consumers only
  process events while their service is awake. Events wait in Kafka (7-day
  retention) and are consumed on wake-up — nothing is lost.
- **One shared MySQL**: keep Hikari pools small (already 5 per service);
  Aiven free tier caps connections.
- A cron pinger (e.g. cron-job.org hitting each `/actuator/health` every 10
  min) keeps services warm if you need the demo to feel instant.

## 6. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Deploy stuck "in progress", health check failing | check service logs; usually a missing `sync: false` env var (JWT_SECRET is required for boot) |
| `Could not resolve placeholder 'JWT_SECRET'` | env var not saved before deploy — save and redeploy |
| Kafka `SaslAuthenticationException` | JAAS string malformed — must end with `;` and use straight quotes |
| Kafka `UnknownTopicOrPartitionException` | topics not created in the Aiven console (step 1b.3) |
| MySQL `Access denied` | wrong `MYSQL_PASSWORD`, or Aiven IP allowlist restricting access (open to 0.0.0.0/0 for Render free) |
| 401 from gateway with valid token | `JWT_SECRET` differs between gateway and services — must be byte-identical |
| Disbursement returns "verification unavailable" | `INTERNAL_API_KEY` differs between loan-service and user-service, or user-service is asleep — retry after waking it |
