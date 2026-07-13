#!/usr/bin/env python3
"""
DLMP — fully automated Render deployment.

One command does everything:
  • Aiven API   → fetch MySQL creds, Kafka SASL creds + CA cert, create topics
  • Upstash API → fetch (or create) the Redis database + credentials
  • generates JWT_SECRET / INTERNAL_API_KEY / ADMIN_PASSWORD (persisted locally)
  • Render API  → create-or-find all six services, sync every env var,
                  trigger deploys, wait until live
  • verifies    → health checks + live register/login smoke test

Usage:
    python3 scripts/deploy/auto_deploy.py            # full pipeline
    python3 scripts/deploy/auto_deploy.py --check    # preflight only
    python3 scripts/deploy/auto_deploy.py --no-push  # skip `git push`
    python3 scripts/deploy/auto_deploy.py --smoke    # smoke-test only (stack already live)

Setup (one time): copy .deploy-secrets.example → .deploy-secrets and paste
three API tokens. Everything else is automatic. .deploy-secrets is gitignored.
"""

import argparse
import base64
import json
import os
import secrets as pysecrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SECRETS_FILE = ROOT / ".deploy-secrets"

TOPICS = ["dlmp.loan.events", "dlmp.payment.events", "dlmp.user.events"]

RENDER_SERVICES = {
    "dlmp-user-service":         {"dockerfile": "user-service/Dockerfile"},
    "dlmp-loan-service":         {"dockerfile": "loan-service/Dockerfile"},
    "dlmp-payment-service":      {"dockerfile": "payment-service/Dockerfile"},
    "dlmp-notification-service": {"dockerfile": "notification-service/Dockerfile"},
    "dlmp-report-service":       {"dockerfile": "report-service/Dockerfile"},
    "dlmp-gateway":              {"dockerfile": "api-gateway/Dockerfile"},
}

OK, WARN, ERR, INFO = "✅", "⚠️ ", "❌", "▸"


# ─── tiny helpers ─────────────────────────────────────────────────────────────

def die(msg: str) -> None:
    print(f"\n{ERR} {msg}")
    sys.exit(1)


def http(method: str, url: str, *, token=None, basic=None, body=None, ok_codes=(200, 201, 202)):
    """Minimal JSON HTTP client (stdlib only)."""
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = token
    if basic:
        headers["Authorization"] = "Basic " + base64.b64encode(basic.encode()).decode()
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            text = resp.read().decode() or "{}"
            return resp.status, json.loads(text)
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            payload = json.loads(text)
        except Exception:
            payload = {"raw": text}
        if e.code in ok_codes:
            return e.code, payload
        return e.code, payload
    except urllib.error.URLError as e:
        die(f"Network error calling {url}: {e.reason}")


def load_secrets() -> dict:
    cfg = {}
    if SECRETS_FILE.exists():
        for line in SECRETS_FILE.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip()
                os.environ[k.strip()] = v.strip()
    return cfg


def save_secret(cfg: dict, key: str, value: str) -> None:
    cfg[key] = value
    lines = []
    seen = set()
    if SECRETS_FILE.exists():
        for line in SECRETS_FILE.read_text().splitlines():
            stripped = line.strip()
            if stripped and not stripped.startswith("#") and "=" in stripped:
                k = stripped.split("=", 1)[0].strip()
                if k == key:
                    lines.append(f"{key}={value}")
                    seen.add(key)
                    continue
            lines.append(line)
    if key not in seen:
        lines.append(f"{key}={value}")
    SECRETS_FILE.write_text("\n".join(lines) + "\n")


# ─── providers ────────────────────────────────────────────────────────────────

