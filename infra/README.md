# Weekend 2.0 — Infrastructure (Terraform, Google Cloud)

All cloud resources for Weekend 2.0 run on **Google Cloud**, primary region **asia-south1 (Mumbai)**. Every resource is listed with its monthly cost in [`tracking/Weekend2.0_Tracker.xlsx`](../tracking/Weekend2.0_Tracker.xlsx), on the **Resources** (prod) and **Dev env** sheets.

> **Status: DRAFT, PROVISIONAL.** D4 (cloud) = **GCP** and D2 (model) = **Claude on Vertex AI, global** were decided by the owner on 2026-10-03. D1 (hosting shape) is still open. Nothing has been applied.

## Design in one paragraph
Your phone and Mac reach an **e2-micro entry node** over the Tailscale tailnet. The node has **no external IP**: it reaches the internet through **Cloud NAT**, and you administer it with **IAP SSH + OS Login**. It forwards each request to the **API on Cloud Run**, attaching a Google ID token from its own service account. The API has **internal-only ingress** and **IAM-only invoke**, so nothing on the internet can call it. Cloud Run scales to zero. Data lives in **Firestore (Native)** in Mumbai: serverless, CMEK-encrypted, with a free daily quota and vector search for memory (Phase 1). **Claude runs on Vertex AI.** Current Claude models (Haiku 4.5, Sonnet 5 and newer) are offered only on the **global** or US/EU multi-region endpoints, with **no India region**, so prompts can leave India (🔓 P7). The in-India alternative is Gemini in asia-south1 (a D2 decision). Daily jobs run from **Cloud Scheduler** in IST, and reminders are **Cloud Tasks** (no polling). Everything at rest uses one **Cloud KMS** key (CMEK), all secrets are in **Secret Manager** (single region, CMEK), and backups go to a **GCS** bucket with a retention policy.

## Layout
```
infra/
├── bootstrap/            # per project, run once: APIs, state bucket, KMS (optional), GitHub WIF,
│   └── deployer.tf       #   read-only plan SA + least-privilege DEPLOYER SA (<prefix>-tf, no keys)
├── stack/                # ONE root for every environment — no env values in it
├── values/               # env.tfvars.example / env.bootstrap.tfvars.example / secrets.tfvars.example (templates)
│                         #   + <env>.tfvars and <env>.bootstrap.tfvars ONLY on that env's branch (dev.tfvars on dev)
├── modules/
│   ├── network/          # custom VPC, node subnet (Private Google Access), Cloud Router + NAT, IAP-SSH firewall
│   ├── secrets/          # Secret Manager containers only (single region, CMEK) — values set outside Terraform
│   ├── database/         # Firestore (default) Native, CMEK, delete protection, daily backups
│   ├── registry/         # Artifact Registry Docker repo for the app image; cleanup keeps the newest N
│   ├── app/              # Cloud Run api + worker (internal ingress), service accounts, IAM, Cloud Tasks queue
│   ├── scheduler/        # Cloud Scheduler daily retention/export jobs (Asia/Kolkata) → worker with OIDC
│   ├── entry_node/       # e2-micro, no external IP, Shielded VM, OS Login, Tailscale startup script
│   ├── backup/           # GCS bucket: CMEK, retention policy, lifecycle, public access prevention
│   └── budget/           # Cloud Billing budget (INR, before tax) + e-mail notification channel
├── scripts/
│   ├── tf.py             # LOCAL runs with a 1-hour impersonated token (no key files, no ADC)
│   ├── plan_env.py       # CI: terraform plan → masked Markdown report (+ JSON for the cost guard)
│   ├── cost_guard.py     # CI/CD: blocks any apply that is not free of cost
│   ├── promote.py        # move code between branches; each branch keeps its own infra/values
│   └── gcp_check.py      # read-only pre-flight: login, deployer impersonation, billing, APIs
├── .tflint.hcl           # tflint: terraform recommended preset + google ruleset 0.40.0
└── tailscale/            # policy.hujson (template) / policy.local.hujson (your copy, git-ignored)
```

## Branches and environment values (docs/BRANCHING.md v1.4)
The **code is identical on every branch**. A new environment needs only values, not code changes:

| What | Where | Committed? |
|---|---|---|
| Non-identifying env values (region, sizing, switches, models) | `infra/values/<env>.tfvars`, `<env>.bootstrap.tfvars` | **Only on that env's branch** (`dev.tfvars` on `dev`); `main`/`release` hold templates only — `branch-guard` enforces it |
| Identifying values (project ID, billing account, alert e-mail, owner principal) | CI: GitHub environment secrets → `secrets.auto.tfvars` on the runner · Mac: `../credentials/<project>-<env>.secrets.tfvars` | Never |
| Credentials | CI: Workload Identity Federation · Mac: `tf.py` 1-hour impersonated token | Never (no keys exist) |

