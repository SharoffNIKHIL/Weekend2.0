# Branching strategy — Weekend 2.0

**Version 1.1 · 2026-10-03 · Owner: Nikhil** (1.0 kept env values in code; superseded)

The code is the same for every environment. **Environment values are never in the code.** They live in GitHub **environment secrets** and are written to a temporary `terraform.tfvars` only while a workflow runs.

## Branches

| Branch | Created from | Purpose | Merges into | Status |
|---|---|---|---|---|
| `main` | — | Base code: modules, env roots, scripts, docs, tracker | — (default branch) | Active |
| `dev` | `main` | Integration branch for the dev environment (`weekend2-dev-*`, ap-south-1) | Nothing yet; code is promoted to `main` later through `release` | Active |
| `feature_<area>` | `dev` | One per piece of work. Many can exist at once, e.g. `feature_infra`, `feature_app`, `feature_mobile`, `feature_memory` | `dev` (PR) | `feature_infra` active |
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

## Where values live

| Value | Where | Why |
|---|---|---|
| Non-sensitive dev values (sizing, CIDRs, tags, model IDs, budget) | GitHub → Settings → Environments → **dev** → secret `TFVARS` | Not in code (owner's rule) |
| AWS account ID | `dev` environment secret `AWS_ACCOUNT_ID` | Identifying |
| Budget alert e-mail | `dev` environment secret `ALERT_EMAIL` | Personal data |
| Aurora version | `dev` environment secret `AURORA_ENGINE_VERSION` | Set after T-018 |
| AWS access for CI | `dev` environment secret `AWS_PLAN_ROLE_ARN` (OIDC role from `infra/bootstrap/github_oidc.tf`) | No long-lived keys |
| Your local copy for plans on the Mac | `infra/envs/dev/terraform.tfvars` (git-ignored) | GitHub secrets are write-only and can't be read back |

The format of every key is in [`infra/envs/dev/terraform.tfvars.example`](../infra/envs/dev/terraform.tfvars.example). No `*.tfvars` file other than `*.tfvars.example` may ever be committed; CI rejects it.

## Day-to-day flow

1. **Start work:** `git switch dev && git pull && git switch -c feature_<area>`.
2. **Push and open a PR into `dev`.** CI runs:
   - `branch-guard`: blocks committed tfvars, blocks feature branches that target anything other than `dev`, and blocks PRs into the idle `prod` and `release`. It also runs `terraform fmt` and `validate`.
   - `terraform-plan`: plans dev from the environment secrets and posts the masked plan as a PR comment. It skips with a notice until `AWS_PLAN_ROLE_ARN` is set.
3. **Review the plan, merge, then apply to dev** from your Mac after the security checkpoint.
4. **Keep feature branches current:** `git switch feature_<area> && git merge dev`.
5. **Later (not yet):** `dev` → `release` → `main`, and `prod` deploys from `main` after D1.

## Recommended GitHub settings (owner, one-time)

- Branch protection on `main` and `dev`: require a PR and the `branch-guard` checks, block force-pushes and deletion.
- Environment `dev`: limit deployment branches to `dev` and `feature_*`.