class Aiven:
    BASE = "https://api.aiven.io/v1"

    def __init__(self, token, project=None):
        self.auth = f"aivenv1 {token}"
        self.project = project

    def connect(self):
        code, body = http("GET", f"{self.BASE}/project", token=self.auth)
        if code == 401:
            die("Aiven token rejected (401). Create one at https://console.aiven.io → User settings → Tokens")
        projects = [p["project_name"] for p in body.get("projects", [])]
        if not projects:
            die("No Aiven projects found on this account.")
        if not self.project:
            self.project = projects[0]
            if len(projects) > 1:
                print(f"{WARN} Multiple Aiven projects {projects}; using '{self.project}'. "
                      f"Set AIVEN_PROJECT in .deploy-secrets to override.")
        print(f"{OK} Aiven authenticated (project: {self.project})")

    def services(self):
        _, body = http("GET", f"{self.BASE}/project/{self.project}/service", token=self.auth)
        return body.get("services", [])

    def power_on(self, service_name: str) -> None:
        """Power on a service that is in POWEROFF state via the Aiven REST API."""
        print(f"{INFO} Powering on Aiven service '{service_name}' (was POWEROFF) …")
        code, body = http(
            "PUT",
            f"{self.BASE}/project/{self.project}/service/{service_name}",
            token=self.auth,
            body={"powered": True},
            ok_codes=(200, 201, 202),
        )
        if code not in (200, 201, 202):
            die(f"Could not power on '{service_name}' via Aiven API (HTTP {code}): {body}\n"
                f"   → Go to https://console.aiven.io and power it on manually.")
        print(f"{OK} Power-on request accepted for '{service_name}'")

    def allow_all_ips(self, service_name: str) -> None:
        """Open the Aiven service to all IPs (0.0.0.0/0).
        Required so Render (dynamic IPs) can connect. Safe for portfolio use."""
        url = f"{self.BASE}/project/{self.project}/service/{service_name}"
        # Try PATCH first (partial update), then PUT with both formats
        for method, body in (
            ("PATCH", {"ip_filter": [{"network": "0.0.0.0/0", "description": "Render"}]}),
            ("PUT",   {"ip_filter": [{"network": "0.0.0.0/0", "description": "Render"}]}),
            ("PUT",   {"ip_filter": ["0.0.0.0/0"]}),
        ):
            code, resp = http(method, url, token=self.auth, body=body,
                              ok_codes=(200, 201, 202, 400, 405))
            if code in (200, 201, 202):
                print(f"{OK} Aiven '{service_name}' IP allowlist opened to 0.0.0.0/0")
                return
        print(f"{WARN} Could not open IP allowlist automatically (all methods returned error). "
              f"Fix manually: Aiven → dlmp-db → Service settings → Allowed IP ranges → add 0.0.0.0/0")

    def find(self, service_type: str):
        matches = [s for s in self.services() if s.get("service_type") == service_type]
        if not matches:
            die(f"No Aiven {service_type} service found in project '{self.project}'. "
                f"Create one (free plan) in the Aiven console first.")
        svc = matches[0]
        state = svc.get("state", "")
        if state == "POWEROFF":
            self.power_on(svc["service_name"])
        if state != "RUNNING":
            print(f"{WARN} Aiven {service_type} '{svc['service_name']}' state={state} — waiting up to 10 min …")
            for i in range(60):  # 60 × 10 s = 10 min
                time.sleep(10)
                svc = [s for s in self.services() if s["service_name"] == svc["service_name"]][0]
                state = svc.get("state", "")
                if state == "RUNNING":
                    print(f"{OK} Aiven {service_type} is RUNNING  ({(i + 1) * 10}s)")
                    break
                if (i + 1) % 6 == 0:
                    print(f"   … still {state} ({(i + 1) * 10}s elapsed)")
            else:
                die(f"Aiven {service_type} '{svc['service_name']}' never reached RUNNING state after 10 min.\n"
                    f"   Current state: {state}. Check the Aiven console.")
        return svc

    def mysql_creds(self) -> dict:
        svc = self.find("mysql")
        name = svc["service_name"]
        # Open IP allowlist so Render's dynamic IPs are allowed to connect
        self.allow_all_ips(name)
        p = svc.get("service_uri_params", {})
        password = p.get("password") or next(
            (u["password"] for u in svc.get("users", []) if u.get("username") == p.get("user", "avnadmin")), None)
        
        # Aiven API might redact the password for security
        if not password or password == "<redacted>":
            password = os.environ.get("MYSQL_PASSWORD")
            if not password:
                die(f"Aiven API redacted the MySQL password. "
                    f"Please add MYSQL_PASSWORD=your_actual_password to your .deploy-secrets file.")
                
        if not (p.get("host") and p.get("port")):
            die("Could not extract MySQL host/port from Aiven service response.")
            
        creds = {"host": p["host"], "port": p["port"], "db": p.get("dbname", "defaultdb"),
                 "user": p.get("user", "avnadmin"), "password": password}
        print(f"{OK} Aiven MySQL: {creds['host']}:{creds['port']}/{creds['db']}")
        return creds

    def kafka_creds(self) -> dict:
        svc = self.find("kafka")
        name = svc["service_name"]
        p = svc.get("service_uri_params", {})
        password = p.get("password") or next(
            (u["password"] for u in svc.get("users", []) if u.get("username") == p.get("user", "avnadmin")), None)
            
        if not password or password == "<redacted>":
            password = os.environ.get("KAFKA_PASSWORD")
            if not password:
                die(f"Aiven API redacted the Kafka password. "
                    f"Please add KAFKA_PASSWORD=your_actual_password to your .deploy-secrets file.")

        def sasl_component(s):
            return next((c for c in s.get("components", [])
                         if c.get("component") == "kafka" and c.get("kafka_authentication_method") == "sasl"), None)

        comp = sasl_component(svc)
        if not comp:
            print(f"{INFO} Enabling SASL authentication on Kafka '{name}' …")
            http("PUT", f"{self.BASE}/project/{self.project}/service/{name}", token=self.auth,
                 body={"user_config": {"kafka_authentication_methods": {"sasl": True}}})
            for _ in range(24):
                time.sleep(10)
                svc = self.find("kafka")
                comp = sasl_component(svc)
                if comp:
                    break
            else:
                die("SASL endpoint never appeared on the Aiven Kafka service.")

        user = next((u for u in svc.get("users", []) if u.get("username") == "avnadmin"),
                    (svc.get("users") or [None])[0])
        if not user or not user.get("password"):
            die("Could not extract Kafka SASL credentials from Aiven.")

        # topics
        _, body = http("GET", f"{self.BASE}/project/{self.project}/service/{name}/topic", token=self.auth)
        existing = {t["topic_name"] for t in body.get("topics", [])}
        for topic in TOPICS:
            if topic in existing:
                continue
            created = False
            for repl in (2, 3):
                code, resp = http("POST", f"{self.BASE}/project/{self.project}/service/{name}/topic",
                                  token=self.auth,
                                  body={"topic_name": topic, "partitions": 1, "replication": repl},
                                  ok_codes=(200, 201))
                if code in (200, 201):
                    created = True
                    break
            if created:
                print(f"{OK} Created Kafka topic {topic}")
            else:
                die(f"Could not create Kafka topic {topic}: {resp}")

        _, ca = http("GET", f"{self.BASE}/project/{self.project}/kms/ca", token=self.auth)
        cert = ca.get("certificate")
        if not cert:
            die("Could not fetch Aiven CA certificate.")

        creds = {"servers": f"{comp['host']}:{comp['port']}",
                 "user": user["username"], "password": user["password"], "ca": cert}
        print(f"{OK} Aiven Kafka: {creds['servers']} (SASL), topics ready")
        return creds