New environment `<env>`: create its GCP project → branch `<env>` from `main` → add `infra/values/<env>.tfvars` + `<env>.bootstrap.tfvars` from the templates → copy the two `credentials/` files with the new prefix → bootstrap → stack.

## Versions (checked 2026-10-03)
| Tool | Version | Source |
|---|---|---|
| Terraform | `>= 1.16.0, < 2.0.0` — **1.16.5** on the Mac and in CI | releases.hashicorp.com (SHA256 verified) |
| Google / Google-beta provider | `~> 8.5` (8.5.0) | registry.terraform.io |
| TFLint / google ruleset | v0.64.0 / 0.40.0 | github.com/terraform-linters |
| ruff | 0.16.10 | Homebrew / PyPI |
| gh CLI | 2.102.0 | Homebrew |

`.terraform.lock.hcl` files are committed with hashes for `darwin_arm64` and `linux_amd64`.

## Credentials: short-lived only
- **No service-account keys anywhere** (Weekend v1 lesson). Long-lived Application Default Credentials are not needed; remove them with `gcloud auth application-default revoke`.
- **Your Mac:** `gcloud auth login` (your user) → `infra/scripts/tf.py` asks gcloud for a **1-hour** token of the deployer `weekend2-<env>-tf` (`--impersonate-service-account`) and gives it to Terraform in `GOOGLE_OAUTH_ACCESS_TOKEN`, in memory only. Config: `../credentials/<project>-<env>.terraform.json` (no secret inside; see `credentials/README.md`).
- **CI/CD:** GitHub OIDC → Workload Identity Federation. PR plans use the read-only plan SA (environment `<env>`). CD uses the deployer, accepted only from environment `<env>-apply` on `refs/heads/<env>`.
- **Deployer permissions:** one admin role per service in the stack, no Owner/Editor; project-IAM admin only through an IAM Condition that allows granting just `datastore.user`, `aiplatform.user`, `logging.logWriter`, `monitoring.metricWriter`; budgets via `billing.costsManager`. See `bootstrap/deployer.tf`.

## Before the first plan (one-time, manual)
1. **Sign in on your Mac** (never paste keys into chat or files): `gcloud auth login`, then `gcloud auth application-default revoke` (remove the long-lived ADC file).
2. **Fill `../credentials/weekend2-0-dev.secrets.tfvars`** (billing account ID, alert e-mail, `owner_principal = "user:<your gcloud login>"`).
3. **Claude on Vertex AI** (D2 decided 2026-10-03): Console → Vertex AI → Model Garden → Claude Haiku 4.5 → **Enable** and accept Anthropic's terms. Enabling is free; you pay only per token used. Model IDs: `claude-haiku-4-5@20251001`, `claude-sonnet-5`.
4. **Tailnet policy:** copy `tailscale/policy.hujson` → `tailscale/policy.local.hujson` (git-ignored), set `<OWNER_LOGIN>`, paste it into the Tailscale admin console.
5. **Pre-flight (read-only):** `python3 infra/scripts/gcp_check.py --project weekend2-0 --deployer weekend2-dev-tf@weekend2-0.iam.gserviceaccount.com` → `READY`.

## Dev run order (from the repo root, on the `dev` branch)
```bash
# 1. Bootstrap (⚠️ security checkpoint: IAM, WIF, deployer). Your own 1-hour token; local state.
python3 infra/scripts/tf.py --env dev --root bootstrap --as-owner plan     # review
python3 infra/scripts/tf.py --env dev --root bootstrap --as-owner apply
python3 infra/scripts/tf.py --env dev --root bootstrap --as-owner output   # → GitHub secrets (CI/CD setup)

# 2. Stack, as the deployer (1-hour token). Secret containers first, then their values (never via Terraform).
python3 infra/scripts/tf.py --env dev plan && python3 infra/scripts/tf.py --env dev apply
gcloud secrets versions add weekend2-dev-tailscale-authkey --data-file=authkey.txt && rm -P authkey.txt   # one-off key, tag:weekend-dev
openssl rand -base64 48 | tr -d '\n' | gcloud secrets versions add weekend2-dev-app-session-signing-key --data-file=-
```
`tf.py apply` applies only the saved, reviewed plan, then deletes it (saved plans hold values in cleartext).
The entry node's startup script waits up to about 20 minutes for the auth key; if it arrives later, run `gcloud compute instances reset weekend2-dev-entry --zone us-central1-a`.

