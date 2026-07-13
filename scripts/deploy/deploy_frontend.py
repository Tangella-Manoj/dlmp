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


def run(cmd, **kwargs):
    print(f"{INFO} {' '.join(str(c) for c in cmd if not str(c).startswith('VERCEL'))}")
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
    url = r.stdout.strip().splitlines()[-1]
    print(f"{OK} Deployed: {url}")

    # Read back the linked project name so we can report the stable production URL too.
    project_json = FRONTEND / ".vercel" / "project.json"
    if project_json.exists():
        proj = json.loads(project_json.read_text())
        print(f"{INFO} Vercel project: {proj.get('projectId', '?')}")

    print("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print(f"  {OK} FRONTEND DEPLOYED")
    print(f"  {url}")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print(f"\n{WARN} Next: add this URL to the gateway's CORS_ALLOWED_ORIGINS")
    print("  (run: python3 scripts/deploy/update_cors.py <this-url>)")


if __name__ == "__main__":
    main()
