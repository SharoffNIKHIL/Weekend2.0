# infra/scripts/cost_guard.py
"""Fail when a Terraform plan would create or change anything that is NOT free of cost.

The infra CD pipeline runs this between `plan` and `apply -auto-approve` (owner rule 2026-10-03:
automatic applies only for free-of-cost resources). Anything outside the allowlist, or a free
resource configured in a paid way, blocks the apply; the owner then applies by hand after a
security checkpoint.

Usage:
    terraform show -json tfplan > plan.json
    python3 infra/scripts/cost_guard.py plan.json
Exit code: 0 = only free resources, 1 = paid resource found, 2 = bad input.
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

LOG = logging.getLogger("cost_guard")

# Free at one-user scale (DESIGN §21.2, free tiers per billing account; checked 2026-10-03).
FREE_TYPES = {
    "google_artifact_registry_repository",       # 0.5 GB/month free (cleanup policy keeps 2-3 images)
    "google_billing_budget",
    "google_cloud_run_v2_service",               # free tier; scale to zero (checked below)
    "google_cloud_run_v2_service_iam_member",
    "google_cloud_scheduler_job",                # 3 jobs free per billing account
    "google_cloud_tasks_queue",                  # 1M operations free
    "google_cloud_tasks_queue_iam_member",
    "google_compute_firewall",
    "google_compute_network",
    "google_compute_router",                     # the router is free; NAT is not (not listed)
    "google_compute_subnetwork",
    "google_firestore_database",                 # free quota (checked below: no PITR)
    "google_firestore_backup_schedule",          # backups are billed per GB — only allowed with 0 days (not created)
    "google_monitoring_notification_channel",
    "google_project_iam_member",
    "google_secret_manager_secret",              # 6 active versions free
    "google_secret_manager_secret_iam_member",
    "google_service_account",
    "google_service_account_iam_member",
    "google_storage_bucket",                     # free only in US regions (checked below)
    "google_storage_bucket_iam_member",
}
FREE_BUCKET_LOCATIONS = {"US-CENTRAL1", "US-EAST1", "US-WEST1"}  # 5 GB Standard free


def check(change: dict) -> list[str]:
    """Return reasons why this resource change is not free."""
    rtype, after = change["type"], change["change"].get("after") or {}
    addr = change["address"]
    if rtype not in FREE_TYPES:
        return [f"{addr}: {rtype} is not on the free allowlist"]
    problems: list[str] = []
    if rtype == "google_firestore_backup_schedule":
        problems.append(f"{addr}: Firestore backups are billed per GB stored")
    if rtype == "google_firestore_database" and after.get("point_in_time_recovery_enablement") == "POINT_IN_TIME_RECOVERY_ENABLED":
        problems.append(f"{addr}: point-in-time recovery is billed")
    if rtype == "google_storage_bucket" and str(after.get("location", "")).upper() not in FREE_BUCKET_LOCATIONS:
        problems.append(f"{addr}: bucket location {after.get('location')} is outside the free-tier regions")
    if rtype == "google_cloud_run_v2_service":
        for tmpl in after.get("template") or []:
            for scaling in tmpl.get("scaling") or []:
                if (scaling.get("min_instance_count") or 0) > 0:
                    problems.append(f"{addr}: min instances > 0 is billed while idle")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("plan_json", type=Path)
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    try:
        plan = json.loads(args.plan_json.read_text())
    except (OSError, json.JSONDecodeError) as exc:
        LOG.error("cannot read plan JSON: %s", exc)
        return 2

    problems: list[str] = []
    checked = 0
    for rc in plan.get("resource_changes", []):
        actions = rc["change"]["actions"]
        if rc.get("mode") != "managed" or actions in (["no-op"], ["read"], ["delete"]):
            continue
        checked += 1
        problems.extend(check(rc))

    if problems:
        LOG.error("NOT free of cost — CD will not apply. Apply by hand after a security checkpoint:")
        for p in problems:
            LOG.error("  - %s", p)
        return 1
    LOG.info("cost guard OK: %d create/update changes, all on the free allowlist", checked)
    return 0


if __name__ == "__main__":
    sys.exit(main())