## CI/CD (GitHub Actions)
| Workflow | Trigger | Does | Credentials |
|---|---|---|---|
| `branch-guard` | every PR | branch rules; env values only on their own branch; no identifying files | none |
| `infra-ci` | PR touching `infra/` | `terraform fmt` → `tflint` → `validate` → `ruff` → **plan** → cost guard → **masked plan as a PR comment** | plan SA (read-only), env `dev` |
| `infra-cd` | push to `dev` from a **merged PR** | plan → **cost guard (free only)** → `terraform apply -auto-approve` | deployer, env `dev-apply` (**you approve**) |
| `app-ci` | PR touching `app/` | `mvn verify` (tests) → Docker image build | none |
| `app-cd` | push to `dev` from a merged PR | test → image → Artifact Registry → new Cloud Run revisions | deployer, env `dev-apply` (**you approve**) |

**Approval:** GitHub doesn't let a PR author approve their own PR, so the approval step is the `dev-apply` environment: you are its required reviewer and click **Approve and deploy**. **CD is off** until you set the repository variables `INFRA_CD_ENABLED=true` / `APP_CD_ENABLED=true` ("don't deploy yet").

One-time GitHub setup (after bootstrap; values from `tf.py … output`):
```bash
gh auth login
for e in dev dev-apply; do gh api -X PUT "repos/SharoffNIKHIL/Weekend2.0/environments/$e" > /dev/null; done
gh api -X PUT repos/SharoffNIKHIL/Weekend2.0/environments/dev-apply --input - <<JSON
{"reviewers":[{"type":"User","id":$(gh api user --jq .id)}],"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}
JSON
gh api -X POST repos/SharoffNIKHIL/Weekend2.0/environments/dev-apply/deployment-branch-policies -f name=dev
for e in dev dev-apply; do
  gh secret set GCP_PROJECT_ID --env $e;  gh secret set GCP_BILLING_ACCOUNT_ID --env $e
  gh secret set ALERT_EMAIL --env $e;     gh secret set GCP_WIF_PROVIDER --env $e
done
gh secret set GCP_PLAN_SERVICE_ACCOUNT --env dev
gh secret set GCP_DEPLOY_SERVICE_ACCOUNT --env dev-apply
gh secret delete TFVARS --env dev      # replaced by infra/values/dev.tfvars on the dev branch
# when you are ready to deploy:  gh variable set INFRA_CD_ENABLED --body true ; gh variable set APP_CD_ENABLED --body true
```

### Dev smoke tests
```bash
python3 infra/scripts/tf.py --env dev output
gcloud compute ssh weekend2-dev-entry --zone us-central1-a --tunnel-through-iap
#   on the node:
sudo tailscale status                                                   # node-d1, tag:weekend-dev
curl -s -H "Authorization: Bearer $(curl -s -H 'Metadata-Flavor: Google' \
  'http://metadata/computeMetadata/v1/instance/service-accounts/default/identity?audience=<API_URL>')" <API_URL>   # expect 200 (hello page)
# from your Mac (internet): expect 403/404 — the API has internal-only ingress
curl -s -o /dev/null -w '%{http_code}\n' <API_URL>
```

### Save money / tear down
```bash
gcloud compute instances stop  weekend2-dev-entry --zone us-central1-a
gcloud compute instances start weekend2-dev-entry --zone us-central1-a
python3 infra/scripts/tf.py --env dev destroy-plan && python3 infra/scripts/tf.py --env dev apply   # ⚠️ destructive: checkpoint
```
The KMS key ring and key belong to bootstrap and are never destroyed, because GCP key rings can't be deleted. A Cloud Tasks queue name can't be reused for about 7 days after deletion; if you re-create dev sooner, the apply fails on the queue. Firestore's `(default)` database may also take a few minutes before it can be re-created.

## Free by default: what's on hold (dev)
Dev deploys **only free-tier resources** unless you switch a costed item on. Every apply still needs an **open billing account** linked to the project, because Google's free tiers require one (linked and enabled, checked 2026-10-03). The CD pipeline auto-applies only plans that pass `cost_guard.py`.

| Item | Switch (dev default) | Cost when on | Status |
|---|---|---|---|
| Cloud Run api + worker, Firestore `(default)`, Secret Manager (2 secrets), Cloud Scheduler (2 jobs), Cloud Tasks, Logging, budget, WIF + plan service account, GCS state + backup buckets in US-CENTRAL1 | always on | $0 within free tiers | ✅ Free |
| Cloud KMS key (CMEK, P3) | `enable_cmek = false` (bootstrap + dev) | ≈ $0.10/month | ⏸ On hold (Google-managed encryption meanwhile; prod must enable it) |
| Entry node e2-micro (us-central1, always free) + 10 GB pd-standard (free) | `entry_node_enabled = false` | VM free, but egress isn't | ⏸ On hold |
| Node egress | `entry_node_egress = "nat"` | Cloud NAT ≈ $1.02/month, or `"external_ip"` (IPv4 billing `Not verified`) | ⏸ On hold (with the node) |
| Artifact Registry (app images) | always on, keeps 2 versions | 0.5 GB/month free (`Not verified` for asia-south1) | ✅ Free if images stay small |
| Claude on Vertex AI | no resource; usage only | ≈ $1/month light dev use | ⏸ Enable in Model Garden (owner, free to enable) |

