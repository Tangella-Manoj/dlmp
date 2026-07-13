#!/usr/bin/env python3
"""
Add an origin to the gateway's CORS_ALLOWED_ORIGINS (idempotent — skips if
already present) and redeploy so it takes effect immediately.

Usage:
    python3 scripts/deploy/update_cors.py https://your-app.vercel.app
"""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import auto_deploy as ad  # noqa: E402

OK, ERR, INFO = "✅", "❌", "▸"


def main():
    if len(sys.argv) != 2:
        print("Usage: python3 scripts/deploy/update_cors.py <origin-url>")
        sys.exit(1)
    new_origin = sys.argv[1].rstrip("/")

    cfg = ad.load_secrets()
    render = ad.Render(cfg["RENDER_API_KEY"], cfg.get("GITHUB_REPO", "https://github.com/Tangella-Manoj/dlmp"))
    render.connect()
    services = render.list_services()
    gw = services["dlmp-gateway"]

    code, envs = ad.http("GET", f"{ad.Render.BASE}/services/{gw['id']}/env-vars", token=render.auth)
    current = ""
    for e in envs:
        ev = e.get("envVar", e)
        if ev.get("key") == "CORS_ALLOWED_ORIGINS":
            current = ev.get("value", "")

    origins = [o.strip() for o in current.split(",") if o.strip()]
    if new_origin in origins:
        print(f"{OK} {new_origin} already in CORS_ALLOWED_ORIGINS — nothing to do")
        return

    origins.append(new_origin)
    new_value = ",".join(origins)
    code, resp = ad.http(
        "PUT",
        f"{ad.Render.BASE}/services/{gw['id']}/env-vars/CORS_ALLOWED_ORIGINS",
        token=render.auth,
        body={"value": new_value},
    )
    if code != 200:
        print(f"{ERR} Failed to update CORS_ALLOWED_ORIGINS: {resp}")
        sys.exit(1)
    print(f"{OK} CORS_ALLOWED_ORIGINS updated: {new_value}")

    deploy_id = render.deploy(gw["id"])
    print(f"{INFO} Redeploying gateway to apply the change …")
    start = time.time()
    while time.time() - start < 600:
        status = render.deploy_status(gw["id"], deploy_id)
        if status == "live":
            print(f"{OK} Gateway redeployed and live ({int(time.time()-start)}s)")
            return
        if status in ("build_failed", "update_failed", "canceled", "deactivated", "pre_deploy_failed"):
            print(f"{ERR} Redeploy failed: {status}")
            sys.exit(1)
        time.sleep(15)
    print(f"{ERR} Timed out waiting for redeploy")
    sys.exit(1)


if __name__ == "__main__":
    main()
