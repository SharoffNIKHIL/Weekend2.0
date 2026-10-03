# Branching strategy — Weekend 2.0

**Version 1.4 · 2026-10-03 · Owner: Nikhil** (1.4: one generic Terraform root, env values on env branches (option A), promote.py, CI/CD; 1.3: feature sequence; 1.2: GCP; 1.1: values in secrets — SUPERSEDED for non-identifying values by 1.4)

**The code is the same on every branch.** Branches differ only in their **environment values**:

| Branch | Holds | Values committed | Purpose |
|---|---|---|---|
| `main` | Raw code: app, `infra/stack` (one root for all envs), modules, scripts, docs, tracker | **None** — templates only (`infra/values/*.example`) | Base for every environment; clone it to start a new one |
| `release` | Same as `main` | None | Stabilise a release (`main` → `release`) — idle |
| `dev` | Same code **+ dev values** | `infra/values/dev.tfvars`, `dev.bootstrap.tfvars` | The dev environment (GCP project `weekend2-0`, asia-south1); CD deploys from here |
| `prod` | Same code + prod values (after D1) | `infra/values/prod*.tfvars` (later) | The production environment — idle |
| `Feature_*` / `feature_*` | Work in progress, cut from `dev` | dev values (inherited) | One feature at a time → PR into `dev` |

**Never committed anywhere:** project ID, billing account ID, e-mail addresses, owner principal, keys, tokens. They live in GitHub environment secrets (CI) and in `../credentials/<project>-<env>.secrets.tfvars` on the Mac. `branch-guard` fails any PR that breaks these rules.

```
main  ──●────────────●──────────────►   raw code, no values
        │  ▲ promote.py (code only)
        │  │            ├── release  (idle)  ← main
        │  │            └── prod     (idle)  ← main, + prod values
        └─ dev ──●──────●──────●────►   code + dev values ── infra-cd / app-cd (after your approval)
                  \    ↗ \    ↗
             Feature_code  feature_cicd …     (PR → dev)
```

## Feature sequence (owner's plan, 2026-10-03)
| # | Branch | Scope | Status |
|---|---|---|---|
| 1 | `feature_infra` | Terraform for GCP, CI, plan tooling | ✅ Merged (PR #2), branch removed |
| 2 | `Feature_code` | Java application core, PWA, in-memory adapters | ✅ Merged (PR #3, 2026-10-03); branch kept |
| 2b | `feature_cicd` | Generic `infra/stack`, env values files, deployer SA, CI/CD pipelines, short-lived credentials | ✅ Merged into `dev` 2026-10-03 |
| 3 | `Feature_database` | Firestore adapters, vector index, **copy of every LLM exchange (D2)**, export to GCS | ⚪ Next |
| 4 | `Feature_networking` | Entry-node ID-token proxy, Cloud Tasks reminders | ⚪ |
| 5 | `Feature_final` | End-to-end tests on dev GCP, release to `main` | ⚪ |

## Moving code between branches
Plain `git merge` would carry dev values into `main`, or delete them from `dev` on a back-merge. Use:
```bash
python3 infra/scripts/promote.py --source dev  --target main     # code only; main keeps no values
python3 infra/scripts/promote.py --source main --target release
python3 infra/scripts/promote.py --source main --target dev      # back-sync; dev keeps dev.tfvars
git push -u origin <printed promote/... branch>   # then open a PR into the target
```
`release` and `prod` accept PRs only from `main` or `promote/*`.

## New environment (e.g. `staging`)
1. Create the GCP project and link billing (⚠️ checkpoint).
2. `git switch main && git switch -c staging`, copy `infra/values/env.tfvars.example` → `staging.tfvars` and `env.bootstrap.tfvars.example` → `staging.bootstrap.tfvars`, fill them in.
3. Copy `../credentials/<project>-dev.*` to `<project>-staging.*` and edit.
4. Bootstrap, then the stack (`infra/README.md`). Add GitHub environments `staging` / `staging-apply` if CI/CD should cover it. **No code changes.**

## CI/CD
| PR into `dev` | merge into `dev` |
|---|---|
| `branch-guard`, `infra-ci` (fmt → tflint → validate → ruff → plan → cost guard → plan comment), `app-ci` (tests → image build) | `infra-cd` / `app-cd`: only from a merged PR, only after **you** approve the `dev-apply` deployment, infra only if `cost_guard.py` says free of cost, and only when `INFRA_CD_ENABLED` / `APP_CD_ENABLED` = `true` (off now) |

## Day-to-day flow
1. `git switch dev && git pull && git switch -c feature_<area>`.
2. Push, open a PR into `dev`, read the plan comment.
3. Merge → approve the `dev-apply` deployment in GitHub (when CD is enabled).
4. Keep feature branches current: `git merge dev`.

## Recommended GitHub settings (owner, one-time)
- Branch protection on `main`, `dev`, `release`, `prod`: require a PR and the `branch-guard`, `infra-ci`, `app-ci` checks; block force-pushes and deletion.
- Environments: `dev` (plans; branches `dev`, `feature_*`, `Feature_*`) and `dev-apply` (required reviewer: you; branch `dev` only). Commands: `infra/README.md` → CI/CD.
