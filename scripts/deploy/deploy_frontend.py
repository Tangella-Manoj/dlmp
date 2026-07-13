#!/usr/bin/env python3
"""
DLMP — fully automated Vercel deployment for frontend/.

Uses the Vercel CLI (via npx) non-interactively with a token — the same
one-token-per-provider pattern as auto_deploy.py's Aiven/Upstash/Render flow.

Usage:
    python3 scripts/deploy/deploy_frontend.py            # deploy to production
    python3 scripts/deploy/deploy_frontend.py --check    # verify VERCEL_TOKEN only
"""

import argparse
import json
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
FRONTEND = ROOT / "frontend"
SECRETS_FILE = ROOT / ".deploy-secrets"

OK, WARN, ERR, INFO = "✅", "⚠️ ", "❌", "▸"


def die(msg: str) -> None:
    print(f"\n{ERR} {msg}")
    sys.exit(1)


def load_secrets() -> dict:
    cfg = {}
    if SECRETS_FILE.exists():
        for line in SECRETS_FILE.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip()
    return cfg


def ad_http_get_project(token: str, project_id: str):
    req = urllib.request.Request(
        f"https://api.vercel.com/v9/projects/{project_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode())
    except Exception as e:
        return 0, {"error": str(e)}


def run(cmd, **kwargs):
    # Redact the token value itself (it follows a "--token" arg), not just args
    # literally named VERCEL* — the previous version leaked the raw token to
    # stdout/logs.
    printable = []
    redact_next = False
    for c in cmd:
        if redact_next:
            printable.append("***")
            redact_next = False
        else:
            printable.append(str(c))
        if str(c) == "--token":
            redact_next = True
    print(f"{INFO} {' '.join(printable)}")
    return subprocess.run(cmd, cwd=FRONTEND, text=True, **kwargs)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print("  DLMP frontend — automated Vercel deployment")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    cfg = load_secrets()
    token = cfg.get("VERCEL_TOKEN")
    if not token:
        print(f"""
{ERR} Missing VERCEL_TOKEN in .deploy-secrets

One-time setup (~1 minute, logged in with tangellamanoj9@gmail.com):

  https://vercel.com/account/tokens → Create Token

Then add to .deploy-secrets:
    VERCEL_TOKEN=<paste here>

Then:   python3 scripts/deploy/deploy_frontend.py
""")
        sys.exit(2)

    r = run(["npx", "--yes", "vercel", "whoami", "--token", token], capture_output=True)
    if r.returncode != 0:
        die(f"VERCEL_TOKEN rejected: {r.stderr or r.stdout}")
    print(f"{OK} Vercel authenticated as {r.stdout.strip()}")
    if args.check:
        return

    api_base = cfg.get("API_BASE_URL", "https://dlmp-gateway.onrender.com")

    # Link (or create) the Vercel project non-interactively.
    r = run(
        ["npx", "--yes", "vercel", "link", "--yes", "--token", token],
        capture_output=True,
    )
    if r.returncode != 0:
        die(f"vercel link failed: {r.stderr or r.stdout}")
    print(f"{OK} Vercel project linked")

    # Ensure VITE_API_BASE_URL is set for production builds (idempotent: remove-then-add).
    run(
        ["npx", "--yes", "vercel", "env", "rm", "VITE_API_BASE_URL", "production", "--yes", "--token", token],
        capture_output=True,
    )
    r = run(
        ["npx", "--yes", "vercel", "env", "add", "VITE_API_BASE_URL", "production", "--token", token],
        input=api_base, capture_output=True,
    )
    if r.returncode != 0:
        die(f"Could not set VITE_API_BASE_URL: {r.stderr or r.stdout}")
    print(f"{OK} VITE_API_BASE_URL = {api_base}")

    # Build + deploy to production.
    r = run(["npx", "--yes", "vercel", "--prod", "--yes", "--token", token], capture_output=True)
    if r.returncode != 0:
        die(f"Deployment failed: {r.stderr or r.stdout}")
    # Vercel CLI's output format isn't stable across invocations — sometimes a
    # plain "https://..." line, sometimes a JSON blob (seen when redeploying
    # an already-linked project). Try JSON first, fall back to line-scanning.
    url = None
    try:
        payload = json.loads(r.stdout)
        url = payload.get("deployment", {}).get("url")
        if url and not url.startswith("http"):
            url = f"https://{url}"
    except (json.JSONDecodeError, AttributeError):
        pass
    if not url:
        url = next(
            (line.strip() for line in reversed(r.stdout.splitlines()) if line.strip().startswith("https://")),
            None,
        )
    if not url:
        die(f"Could not find deployment URL in Vercel output:\n{r.stdout}")
    print(f"{OK} Deployed: {url}")

    # Read back the linked project and warn if Vercel's default "Authentication"
    # (SSO) wall is on — it 302s every visitor to a Vercel login page instead
    # of serving the app, and is enabled by default for team-owned projects.
    project_json = FRONTEND / ".vercel" / "project.json"
    project_id = None
    if project_json.exists():
        proj = json.loads(project_json.read_text())
        project_id = proj.get("projectId")
        print(f"{INFO} Vercel project: {project_id}")

    if project_id:
        code, body = ad_http_get_project(token, project_id)
        if code == 200 and body.get("ssoProtection"):
            print(f"\n{WARN} Vercel Authentication is ON for this project — the URL above "
                  f"redirects every visitor to a Vercel login page instead of the app.")
            print(f"   Disable it yourself (Project → Settings → Deployment Protection → "
                  f"Vercel Authentication → Off), or ask your assistant to do it — that's a "
                  f"security-relevant change it should confirm with you first.")

    print("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print(f"  {OK} FRONTEND DEPLOYED")
    print(f"  {url}")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print(f"\n{WARN} Next: add this URL to the gateway's CORS_ALLOWED_ORIGINS")
    print("  (run: python3 scripts/deploy/update_cors.py <this-url>)")


if __name__ == "__main__":
    main()
