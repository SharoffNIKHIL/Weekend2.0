# infra/scripts/promote.py
"""Move CODE between branches while each branch keeps its OWN environment values.

Branch model (docs/BRANCHING.md v1.4): main/release hold the raw code (values templates only);
dev and prod hold the same code plus infra/values/<env>*.tfvars. A plain `git merge` would carry
dev values into main, or delete them from dev on a back-merge. This script merges the source
branch into a new promotion branch cut from the target, then puts infra/values/ back exactly as
the target had it (templates *.example always follow the code). Nothing is pushed.

Usage (from the repo root, clean working tree):
    python3 infra/scripts/promote.py --source dev --target main      # dev code → main (no values)
    python3 infra/scripts/promote.py --source main --target release
    python3 infra/scripts/promote.py --source main --target dev      # back-sync, dev values kept
Then: git push -u origin <printed branch> and open a PR into the target.
"""
from __future__ import annotations

import argparse
import logging
import subprocess
import sys
from datetime import datetime, timedelta, timezone

LOG = logging.getLogger("promote")
VALUES = "infra/values"
IST = timezone(timedelta(hours=5, minutes=30))


def git(*args: str, check: bool = True) -> str:
    proc = subprocess.run(["git", *args], capture_output=True, text=True, check=False)
    if check and proc.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)}: {proc.stderr.strip() or proc.stdout.strip()}")
    return proc.stdout.strip()


def env_values(ref: str) -> set[str]:
    """Value files (not templates) under infra/values/ in a ref."""
    files = git("ls-tree", "-r", "--name-only", ref, "--", VALUES, check=False).splitlines()
    return {f for f in files if not f.endswith(".example")}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", required=True)
    ap.add_argument("--target", required=True)
    ap.add_argument("--branch", help="promotion branch name (default: promote/<source>-to-<target>-<date>)")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    try:
        if git("status", "--porcelain"):
            raise RuntimeError("working tree is not clean")
        branch = args.branch or f"promote/{args.source}-to-{args.target}-{datetime.now(IST):%Y%m%d-%H%M}"
        keep = env_values(args.target)
        git("switch", "-c", branch, args.target)
        merge = subprocess.run(["git", "merge", "--no-ff", "--no-commit", args.source],
                               capture_output=True, text=True, check=False)
        # Restore the target's own values; drop value files the target never had.
        for path in env_values("HEAD") | env_values(args.source):
            if path in keep:
                git("checkout", args.target, "--", path)
            else:
                git("rm", "-q", "--cached", "--ignore-unmatch", "--", path)
                subprocess.run(["rm", "-f", path], check=False)
        unresolved = [f for f in git("diff", "--name-only", "--diff-filter=U").splitlines() if f]
        if unresolved:
            raise RuntimeError(f"merge conflicts to resolve by hand on {branch}: {unresolved}")
        if merge.returncode != 0 and "Automatic merge failed" not in merge.stdout + merge.stderr:
            raise RuntimeError(merge.stderr.strip() or merge.stdout.strip())
        if not git("diff", "--cached", "--name-only") and merge.returncode == 0 and "Already up to date" in merge.stdout:
            LOG.info("%s already contains %s", args.target, args.source)
            return 0
        git("commit", "-q", "-m", f"chore(promote): {args.source} → {args.target} (code only; {args.target} keeps its env values)")
    except RuntimeError as exc:
        LOG.error("%s", exc)
        return 1

    LOG.info("promotion branch %s ready. Value files kept: %s", branch, sorted(keep) or "none")
    LOG.info("next: git push -u origin %s && gh pr create --base %s --head %s", branch, args.target, branch)
    return 0


if __name__ == "__main__":
    sys.exit(main())