class Upstash:
    BASE = "https://api.upstash.com/v2"

    def __init__(self, email: str, key: str):
        self.basic = f"{email}:{key}"

    def redis_creds(self) -> dict:
        code, body = http("GET", f"{self.BASE}/redis/databases", basic=self.basic)
        if code == 401 or (isinstance(body, dict) and body.get("error")):
            die("Upstash credentials rejected. Create a Management API key at "
                "https://console.upstash.com/account/api")
        dbs = body if isinstance(body, list) else []
        db = next((d for d in dbs if d.get("database_name") == "dlmp-redis"), dbs[0] if dbs else None)
        if not db:
            print(f"{INFO} No Upstash Redis found — creating 'dlmp-redis' …")
            code, db = http("POST", f"{self.BASE}/redis/database", basic=self.basic,
                            body={"name": "dlmp-redis", "region": "us-east-1", "tls": True})
            if code not in (200, 201):
                die(f"Could not create Upstash Redis: {db}")
        creds = {"host": db["endpoint"], "port": str(db.get("port", 6379)), "password": db["password"]}
        print(f"{OK} Upstash Redis: {creds['host']}:{creds['port']} (TLS)")
        return creds


class Render:
    BASE = "https://api.render.com/v1"

    def __init__(self, key: str, repo_url: str):
        self.auth = f"Bearer {key}"
        self.repo_url = repo_url
        self.owner_id = None

    def connect(self):
        code, body = http("GET", f"{self.BASE}/owners?limit=20", token=self.auth)
        if code == 401:
            die("Render API key rejected (401). Create one at https://dashboard.render.com → "
                "Account Settings → API Keys")
        owners = [o["owner"] for o in body]
        if not owners:
            die("No Render owners/workspaces visible to this API key.")
        self.owner_id = owners[0]["id"]
        print(f"{OK} Render authenticated (workspace: {owners[0].get('name', self.owner_id)})")

    def list_services(self) -> dict:
        services, cursor = {}, None
        while True:
            url = f"{self.BASE}/services?limit=100" + (f"&cursor={cursor}" if cursor else "")
            _, body = http("GET", url, token=self.auth)
            if not body:
                break
            for item in body:
                svc = item["service"]
                services[svc["name"]] = svc
            cursor = body[-1].get("cursor")
            if len(body) < 100 or not cursor:
                break
        return services

    def ensure_service(self, existing: dict, name: str, dockerfile: str) -> dict:
        if name in existing:
            return existing[name]
        print(f"{INFO} Render service '{name}' not found — creating it …")
        payload = {
            "type": "web_service",
            "name": name,
            "ownerId": self.owner_id,
            "repo": self.repo_url,
            "branch": "main",
            "autoDeploy": "yes",
            "serviceDetails": {
                "env": "docker",
                "plan": "free",
                "region": "oregon",
                "healthCheckPath": "/actuator/health",
                "envSpecificDetails": {"dockerfilePath": dockerfile, "dockerContext": "."},
            },
        }
        code, body = http("POST", f"{self.BASE}/services", token=self.auth, body=payload)
        if code not in (200, 201):
            die(f"Could not create Render service {name}: {body}\n"
                f"   (Is the GitHub repo {self.repo_url} connected to your Render account?)")
        print(f"{OK} Created Render service {name}")
        return body["service"] if "service" in body else body

    def set_env(self, service_id: str, env: dict):
        payload = [{"key": k, "value": v} for k, v in sorted(env.items())]
        code, body = http("PUT", f"{self.BASE}/services/{service_id}/env-vars",
                          token=self.auth, body=payload)
        if code not in (200, 201):
            die(f"Failed to set env vars on {service_id}: {body}")

    @staticmethod
    def _unwrap(obj):
        """Render sometimes wraps payloads ({'deploy': {...}}); normalize."""
        if isinstance(obj, dict):
            return obj.get("deploy", obj)
        return obj

    def latest_deploy(self, service_id: str):
        _, body = http("GET", f"{self.BASE}/services/{service_id}/deploys?limit=1", token=self.auth)
        if isinstance(body, list) and body:
            return self._unwrap(body[0]).get("id")
        return None

    def deploy(self, service_id: str) -> str:
        # The git push (autoDeploy=yes) may already have started a deploy —
        # a conflicting trigger is fine, we then attach to the running one.
        code, body = http("POST", f"{self.BASE}/services/{service_id}/deploys",
                          token=self.auth, body={"clearCache": "do_not_clear"},
                          ok_codes=(200, 201, 202, 400, 409, 429))
        deploy_id = self._unwrap(body).get("id") if isinstance(body, dict) else None
        if not deploy_id:
            deploy_id = self.latest_deploy(service_id)
        if not deploy_id:
            die(f"Could not trigger or find a deploy for {service_id}: {body}")
        return deploy_id

    def deploy_status(self, service_id: str, deploy_id: str) -> str:
        _, body = http("GET", f"{self.BASE}/services/{service_id}/deploys/{deploy_id}", token=self.auth)
        return self._unwrap(body).get("status", "unknown")


