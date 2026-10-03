# infra/scripts/tf.py
"""Run Terraform on your Mac with SHORT-LIVED credentials only (no key files, no ADC needed).

How it works:
  1. Reads ../credentials/<project>-<env>.terraform.json (outside the repo; see credentials/README.md):
     the deployer service account to impersonate. It holds no secret.
  2. Asks gcloud (your normal `gcloud auth login`) for a 1-hour access token of that service account:
     `gcloud auth print-access-token --impersonate-service-account=<SA>`.
  3. Runs terraform with the token in GOOGLE_OAUTH_ACCESS_TOKEN (process memory only; never written
     to disk, never passed as -backend-config so it can't land in .terraform/).
  4. Passes infra/values/<env>[.bootstrap].tfvars and ../credentials/<project>-<env>.secrets.tfvars.

Bootstrap creates the deployer, so its first run uses YOUR token (--as-owner), also 1 hour.

Usage (from the repo root):
    python3 infra/scripts/tf.py --env dev --root bootstrap --as-owner plan
    python3 infra/scripts/tf.py --env dev --root bootstrap --as-owner apply
    python3 infra/scripts/tf.py --env dev plan                  # stack, as the deployer
    python3 infra/scripts/tf.py --env dev apply                 # applies the saved plan only
Exit code: terraform's, or 2 for a setup problem.
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import shutil
import subprocess
import sys
from pathlib import Path

LOG = logging.getLogger("tf")
REPO = Path(__file__).resolve().parents[2]
INFRA = REPO / "infra"
CREDENTIALS = REPO.parent / "credentials"  # beside the repo folder, never inside it
PLAN_DIR = Path.home() / ".tfplans"


class SetupError(Exception):
    """Missing file, tool or login."""


def load_config(env: str) -> dict[str, str]:
    """Find exactly one ../credentials/*-<env>.terraform.json and return it."""
    matches = sorted(CREDENTIALS.glob(f"*-{env}.terraform.json"))
    if len(matches) != 1:
        raise SetupError(f"expected one {CREDENTIALS}/<project>-{env}.terraform.json, found {len(matches)}")
    cfg = json.loads(matches[0].read_text())
    for key in ("project_id", "service_account", "secrets_tfvars"):
        if not cfg.get(key):
            raise SetupError(f"{matches[0].name}: missing '{key}'")
    cfg["secrets_tfvars"] = str((CREDENTIALS / cfg["secrets_tfvars"]).resolve())
    if not Path(cfg["secrets_tfvars"]).exists():
        raise SetupError(f"missing {cfg['secrets_tfvars']}")
    return cfg


def access_token(service_account: str | None) -> str:
    """1-hour OAuth token from gcloud: impersonated, or the owner's own when service_account is None."""
    cmd = ["gcloud", "auth", "print-access-token"]
    if service_account:
        cmd.append(f"--impersonate-service-account={service_account}")
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        raise SetupError("gcloud could not mint a token (run `gcloud auth login`; for the deployer you need "
                         "roles/iam.serviceAccountTokenCreator on it):\n" + proc.stderr.strip())
    return proc.stdout.strip()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--env", required=True)
    ap.add_argument("--root", choices=["stack", "bootstrap"], default="stack")
    ap.add_argument("--as-owner", action="store_true", help="use your own 1 h token (bootstrap's first run)")
    ap.add_argument("action", choices=["init", "plan", "apply", "output", "destroy-plan"])
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    try:
        terraform = shutil.which("terraform")
        if not terraform or not shutil.which("gcloud"):
            raise SetupError("terraform and gcloud must be on PATH")
        cfg = load_config(args.env)
        suffix = ".bootstrap.tfvars" if args.root == "bootstrap" else ".tfvars"
        values = INFRA / "values" / f"{args.env}{suffix}"
        if not values.exists():
            raise SetupError(f"missing {values.relative_to(REPO)} (it lives only on the {args.env} branch)")
        token = access_token(None if args.as_owner else cfg["service_account"])
    except SetupError as exc:
        LOG.error("%s", exc)
        return 2

    workdir = INFRA / args.root
    env = {**os.environ, "GOOGLE_OAUTH_ACCESS_TOKEN": token}
    env.pop("GOOGLE_APPLICATION_CREDENTIALS", None)  # never fall back to a key file
    var_files = [f"-var-file={values}", f"-var-file={cfg['secrets_tfvars']}"]
    PLAN_DIR.mkdir(mode=0o700, exist_ok=True)
    plan_file = PLAN_DIR / f"{cfg['project_id']}-{args.env}-{args.root}.tfplan"

    def tf(*cmd: str) -> int:
        LOG.info("terraform %s", " ".join(c for c in cmd if not c.startswith("-var-file")))
        return subprocess.run([terraform, *cmd], cwd=workdir, env=env, check=False).returncode

    if args.root == "stack":
        rc = tf("init", "-input=false", "-reconfigure", f"-backend-config=bucket={cfg['project_id']}-tfstate",
                f"-backend-config=prefix=stack/{args.env}")
    else:
        rc = tf("init", "-input=false")  # bootstrap keeps local state (it creates the state bucket)
    if rc or args.action == "init":
        return rc
    if args.action == "plan":
        return tf("plan", "-input=false", *var_files, f"-out={plan_file}")
    if args.action == "destroy-plan":
        return tf("plan", "-destroy", "-input=false", *var_files, f"-out={plan_file}")
    if args.action == "output":
        return tf("output")
    if not plan_file.exists():
        LOG.error("no saved plan %s — run plan first and review it", plan_file)
        return 2
    rc = tf("apply", "-input=false", str(plan_file))
    plan_file.unlink(missing_ok=True)  # saved plans hold variable values in cleartext
    return rc


if __name__ == "__main__":
    sys.exit(main())
