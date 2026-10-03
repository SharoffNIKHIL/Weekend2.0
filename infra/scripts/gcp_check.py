# infra/scripts/gcp_check.py
"""Read-only pre-flight check of the owner's Google Cloud connection before any Terraform run.

Checks the gcloud user login, Application Default Credentials (what Terraform uses), the
billing account state, the project's billing link and the APIs the stack needs. It never
creates, changes or deletes anything. Billing account IDs are masked in the output.

Usage (from the repo root):
    python3 infra/scripts/gcp_check.py --project weekend2-0
Exit code: 0 = ready to plan/apply, 1 = blocked (reasons printed), 2 = gcloud missing.
"""
from __future__ import annotations

import argparse
import json
import logging
import re
import shutil
import subprocess
import sys

LOG = logging.getLogger("gcp_check")
BILLING_RE = re.compile(r"[0-9A-F]{6}-[0-9A-F]{6}-([0-9A-F]{6})")
REQUIRED_APIS = (
    "cloudresourcemanager.googleapis.com",
    "serviceusage.googleapis.com",
    "iam.googleapis.com",
    "storage.googleapis.com",
)


def mask(text: str) -> str:
    """Keep only the last block of a billing account ID."""
    return BILLING_RE.sub(r"XXXXXX-XXXXXX-\1", text)


def gcloud(*args: str) -> tuple[int, str]:
    """Run a read-only gcloud command; return (exit code, stdout or stderr)."""
    proc = subprocess.run(["gcloud", *args], capture_output=True, text=True)
    return proc.returncode, (proc.stdout if proc.returncode == 0 else proc.stderr).strip()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--project", required=True, help="GCP project ID, e.g. weekend2-0")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    if not shutil.which("gcloud"):
        LOG.error("gcloud is not installed")
        return 2

    blockers: list[str] = []

    rc, out = gcloud("auth", "list", "--filter=status:ACTIVE", "--format=value(account)")
    if rc != 0 or not out:
        blockers.append("no active gcloud login: run `gcloud auth login`")
    else:
        LOG.info("gcloud user login: OK")

    rc, _ = gcloud("auth", "application-default", "print-access-token")
    if rc != 0:
        blockers.append("Application Default Credentials missing/expired: run `gcloud auth application-default login`")
    else:
        LOG.info("Application Default Credentials: OK")

    rc, out = gcloud("projects", "describe", args.project, "--format=json")
    if rc != 0:
        blockers.append(f"project {args.project} not visible: {out.splitlines()[-1] if out else 'unknown error'}")
        project_ok = False
    else:
        project_ok = json.loads(out).get("lifecycleState") == "ACTIVE"
        LOG.info("project %s: %s", args.project, "ACTIVE" if project_ok else "NOT ACTIVE")
        if not project_ok:
            blockers.append(f"project {args.project} is not ACTIVE")

    if project_ok:
        rc, out = gcloud("billing", "projects", "describe", args.project, "--format=json")
        info = json.loads(out) if rc == 0 else {}
        account = info.get("billingAccountName", "")
        LOG.info("billing link: %s, enabled=%s", mask(account) or "none", info.get("billingEnabled"))
        if not account:
            blockers.append("no billing account linked: gcloud billing projects link <PROJECT> --billing-account=<ID>")
        else:
            rc, out = gcloud("billing", "accounts", "describe", account.split("/")[-1], "--format=value(open)")
            if rc == 0 and out.strip().lower() != "true":
                blockers.append("billing account is CLOSED: re-open it or create a new one (Console → Billing)")
        if not info.get("billingEnabled"):
            blockers.append("billing is not enabled on the project (needs an OPEN linked billing account)")

        rc, out = gcloud("services", "list", "--enabled", f"--project={args.project}", "--format=value(config.name)")
        enabled = set(out.split()) if rc == 0 else set()
        missing = [a for a in REQUIRED_APIS if a not in enabled]
        LOG.info("required APIs enabled: %d/%d", len(REQUIRED_APIS) - len(missing), len(REQUIRED_APIS))
        if missing:
            LOG.info("not yet enabled (infra/bootstrap enables them): %s", ", ".join(missing))

    if blockers:
        for b in blockers:
            LOG.error("BLOCKED: %s", b)
        return 1
    LOG.info("READY: terraform plan/apply can run against %s", args.project)
    return 0


if __name__ == "__main__":
    sys.exit(main())