# ─── pipeline ─────────────────────────────────────────────────────────────────

def preflight(cfg: dict) -> list:
    missing = [k for k in ("AIVEN_TOKEN", "UPSTASH_EMAIL", "UPSTASH_API_KEY", "RENDER_API_KEY")
               if not cfg.get(k)]
    if missing:
        print(f"""
{ERR} Missing credentials in {SECRETS_FILE.name}: {', '.join(missing)}

One-time setup (≈2 minutes, all logged in with tangellamanoj9@gmail.com):

  1. AIVEN_TOKEN      https://console.aiven.io/profile/tokens        → Generate token
  2. UPSTASH_API_KEY  https://console.upstash.com/account/api        → Create API key
                      (UPSTASH_EMAIL is just tangellamanoj9@gmail.com)
  3. RENDER_API_KEY   https://dashboard.render.com/settings#api-keys → Create API key

Then:   cp .deploy-secrets.example .deploy-secrets   (already done if file exists)
        edit .deploy-secrets and paste the three values
        make deploy
""")
        sys.exit(2)
    print(f"{OK} All provider tokens present")
    return missing


def ensure_generated_secrets(cfg: dict):
    if not cfg.get("JWT_SECRET"):
        save_secret(cfg, "JWT_SECRET", base64.b64encode(pysecrets.token_bytes(64)).decode())
        print(f"{OK} Generated JWT_SECRET (persisted in .deploy-secrets)")
    if not cfg.get("INTERNAL_API_KEY"):
        save_secret(cfg, "INTERNAL_API_KEY", pysecrets.token_hex(32))
        print(f"{OK} Generated INTERNAL_API_KEY")
    if not cfg.get("ADMIN_EMAIL"):
        save_secret(cfg, "ADMIN_EMAIL", "tangellamanoj9@gmail.com")
    if not cfg.get("ADMIN_PASSWORD"):
        pwd = "Adm!" + pysecrets.token_urlsafe(12)
        save_secret(cfg, "ADMIN_PASSWORD", pwd)
        print(f"{OK} Generated ADMIN_PASSWORD (see .deploy-secrets)")