## Dev vs prod
| Setting | Dev | Prod | Why |
|---|---|---|---|
| Project | `weekend2-0` ("WeekEnd2-0", created 2026-10-03; Google rejects "." in names) | `weekend2-prod-<suffix>` (later) | One project per env; separate IAM, billing filter, blast radius |
| Entry node | ⏸ off by default; when on: e2-micro **us-central1-a** (always free), pd-standard 10 GB, Google-managed disk key, NAT egress | e2-micro asia-south1-a, pd-balanced 10 GB, CMEK | Dev holds **no real personal data**, so the free US VM is acceptable; prod data stays in India |
| Firestore | No delete protection, no backups | Delete protection, 7-day daily backups | Destroyable dev |
| Backup bucket | US-CENTRAL1 (5 GB free), 1-day retention, no soft delete, `force_destroy`, CMEK ⏸ | 35-day retention, 7-day soft delete | Destroy can empty it |
| Cloud Run | Max 1 instance, DEBUG, no deletion protection | Max 2, INFO, protected | Cost and safety |
| Claude | Haiku 4.5 for both slots | Haiku 4.5 + Sonnet 5 | Cost |
| Budget | ₹850 before tax (≈ ₹1,003 incl. GST) | ₹4,237 before tax (≈ ₹5,000 incl. GST) | GCP budgets exclude tax |

## Security notes
- **No public endpoints.** The API and worker have internal-only ingress plus IAM invoke. The only VM has no external IP and no inbound rules except SSH from Google's IAP range (`35.235.240.0/20`), protected by OS Login.
- **The tailnet policy is the real front door.** The node calls the API with its own identity, so who reaches the node decides who reaches the API. Keep `tailscale/policy.hujson` applied, and keep the passkey session check in the app (Phase 2) as a second factor.
- **Keyless CI/CD.** GitHub Actions authenticates through Workload Identity Federation. The provider only accepts tokens from `SharoffNIKHIL/Weekend2.0` jobs in environment `<env>` or `<env>-apply`. The plan service account has Viewer + Security Reviewer (no secret payloads, no object data) and read-only state access; CI plans run with `-lock=false`. The deployer is reachable only from `<env>-apply` on `refs/heads/<env>`.
- **CMEK everywhere data rests (P3):** Firestore, Secret Manager, the backup bucket and the prod VM disk. Cloud Run is stateless. Logs carry no message content and use Google-managed encryption.
- **🔓 P7 data exit — Vertex AI Claude (global endpoint), accepted by the owner (D2, 2026-10-03):** prompts and replies may be processed outside India. A copy of every exchange is kept in our Firestore in asia-south1 (owner requirement; Feature_database). Google's terms apply (no training on customer data; check the Vertex AI zero-data-retention settings).
- **Backups and retention:** the bucket retention policy blocks deletion and overwrite for 35 days (prod). A P6 "delete all my data" request that must purge retained objects is an owner-only admin action (security checkpoint): remove the unlocked policy (`gcloud storage buckets update gs://<BUCKET> --clear-retention-period`), delete the objects, then re-apply Terraform. **Never lock the policy**; a locked policy can't be removed.
- **Keep the tailnet hostname generic** (`node-a1`, `node-d1`). Tailscale HTTPS certificates publish it in public Certificate Transparency logs.

## Cost (expected use, asia-south1; FX ₹96.12 on 2026-10-02)
| | Pre-tax USD | Incl. 18% GST | ₹ / month |
|---|---|---|---|
| **Prod** (entry node 24/7, Claude ≈ 3,000 turns) | ≈ 24.5 | ≈ 29.0 | **≈ 2,784** |
| **Dev, free by default** (all ⏸ items off; light Claude use) | ≈ 1.0 | ≈ 1.2 | **≈ 113** (₹0 infra) |
| Dev with CMEK + entry node + NAT switched on | ≈ 2.1 | ≈ 2.5 | ≈ 243 |

Prod plus free dev ≈ ₹2,900, inside the ₹5,000 budget. The biggest lines are Claude (≈ $14.5) and the prod e2-micro (≈ $7.4). Cloud Run, Firestore, Secret Manager, Scheduler, Tasks and Logging stay inside their free tiers at single-user volume. An account billed by Google Cloud India adds 18% GST; confirm under Billing → Account management. Line-by-line rates and sources are in the tracker.

## Not yet included (later phases)
Firestore vector index (Phase 1) · per-object retention for monthly/audit copies (Phase 1) · Caddy + ID-token proxy on the entry node (Phase 2) · Speech-to-Text / Text-to-Speech permissions (Phase 2 voice decision) · VPC flow logs (cost decision) · Prod environment branch values (after D1).
