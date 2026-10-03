# Branching strategy — Weekend 2.0

**Version 1.0 · 2026-10-03 · Owner: Nikhil**

This repo keeps **code** and **environment values** apart. `main` holds the base code that every environment shares. Each environment has its own long-lived branch that adds only that environment's values.

## Branches

| Branch | Holds | Created from | Merges into | Deploys to |
|---|---|---|---|---|
| `main` | Base code: Terraform modules, env roots (`infra/envs/<env>/*.tf`), scripts, docs, tracker. **No env values.** | — | — (default branch) | Nothing directly |
| `dev` | `main` + `infra/envs/dev/values.auto.tfvars` (dev values, free-tier sizing) | `main` | Never back into `main` | AWS dev (`weekend2-dev-*`, ap-south-1) |
| `prod` (later, after D1) | `main` + `infra/envs/prod/values.auto.tfvars` | `main` | Never back into `main` | AWS prod (`weekend2-prod-*`) |
| `feature_<topic>` (e.g. `feature_infra`) | Work in progress | `dev` | `dev` (PR) | Dev, for testing |
| `promote/<topic>` | Tested code only, no values | `main` | `main` (PR) | — |
| `hotfix/<topic>` | Urgent base-code fix | `main` | `main`, then sync down | — |

```
main ──────●───────────────●───────────────●────►  base code only
            \  (sync down)  \ (sync down)   ↑
dev ─────────●──values──●────●─────●────────┼───►  + dev values
                         \        ↑         │
feature_infra ────────────●──●──●─┘ (PR)    │
                                            │
promote/infra ─────── cherry-pick code ─────┘ (PR)
```

## Where values live

| File | Committed? | Contents |
|---|---|---|
| `infra/envs/<env>/variables.tf` | Yes, on `main` | Variable definitions (types, validation, neutral defaults) |
| `infra/envs/<env>/values.auto.tfvars` | Yes, **only on that env's branch** | Non-sensitive env values: sizing, names, tags, CIDRs, model IDs, budget |
| `infra/envs/<env>/terraform.tfvars` | **Never** (git-ignored) | Sensitive values: `aws_account_id`, `alert_email` |
| `infra/envs/<env>/backend.hcl` | **Never** (git-ignored) | State bucket name (contains the account ID) |

Terraform loads `terraform.tfvars` and every `*.auto.tfvars` automatically, so `terraform plan` needs no `-var-file` flags. The repo is **public**, so a values file must never hold secrets, account IDs, e-mail addresses or real IPs.

## Day-to-day flow

1. **Start work:** `git switch dev && git pull && git switch -c feature_<topic>`.
2. **Commit code and values separately.** Put a change to `values.auto.tfvars` in its own commit. Code commits can then be promoted to `main` without carrying values.
3. **Test in dev:** open a PR `feature_<topic>` → `dev`, attach the masked plan (`python3 infra/scripts/plan_env.py --env dev`), merge, then apply to dev after the security checkpoint.
4. **Promote code to `main`:** `git switch main && git pull && git switch -c promote/<topic> && git cherry-pick <code-commit-SHAs>`, then open a PR to `main`.
5. **Sync down:** after anything merges into `main`, run `git switch dev && git merge main` (and the same for `prod`), so env branches always run the latest base code.

## Guard rails (CI: `.github/workflows/branch-guard.yml`)

- A PR into `main` fails if it adds or changes any `infra/envs/*/values.auto.tfvars`.
- A PR into `dev` fails if it touches another environment's values file.
- Every PR runs `terraform fmt -check` and `validate` on the roots it touches (no AWS credentials needed).

**Recommended GitHub settings** (owner, one-time): protect `main` and `dev` by requiring a PR and passing checks, and blocking force-pushes and deletion.

## Why not one branch with folders only?

Folders (`envs/dev`, `envs/prod`) already separate the Terraform roots. The env branches add one thing: a change to an environment's values is reviewed and merged on that environment's branch, and `main` stays deployable to no environment by itself.