def build_env_maps(cfg, mysql, kafka, redis, urls) -> dict:
    jaas = ('org.apache.kafka.common.security.scram.ScramLoginModule required '
            f'username="{kafka["user"]}" password="{kafka["password"]}";')
    common = {
        "MYSQL_HOST": mysql["host"], "MYSQL_PORT": str(mysql["port"]),
        "MYSQL_DB": mysql["db"], "MYSQL_SSL": "true",
        "MYSQL_USER": mysql["user"], "MYSQL_PASSWORD": mysql["password"],
        "KAFKA_SERVERS": kafka["servers"], "KAFKA_SECURITY_PROTOCOL": "SASL_SSL",
        "KAFKA_SASL_MECHANISM": "SCRAM-SHA-256", "KAFKA_SASL_JAAS": jaas,
        "AIVEN_CA_CERT": kafka["ca"], "JWT_SECRET": cfg["JWT_SECRET"],
        # No Zipkin collector exists in this environment — exporting spans just
        # burns CPU on retries against an unreachable localhost:9411 and spams
        # logs. Sampling stays at the app default (0.1) for local dev via
        # docker-compose's real Zipkin container.
        "TRACING_SAMPLING_PROBABILITY": "0",
    }
    redis_env = {
        "REDIS_HOST": redis["host"], "REDIS_PORT": redis["port"],
        "REDIS_PASSWORD": redis["password"], "REDIS_SSL_ENABLED": "true",
    }
    mail_env = {
        "MAIL_ENABLED": cfg.get("MAIL_ENABLED", "false"),
        "MAIL_HOST": cfg.get("MAIL_HOST", "smtp.sendgrid.net"),
        "MAIL_PORT": cfg.get("MAIL_PORT", "587"),
        "MAIL_USERNAME": cfg.get("MAIL_USERNAME", "apikey"),
        "MAIL_PASSWORD": cfg.get("MAIL_PASSWORD", ""),
        "MAIL_FROM": cfg.get("MAIL_FROM", "noreply@dlmp.com"),
        "MAIL_FROM_NAME": cfg.get("MAIL_FROM_NAME", "DLMP Platform"),
    }
    return {
        "dlmp-user-service": {**common, **redis_env,
                              "INTERNAL_API_KEY": cfg["INTERNAL_API_KEY"],
                              "ADMIN_EMAIL": cfg["ADMIN_EMAIL"],
                              "ADMIN_PASSWORD": cfg["ADMIN_PASSWORD"]},
        "dlmp-loan-service": {**common, **redis_env,
                              "INTERNAL_API_KEY": cfg["INTERNAL_API_KEY"],
                              "USER_SERVICE_URL": urls["dlmp-user-service"]},
        "dlmp-payment-service": {**common, **redis_env},
        "dlmp-notification-service": {**common, **mail_env},
        "dlmp-report-service": {**common, **redis_env},
        "dlmp-gateway": {**redis_env,
                         "JWT_SECRET": cfg["JWT_SECRET"],
                         "TRACING_SAMPLING_PROBABILITY": "0",
                         "USER_SERVICE_URL": urls["dlmp-user-service"],
                         "LOAN_SERVICE_URL": urls["dlmp-loan-service"],
                         "PAYMENT_SERVICE_URL": urls["dlmp-payment-service"],
                         "NOTIFICATION_SERVICE_URL": urls["dlmp-notification-service"],
                         "REPORT_SERVICE_URL": urls["dlmp-report-service"],
                         "CORS_ALLOWED_ORIGINS": cfg.get("CORS_ALLOWED_ORIGINS", urls["dlmp-gateway"])},
    }


