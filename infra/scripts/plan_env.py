# infra/scripts/plan_env.py
"""Run `terraform plan` of infra/stack for one environment and write a masked Markdown report for a PR.

Used by CI (infra-ci / infra-cd). Credentials come from the environment (Workload Identity
Federation in CI). Inputs: infra/values/<env>.tfvars (committed on the env branch) and
infra/stack/secrets.auto.tfvars (written by CI from environment secrets, never committed).
Nothing is applied. The report masks project IDs/numbers, billing account IDs and e-mail
addresses because the repo is public. --json also writes the plan as JSON for cost_guard.py.

Usage (from the repo root):
    python3 infra/scripts/plan_env.py --env dev --state-bucket <PROJECT_ID>-tfstate --out plan.md
    python3 infra/scripts/plan_env.py --env dev --local-state --out plan.md   # before bootstrap
"""
from __future__ import annotations

import argparse
import json
import logging
import re
import shutil
import subprocess
import sys
import tempfile
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

LOG = logging.getLogger("plan_env")
INFRA = Path(__file__).resolve().parents[1]
PLAN_DIR = Path.home() / ".tfplans"
IST = timezone(timedelta(hours=5, minutes=30))
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
NUMBER_RE = re.compile(r"(?<![0-9])[0-9]{10,13}(?![0-9])")  # GCP project numbers
BILLING_RE = re.compile(r"\b[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}\b")
PROJECT_RE = re.compile(r'^\s*project_id\s*=\s*"([^"]+)"', re.MULTILINE)
PROJECT_IDS: list[str] = []
ACTION_ORDER = ("create", "update", "replace", "delete", "read", "no-op")


def run(cmd: list[str], cwd: Path) -> str:
    """Run a command, return stdout, raise with stderr on failure."""
    LOG.info("running: %s", " ".join(cmd))
    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        raise RuntimeError(mask(proc.stderr or proc.stdout))
    return proc.stdout


def mask(text: str) -> str:
    """Hide project IDs/numbers, billing account IDs and e-mail addresses (repo is public)."""
    for pid in PROJECT_IDS:
        text = text.replace(pid, "<PROJECT_ID>")
    text = EMAIL_RE.sub("<EMAIL>", text)
    text = BILLING_RE.sub("<BILLING_ACCOUNT_ID>", text)
    return NUMBER_RE.sub("<PROJECT_NUMBER>", text)


def action_of(change: dict) -> str:
    actions = change["change"]["actions"]
    if actions in (["delete", "create"], ["create", "delete"]):
        return "replace"
    return actions[0]


def summarise(plan_json: dict) -> tuple[Counter, list[tuple[str, str]]]:
    counts: Counter = Counter()
    rows: list[tuple[str, str]] = []
    for rc in plan_json.get("resource_changes", []):
        if rc.get("mode") != "managed":
            continue
        act = action_of(rc)
        counts[act] += 1
        if act != "no-op":
            rows.append((act, rc["address"]))
    rows.sort(key=lambda r: (ACTION_ORDER.index(r[0]), r[1]))
    return counts, rows


def report(env: str, version: str, counts: Counter, rows: list[tuple[str, str]], text: str, state: str) -> str:
    now = datetime.now(IST).strftime("%Y-%m-%d %H:%M IST")
    lines = [
        f"## Terraform plan — `infra/stack` · env `{env}`",
        "",
        f"- Generated: {now} · {version} · state: {state}",
        (
            f"- **Plan: {counts['create']} to add, {counts['update']} to change, "
            f"{counts['replace'] + counts['delete']} to destroy** (replace: {counts['replace']})"
        ),
        "- Project ID/number, billing account and e-mail addresses are masked. Nothing has been applied.",
        "",
        "| Action | Resource |",
        "|---|---|",
        *[f"| {a} | `{r}` |" for a, r in rows],
        "",
        "<details><summary>Full plan output (masked)</summary>",
        "",
        "```hcl",
        text.strip(),
        "```",
        "",
        "</details>",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--env", required=True)
    ap.add_argument("--state-bucket", help="GCS state bucket (bootstrap output state_bucket)")
    ap.add_argument("--local-state", action="store_true", help="plan with throwaway local state (no state bucket needed)")
    ap.add_argument("--out", type=Path, help="Markdown report path (default: ~/.tfplans/weekend2-<env>-plan.md)")
    ap.add_argument("--json", type=Path, help="also write the plan as JSON (input for cost_guard.py)")
    ap.add_argument("--plan-file", type=Path, help="binary plan path (default: ~/.tfplans/weekend2-<env>.tfplan)")
    ap.add_argument("--terraform", default=shutil.which("terraform") or "terraform")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    env_dir = INFRA / "stack"
    values = INFRA / "values" / f"{args.env}.tfvars"
    secrets = env_dir / "secrets.auto.tfvars"
    if not values.exists() or not secrets.exists():
        LOG.error("need %s (env branch) and %s (written from secrets)", values, secrets)
        return 2
    if not args.local_state and not args.state_bucket:
        LOG.error("pass --state-bucket or --local-state")
        return 2
    PROJECT_IDS.extend(PROJECT_RE.findall(secrets.read_text()))
    PLAN_DIR.mkdir(mode=0o700, exist_ok=True)
    plan_file = (args.plan_file or PLAN_DIR / f"weekend2-{args.env}.tfplan").resolve()
    var_file = f"-var-file={values}"
    out = args.out or PLAN_DIR / f"weekend2-{args.env}-plan.md"

    try:
        version = run([args.terraform, "version"], env_dir).splitlines()[0]
        if args.local_state:
            # Copy infra/ to a temp dir and override the GCS backend with a local one.
            with tempfile.TemporaryDirectory() as tmp:
                work_infra = Path(tmp) / "infra"
                shutil.copytree(INFRA, work_infra, ignore=shutil.ignore_patterns(".terraform", ".build"))
                work = work_infra / "stack"
                (work / "backend_override.tf").write_text('terraform {\n  backend "local" {}\n}\n')
                run([args.terraform, "init", "-no-color", "-input=false", "-lockfile=readonly"], work)
                run([args.terraform, "plan", "-no-color", "-input=false", "-lock=false", var_file, f"-out={plan_file}"], work)
                text = run([args.terraform, "show", "-no-color", str(plan_file)], work)
                plan_json = json.loads(run([args.terraform, "show", "-json", str(plan_file)], work))
            state = "throwaway local state (pre-bootstrap)"
        else:
            run([args.terraform, "init", "-no-color", "-input=false", "-lockfile=readonly",
                 f"-backend-config=bucket={args.state_bucket}", f"-backend-config=prefix=stack/{args.env}"], env_dir)
            # CI plans for PRs don't take the state lock (read-only plan service account).
            run([args.terraform, "plan", "-no-color", "-input=false", "-lock=false", var_file, f"-out={plan_file}"], env_dir)
            text = run([args.terraform, "show", "-no-color", str(plan_file)], env_dir)
            plan_json = json.loads(run([args.terraform, "show", "-json", str(plan_file)], env_dir))
            state = "remote GCS state"
    except RuntimeError as exc:
        LOG.error("terraform failed:\n%s", exc)
        return 1

    counts, rows = summarise(plan_json)
    out.write_text(report(args.env, version, counts, rows, mask(text), state))
    if args.json:
        args.json.write_text(json.dumps(plan_json))
        args.json.chmod(0o600)
    out.chmod(0o600)
    LOG.info("plan saved: %s", plan_file)
    LOG.info("report written: %s (%d to add)", out, counts["create"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
