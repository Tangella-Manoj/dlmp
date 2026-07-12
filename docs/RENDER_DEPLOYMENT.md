# Deploying DLMP to Render

## ⚡ Automated deployment (recommended — one command)

Everything is automated: credentials are fetched from Aiven (MySQL + Kafka) and
Upstash (Redis) via their APIs, Kafka topics are created, all env vars are
pushed to all six Render services, deploys are triggered, and the live stack is
health-checked and smoke-tested.

### One-time setup (~2 minutes)

Log in to each console with **tangellamanoj9@gmail.com** and create one API
token per provider (this is the only manual step — interactive Google logins
cannot be scripted, API tokens are exactly what these platforms provide for
automation):

| # | Token | Where |
|---|-------|-------|
| 1 | `AIVEN_TOKEN` | https://console.aiven.io/profile/tokens → **Generate token** |
| 2 | `UPSTASH_API_KEY` | https://console.upstash.com/account/api → **Create API key** |
| 3 | `RENDER_API_KEY` | https://dashboard.render.com/settings#api-keys → **Create API Key** |

Paste the three values into `.deploy-secrets` (already created in the repo
root, gitignored — never committed).

### Prerequisites in each provider (already exist for this project)

- **Aiven**: a MySQL service and a Kafka service (free plans). The tool finds
  them automatically, enables SASL on Kafka if needed, creates the three
  topics, and downloads the CA certificate.
- **Upstash**: nothing — the tool creates `dlmp-redis` if no database exists.
- **Render**: the GitHub repo connected to your Render account (done once when
  you first used Render). The tool creates any missing `dlmp-*` service.

### Deploy

```bash
make deploy-check   # optional: verify the three tokens work (changes nothing)
make deploy         # the whole pipeline: fetch → configure → deploy → verify
```

What `make deploy` does, in order:

1. Fetches MySQL host/port/user/password from Aiven
2. Fetches Kafka SASL endpoint + credentials, enables SASL if off, creates
   `dlmp.loan.events` / `dlmp.payment.events` / `dlmp.user.events`, downloads the CA cert
3. Fetches (or creates) the Upstash Redis database + password
4. Generates `JWT_SECRET`, `INTERNAL_API_KEY`, `ADMIN_PASSWORD` on first run and
   persists them in `.deploy-secrets` (stable across re-runs)
5. Finds or creates the six Render services and syncs the full env-var set to
   each (16–19 vars per service, including the multiline CA cert)
6. Pushes any unpushed commits to GitHub
7. Triggers all six deploys and waits until every one is **live**
   (free-tier builds take 10–20 min)
8. Health-checks every service URL (tolerates ~50 s free-tier cold starts)
9. Runs a live smoke test: register → `/users/me` → admin login

Final output shows the gateway URL and your admin login
(`ADMIN_EMAIL` = tangellamanoj9@gmail.com, password in `.deploy-secrets`).

Re-running `make deploy` is always safe — it is idempotent (same secrets,
updated env, fresh deploys).

```bash
make deploy-smoke   # re-verify the live stack any time
```

### Enabling real emails later (optional)

Set in `.deploy-secrets`: `MAIL_ENABLED=true`, `MAIL_PASSWORD=<SendGrid API key>`,
`MAIL_FROM=<verified sender>` — then `make deploy` again.

---

## Free-tier realities

- **Cold starts**: free services sleep after ~15 min idle; first request takes
  ~50 s. The gateway's circuit-breaker time limit is 60 s to tolerate this.
- **Kafka consumers sleep too**: events wait in Kafka (7-day retention) and are
  consumed when the service wakes — nothing is lost.
- A cron pinger (e.g. cron-job.org hitting each `/actuator/health` every 10
  min) keeps services warm for demos.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Aiven token rejected (401)` | token expired/mistyped — regenerate |
| `No Aiven mysql/kafka service found` | create the service (free plan) in console.aiven.io, re-run |
| `Could not create Render service … repo` | connect the GitHub repo to Render once (Dashboard → New → connect repo), re-run |
| Deploy `build_failed` | open the service's Logs tab in Render; usually a transient builder issue — re-run `make deploy` |
| Health check red but deploy live | free-tier cold start — `make deploy-smoke` after a minute |
| Kafka `SaslAuthenticationException` in service logs | re-run `make deploy` (re-syncs JAAS + CA from Aiven) |
| 401 from gateway with valid token | JWT_SECRET drift — re-run `make deploy` (re-syncs the same secret everywhere) |

---

## Appendix: manual deployment (not recommended)

<details>
<summary>Expand if you ever need to configure by hand</summary>

1. Create Aiven MySQL + Kafka (enable SASL; create the 3 topics) and Upstash Redis.
2. Generate secrets: `openssl rand -base64 64 | tr -d '\n'` (JWT_SECRET),
   `openssl rand -hex 32` (INTERNAL_API_KEY).
3. Render → New → Blueprint → this repo (`render.yaml` defines all six services).
4. Fill every `sync: false` env var per service (see the table in `render.yaml`
   comments): MYSQL_PASSWORD, REDIS_PASSWORD, KAFKA_SASL_JAAS, AIVEN_CA_CERT
   (full PEM), JWT_SECRET (identical everywhere), INTERNAL_API_KEY (user+loan,
   identical), ADMIN_EMAIL/ADMIN_PASSWORD (user-service), MAIL_* (notification).
5. Manual Deploy → Deploy latest commit on each service.
6. Verify: `curl https://dlmp-gateway.onrender.com/actuator/health`, then
   register/login as in the smoke test.

</details>