def git_push():
    ahead = subprocess.run(["git", "rev-list", "--count", "origin/main..HEAD"],
                           cwd=ROOT, capture_output=True, text=True).stdout.strip()
    if ahead and int(ahead) > 0:
        print(f"{INFO} Pushing {ahead} commit(s) to origin/main …")
        r = subprocess.run(["git", "push", "origin", "main"], cwd=ROOT)
        if r.returncode != 0:
            die("git push failed — fix git auth and re-run (or use --no-push).")
        print(f"{OK} Pushed to GitHub")
    else:
        print(f"{OK} GitHub already up to date")


def wait_for_deploys(render: Render, deploys: dict, timeout_s=2400):
    print(f"{INFO} Waiting for {len(deploys)} deploys (free-tier builds take 10-20 min) …")
    start, pending = time.time(), dict(deploys)
    failed = {}
    while pending and time.time() - start < timeout_s:
        time.sleep(30)
        for name in list(pending):
            sid, did = pending[name]
            status = render.deploy_status(sid, did)
            if status == "live":
                print(f"{OK} {name} is LIVE  ({int(time.time() - start)}s)")
                del pending[name]
            elif status in ("build_failed", "update_failed", "canceled", "deactivated", "pre_deploy_failed"):
                print(f"{ERR} {name} deploy {status}")
                failed[name] = status
                del pending[name]
            else:
                print(f"   … {name}: {status}")
    for name in pending:
        failed[name] = "timeout"
    return failed


def health_check(urls: dict) -> bool:
    print(f"{INFO} Health-checking all services …")
    all_ok = True
    for name, url in urls.items():
        target = f"{url}/actuator/health"
        ok = False
        for _ in range(10):
            try:
                with urllib.request.urlopen(target, timeout=70) as r:  # free tier cold start ≈50s
                    ok = r.status == 200
                    break
            except Exception:
                time.sleep(15)
        print(f"{OK if ok else ERR} {name}: {target}")
        all_ok = all_ok and ok
    return all_ok


