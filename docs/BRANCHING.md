# Branching strategy — Weekend 2.0

**Version 1.3 · 2026-10-03 · Owner: Nikhil** (1.3: feature sequence, `feature_infra` merged + removed, `Feature_code`; 1.2: GCP; 1.1: values in secrets)

The code is the same for every environment. **Environment values are never in the code.** They live in GitHub **environment secrets** and are written to a temporary `terraform.tfvars` only while a workflow runs.

## Branches

| Branch | Created from | Purpose | Merges into | Status |
|---|---|---|---|---|
| `main` | — | Base code: modules, env roots, scripts, docs, tracker | — (default branch) | Active |
| `dev` | `main` | Integration branch for the dev environment (GCP project `weekend2-dev-<suffix>`, asia-south1) | Nothing yet; code is promoted to `main` later through `release` | Active |
| `Feature_<area>` / `feature_<area>` | `dev` | One isolated feature at a time, developed and tested completely, then merged into `dev` and removed | `dev` (PR) | `feature_infra` ✅ merged (PR #2) and deleted · **`Feature_code` active** |
| `release` | `main` | Future: stabilise a release (dev → release → main) | `main` | **Created, idle** |
| `prod` | `main` | Future: the production environment | — | **Created, idle** (after D1) |

```
main ────●─────────────────────────────────────────►
         ├── release   (idle)
         ├── prod      (idle)
         └── dev ──────●──────────●──────────●─────►
                        \        ↗ \        ↗
                         feature_infra    feature_app …   (PR → dev)
```

## Feature sequence (owner's plan, 2026-10-03)
Each feature is built in its own branch from `dev`, completely developed and tested against the `dev` base, merged by PR, then deleted:

| # | Branch | Scope | Status |
|---|---|---|---|
| 1 | `feature_infra` | Terraform for GCP, CI, plan tooling | ✅ Merged into `dev` (PR #2), branch removed |
| 2 | `Feature_code` | Java application core: agent, tools (plugins), memory, reminders, retention, API, PWA UI/UX — with in-memory adapters | 🟡 PR open |
| 3 | `Feature_database` | Firestore adapters, vector index, queries, export to GCS | ⚪ Next |
| 4 | `Feature_networking` | Entry-node ID-token proxy, Cloud Tasks reminders, Cloud Run deploy wiring | ⚪ |
| 5 | `Feature_final` | Consolidation: end-to-end tests on dev GCP, release to `main` | ⚪ |

## Where values live

| Value | Where | Why |
|---|---|---|
| Non-sensitive dev values (sizing, CIDRs, tags, model IDs, budget) | GitHub → Settings → Environments → **dev** → secret `TFVARS` | Not in code (owner's rule) |
| GCP project ID | `dev` environment secret `GCP_PROJECT_ID` | Identifying |
| Billing account ID | `dev` environment secret `GCP_BILLING_ACCOUNT_ID` | Identifying |
| Budget alert e-mail | `dev` environment secret `ALERT_EMAIL` | Personal data |
| GCP access for CI | `dev` environment secrets `GCP_WIF_PROVIDER` + `GCP_PLAN_SERVICE_ACCOUNT` (Workload Identity Federation from `infra/bootstrap`) | No JSON keys |
| Your local copy for plans on the Mac | `infra/envs/dev/terraform.tfvars` (git-ignored) | GitHub secrets are write-only and can't be read back |

The format of every key is in [`infra/envs/dev/terraform.tfvars.example`](../infra/envs/dev/terraform.tfvars.example). No `*.tfvars` file other than `*.tfvars.example` may ever be committed; CI rejects it.

## Day-to-day flow

1. **Start work:** `git switch dev && git pull && git switch -c feature_<area>`.
2. **Push and open a PR into `dev`.** CI runs:
   - `branch-guard`: blocks committed tfvars, blocks feature branches that target anything other than `dev`, and blocks PRs into the idle `prod` and `release`. It also runs `terraform fmt` and `validate`.
   - `terraform-plan`: plans dev from the environment secrets and posts the masked plan as a PR comment. It skips with a notice until `GCP_WIF_PROVIDER` is set.
3. **Review the plan, merge, then apply to dev** from your Mac after the security checkpoint.
4. **Keep feature branches current:** `git switch feature_<area> && git merge dev`.
5. **Later (not yet):** `dev` → `release` → `main`, and `prod` deploys from `main` after D1.

## Recommended GitHub settings (owner, one-time)

- Branch protection on `main` and `dev`: require a PR and the `branch-guard` checks, block force-pushes and deletion.
- Environment `dev`: limit deployment branches to `dev` and `feature_*`.
