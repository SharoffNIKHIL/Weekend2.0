# infra/scripts/plan_env.py
"""Run `terraform plan` for one environment and write a masked Markdown report for a PR.

Nothing is applied. The binary plan stays in ~/.tfplans (outside the repo). The report
masks the AWS account ID and e-mail addresses so it can be pasted into a public PR.

Usage (from the repo root):
    python infra/scripts/plan_env.py --env dev                 # remote S3 state (after bootstrap)
    python infra/scripts/plan_env.py --env dev --local-state   # before bootstrap: throwaway local state
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
ACCOUNT_RE = re.compile(r"(?<![0-9])[0-9]{12}(?![0-9])")
ACTION_ORDER = ("create", "update", "replace", "delete", "read", "no-op")


def run(cmd: list[str], cwd: Path) -> str:
    """Run a command, return stdout, raise with stderr on failure."""
    LOG.info("running: %s", " ".join(cmd))
    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(mask(proc.stderr or proc.stdout))
    return proc.stdout


def mask(text: str) -> str:
    """Hide account IDs and e-mail addresses (repo is public)."""
    text = EMAIL_RE.sub("<EMAIL>", text)
    return ACCOUNT_RE.sub("<ACCOUNT_ID>", text)


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
        f"## Terraform plan — `infra/envs/{env}`",
        "",
        f"- Generated: {now} · {version} · state: {state}",
        f"- **Plan: {counts['create']} to add, {counts['update']} to change, "
        f"{counts['replace'] + counts['delete']} to destroy** (replace: {counts['replace']})",
        "- Account ID and e-mail addresses are masked. Nothing has been applied.",
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
    ap.add_argument("--env", default="dev", choices=["dev", "prod"])
    ap.add_argument("--local-state", action="store_true", help="plan with throwaway local state (no state bucket needed)")
    ap.add_argument("--out", type=Path, help="Markdown report path (default: ~/.tfplans/weekend2-<env>-plan.md)")
    ap.add_argument("--terraform", default=shutil.which("terraform") or "terraform")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    env_dir = INFRA / "envs" / args.env
    if not (env_dir / "terraform.tfvars").exists():
        LOG.error("missing %s/terraform.tfvars (copy terraform.tfvars.example)", env_dir)
        return 2
    PLAN_DIR.mkdir(mode=0o700, exist_ok=True)
    plan_file = PLAN_DIR / f"weekend2-{args.env}.tfplan"
    out = args.out or PLAN_DIR / f"weekend2-{args.env}-plan.md"

    try:
        version = run([args.terraform, "version"], env_dir).splitlines()[0]
        if args.local_state:
            # Copy infra/ to a temp dir and override the S3 backend with a local one.
            with tempfile.TemporaryDirectory() as tmp:
                work_infra = Path(tmp) / "infra"
                shutil.copytree(INFRA, work_infra, ignore=shutil.ignore_patterns(".terraform", ".build"))
                work = work_infra / "envs" / args.env
                (work / "backend_override.tf").write_text('terraform {\n  backend "local" {}\n}\n')
                run([args.terraform, "init", "-no-color", "-input=false", "-lockfile=readonly"], work)
                run([args.terraform, "plan", "-no-color", "-input=false", "-lock=false", f"-out={plan_file}"], work)
                text = run([args.terraform, "show", "-no-color", str(plan_file)], work)
                plan_json = json.loads(run([args.terraform, "show", "-json", str(plan_file)], work))
            state = "throwaway local state (pre-bootstrap)"
        else:
            run([args.terraform, "init", "-no-color", "-input=false", "-backend-config=backend.hcl", "-lockfile=readonly"], env_dir)
            run([args.terraform, "plan", "-no-color", "-input=false", f"-out={plan_file}"], env_dir)
            text = run([args.terraform, "show", "-no-color", str(plan_file)], env_dir)
            plan_json = json.loads(run([args.terraform, "show", "-json", str(plan_file)], env_dir))
            state = "remote S3 state"
    except RuntimeError as exc:
        LOG.error("terraform failed:\n%s", exc)
        return 1

    counts, rows = summarise(plan_json)
    out.write_text(report(args.env, version, counts, rows, mask(text), state))
    out.chmod(0o600)
    LOG.info("plan saved: %s", plan_file)
    LOG.info("report written: %s (%d to add)", out, counts["create"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