def smoke_test(gateway: str, cfg: dict):
    print(f"{INFO} Running live smoke test against {gateway} …")
    email = f"smoke+{pysecrets.token_hex(4)}@example.com"
    code, body = http("POST", f"{gateway}/api/v1/auth/register",
                      body={"firstName": "Smoke", "lastName": "Test", "email": email,
                            "password": "Passw0rd@123", "monthlyIncome": 90000},
                      ok_codes=(200, 201))
    if code not in (200, 201):
        print(f"{ERR} register → HTTP {code}: {json.dumps(body)[:300]}")
        return False
    token = body["data"]["accessToken"]
    print(f"{OK} register → 201 (userId {body['data']['userId']})")

    code, body = http("GET", f"{gateway}/api/v1/users/me", token=f"Bearer {token}")
    print(f"{OK if code == 200 else ERR} /users/me → HTTP {code}")

    code, body = http("POST", f"{gateway}/api/v1/auth/login",
                      body={"email": cfg["ADMIN_EMAIL"], "password": cfg["ADMIN_PASSWORD"]})
    print(f"{OK if code == 200 else WARN} admin login ({cfg['ADMIN_EMAIL']}) → HTTP {code}")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="preflight only")
    ap.add_argument("--no-push", action="store_true", help="skip git push")
    ap.add_argument("--smoke", action="store_true", help="smoke-test the live stack only")
    args = ap.parse_args()

    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print("  DLMP — automated Render deployment")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    cfg = load_secrets()
    preflight(cfg)
    if args.check:
        # verify connectivity too
        Aiven(cfg["AIVEN_TOKEN"], cfg.get("AIVEN_PROJECT")).connect()
        Upstash(cfg["UPSTASH_EMAIL"], cfg["UPSTASH_API_KEY"]).redis_creds()
        r = Render(cfg["RENDER_API_KEY"], cfg.get("GITHUB_REPO", "https://github.com/Tangella-Manoj/dlmp"))
        r.connect()
        print(f"\n{OK} Preflight passed — run without --check to deploy.")
        return

    ensure_generated_secrets(cfg)
    cfg = load_secrets()

    render = Render(cfg["RENDER_API_KEY"], cfg.get("GITHUB_REPO", "https://github.com/Tangella-Manoj/dlmp"))
    render.connect()
    existing = render.list_services()
    services, urls = {}, {}
    for name, meta in RENDER_SERVICES.items():
        svc = render.ensure_service(existing, name, meta["dockerfile"])
        services[name] = svc
        urls[name] = (svc.get("serviceDetails", {}).get("url")
                      or f"https://{name}.onrender.com")
    print(f"{OK} Render services ready: {len(services)}/6")

    if args.smoke:
        smoke_test(urls["dlmp-gateway"], cfg)
        return

    aiven = Aiven(cfg["AIVEN_TOKEN"], cfg.get("AIVEN_PROJECT"))
    aiven.connect()
    mysql = aiven.mysql_creds()
    kafka = aiven.kafka_creds()
    redis = Upstash(cfg["UPSTASH_EMAIL"], cfg["UPSTASH_API_KEY"]).redis_creds()

    env_maps = build_env_maps(cfg, mysql, kafka, redis, urls)
    for name, env in env_maps.items():
        render.set_env(services[name]["id"], env)
        print(f"{OK} Synced {len(env)} env vars → {name}")

    if not args.no_push:
        git_push()

    deploys = {}
    for name, svc in services.items():
        deploys[name] = (svc["id"], render.deploy(svc["id"]))
    print(f"{OK} Triggered {len(deploys)} deploys")

    failed = wait_for_deploys(render, deploys)
    if failed:
        print(f"\n{ERR} Deploys failed: {failed}")
        print("   Check logs in the Render dashboard, fix, and re-run `make deploy`.")
        sys.exit(1)

    healthy = health_check(urls)
    smoke_test(urls["dlmp-gateway"], cfg)

    print("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print(f"  {'✅ DEPLOYED' if healthy else '⚠️  DEPLOYED WITH WARNINGS'}")
    print(f"  Gateway:      {urls['dlmp-gateway']}")
    print(f"  Admin login:  {cfg['ADMIN_EMAIL']}  (password in .deploy-secrets)")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")


if __name__ == "__main__":
    main()
