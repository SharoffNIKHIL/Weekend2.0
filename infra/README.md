# Weekend 2.0 — Infrastructure (Terraform, Google Cloud)

All cloud resources for Weekend 2.0 run on **Google Cloud**, primary region **asia-south1 (Mumbai)**. Every resource is listed with its monthly cost in [`tracking/Weekend2.0_Tracker.xlsx`](../tracking/Weekend2.0_Tracker.xlsx), on the **Resources** (prod) and **Dev env** sheets.

> **Status: DRAFT, PROVISIONAL.** D4 (cloud) = **GCP**, decided by the owner on 2026-10-03 (AWS dropped). D1 (hosting shape) and D2 (model) are still open. Nothing has been applied.

## Design in one paragraph
Your phone and Mac reach an **e2-micro entry node** over the Tailscale tailnet. The node has **no external IP**: it reaches the internet through **Cloud NAT**, and you administer it with **IAP SSH + OS Login**. It forwards each request to the **API on Cloud Run**, attaching a Google ID token from its own service account. The API has **internal-only ingress** and **IAM-only invoke**, so nothing on the internet can call it. Cloud Run scales to zero. Data lives in **Firestore (Native)** in Mumbai: serverless, CMEK-encrypted, with a free daily quota and vector search for memory (Phase 1). **Claude runs on Vertex AI.** Current Claude models (Haiku 4.5, Sonnet 5 and newer) are offered only on the **global** or US/EU multi-region endpoints, with **no India region**, so prompts can leave India (🔓 P7). The in-India alternative is Gemini in asia-south1 (a D2 decision). Daily jobs run from **Cloud Scheduler** in IST, and reminders are **Cloud Tasks** (no polling). Everything at rest uses one **Cloud KMS** key (CMEK), all secrets are in **Secret Manager** (single region, CMEK), and backups go to a **GCS** bucket with a retention policy.

## Layout
```
infra/
├── bootstrap/            # per project, run once: APIs, state bucket, KMS key ring + key, service-agent
│                         #   key access, GitHub Workload Identity Federation + read-only plan service account
├── envs/dev/             # dev root; values come from GitHub `dev` environment secrets
├── envs/prod/            # prod root (created, idle)
├── scripts/plan_env.py   # terraform plan → masked Markdown report for PRs (nothing applied)
├── modules/
│   ├── network/          # custom VPC, one node subnet (Private Google Access), Cloud Router + NAT, IAP-SSH firewall
│   ├── secrets/          # Secret Manager containers only (single region, CMEK) — values set outside Terraform
│   ├── database/         # Firestore (default) Native, CMEK, delete protection, daily backups
│   ├── app/              # Cloud Run api + worker (internal ingress), service accounts, IAM, Cloud Tasks queue
│   ├── scheduler/        # Cloud Scheduler daily retention/export jobs (Asia/Kolkata) → worker with OIDC
│   ├── entry_node/       # e2-micro, no external IP, Shielded VM, OS Login, Tailscale startup script
│   ├── backup/           # GCS bucket: CMEK, retention policy, lifecycle, public access prevention
│   └── budget/           # Cloud Billing budget (INR, before tax) + e-mail notification channel
└── tailscale/
    ├── policy.hujson         # template (public): only the owner reaches tag:weekend / tag:weekend-dev on 443
    └── policy.local.hujson   # your copy with the real login (git-ignored)
```

## Branches and environment values
`main` holds the base code. `dev`, `prod` and `release` were created from `main`; `prod` and `release` stay idle. Work happens on `feature_<area>` branches cut from `dev` (e.g. `feature_infra`), and each one merges back into `dev` by PR. **Env values are never in the code.** They are stored in the GitHub `dev` environment secrets: `TFVARS`, `GCP_PROJECT_ID`, `GCP_BILLING_ACCOUNT_ID`, `ALERT_EMAIL`, `GCP_WIF_PROVIDER`, `GCP_PLAN_SERVICE_ACCOUNT`. The `terraform-plan` workflow writes them to a temporary `terraform.tfvars`, authenticates keylessly through Workload Identity Federation, and posts the masked plan on the PR. Full flow: [docs/BRANCHING.md](../docs/BRANCHING.md).

## Versions (checked 2026-10-03)
| Tool | Version | Source |
|---|---|---|
| Terraform | `>= 1.16.0, < 2.0.0` (validated with 1.16.5) | checkpoint-api.hashicorp.com |
| Google provider | `~> 8.5` (8.5.0) | registry.terraform.io |
| Google-beta provider (bootstrap only) | `~> 8.5` (8.5.0) | registry.terraform.io |
| Google Cloud SDK on the owner's Mac | 548.0.0 | `gcloud --version` |

`.terraform.lock.hcl` files are committed, with hashes for `darwin_arm64` and `linux_amd64`. Homebrew's `terraform` is stuck at 1.5.7, so install the current one with `brew install hashicorp/tap/terraform`.

## Before the first plan (one-time, manual)
1. **Sign in on your Mac. Never paste keys into chat or files.**
   ```bash
   gcloud auth login                         # opens the browser
   gcloud auth application-default login     # credentials Terraform uses
   ```
2. **Create the dev project and link billing** (⚠️ security checkpoint: new project + billing):
   ```bash
   gcloud billing accounts list                                   # note the ID (keep it private)
   gcloud projects create weekend2-dev-<suffix> --name="Weekend2 dev"
   gcloud billing projects link weekend2-dev-<suffix> --billing-account=<BILLING_ACCOUNT_ID>
   gcloud config set project weekend2-dev-<suffix>
   gcloud auth application-default set-quota-project weekend2-dev-<suffix>
   ```
3. **Enable Claude on Vertex AI** (⚠️ third-party terms and cost): Console → Vertex AI → Model Garden → search "Claude" → enable Claude Haiku 4.5 (and Sonnet 5 for prod) and accept Anthropic's terms. Model IDs used: `claude-haiku-4-5@20251001`, `claude-sonnet-5` (from Anthropic's Vertex docs, 2026-10-03).
4. **Apply the tailnet policy**: copy [`tailscale/policy.hujson`](tailscale/policy.hujson) to `tailscale/policy.local.hujson` (git-ignored), replace `<OWNER_LOGIN>`, and paste it into the Tailscale admin console.
5. **Local values:** copy `terraform.tfvars.example` → `terraform.tfvars` and `backend.hcl.example` → `backend.hcl` in `bootstrap/` and `envs/dev/`. Both copies are git-ignored. For CI, put the same values in the GitHub `dev` environment secrets.

## Dev run order
```bash
mkdir -p ~/.tfplans && chmod 700 ~/.tfplans

# 1. Bootstrap the dev project (APIs, state bucket, KMS, WIF). IAM change → security checkpoint.
cd infra/bootstrap && terraform init && terraform plan -out="$HOME/.tfplans/weekend2-bootstrap-dev.tfplan"
terraform apply "$HOME/.tfplans/weekend2-bootstrap-dev.tfplan"
terraform output   # state_bucket → backend.hcl; wif_provider + plan_service_account → GitHub secrets

# 2. Secret containers first, then their values (never via Terraform)
cd ../envs/dev && terraform init -backend-config=backend.hcl
terraform apply -target=module.secrets
gcloud secrets versions add weekend2-dev-tailscale-authkey --data-file=authkey.txt && rm -P authkey.txt   # one-off key, tag:weekend-dev
openssl rand -base64 48 | tr -d '\n' | gcloud secrets versions add weekend2-dev-app-session-signing-key --data-file=-

# 3. Everything else: plan (writes ~/.tfplans/weekend2-dev.tfplan + a masked report for the PR), review, apply
cd ../../.. && python3 infra/scripts/plan_env.py --env dev
cd infra/envs/dev && terraform apply "$HOME/.tfplans/weekend2-dev.tfplan"
```
The entry node's startup script runs on every boot and waits up to about 20 minutes for the auth key. If the key arrives later, run `gcloud compute instances reset weekend2-dev-entry --zone us-central1-a`.

### Dev smoke tests
```bash
terraform output
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
terraform plan -destroy -out="$HOME/.tfplans/weekend2-dev-destroy.tfplan" && terraform apply "$HOME/.tfplans/weekend2-dev-destroy.tfplan"
```
The KMS key ring and key belong to bootstrap and are never destroyed, because GCP key rings can't be deleted. A Cloud Tasks queue name can't be reused for about 7 days after deletion; if you re-create dev sooner, the apply fails on the queue. Firestore's `(default)` database may also take a few minutes before it can be re-created.

## Dev vs prod
| Setting | Dev | Prod | Why |
|---|---|---|---|
| Project | `weekend2-dev-<suffix>` | `weekend2-prod-<suffix>` | One project per env; separate IAM, billing filter, blast radius |
| Entry node | e2-micro **us-central1-a** (always free), pd-standard 10 GB, Google-managed disk key, can be switched off | e2-micro asia-south1-a, pd-balanced 10 GB, CMEK | Dev holds **no real personal data**, so the free US VM is acceptable; prod data stays in India |
| Firestore | No delete protection, no backups | Delete protection, 7-day daily backups | Destroyable dev |
| Backup bucket | 1-day retention, no soft delete, `force_destroy` | 35-day retention, 7-day soft delete | Destroy can empty it |
| Cloud Run | Max 1 instance, DEBUG, no deletion protection | Max 2, INFO, protected | Cost and safety |
| Claude | Haiku 4.5 for both slots | Haiku 4.5 + Sonnet 5 | Cost |
| Budget | ₹850 before tax (≈ ₹1,003 incl. GST) | ₹4,237 before tax (≈ ₹5,000 incl. GST) | GCP budgets exclude tax |

## Security notes
- **No public endpoints.** The API and worker have internal-only ingress plus IAM invoke. The only VM has no external IP and no inbound rules except SSH from Google's IAP range (`35.235.240.0/20`), protected by OS Login.
- **The tailnet policy is the real front door.** The node calls the API with its own identity, so who reaches the node decides who reaches the API. Keep `tailscale/policy.hujson` applied, and keep the passkey session check in the app (Phase 2) as a second factor.
- **Keyless CI.** GitHub Actions authenticates through Workload Identity Federation. The provider only accepts tokens from `SharoffNIKHIL/Weekend2.0` jobs running in the matching GitHub environment. The plan service account has Viewer + Security Reviewer (no secret payloads, no object data) and read-only state access; CI plans run with `-lock=false`.
- **CMEK everywhere data rests (P3):** Firestore, Secret Manager, the backup bucket and the prod VM disk. Cloud Run is stateless. Logs carry no message content and use Google-managed encryption.
- **🔓 P7 data exit — Vertex AI Claude (global endpoint):** prompts and replies may be processed outside India. Google's terms apply (no training on customer data; check the Vertex AI zero-data-retention settings). The in-India alternative is Gemini in asia-south1 (D2).
- **Backups and retention:** the bucket retention policy blocks deletion and overwrite for 35 days (prod). A P6 "delete all my data" request that must purge retained objects is an owner-only admin action (security checkpoint): remove the unlocked policy (`gcloud storage buckets update gs://<BUCKET> --clear-retention-period`), delete the objects, then re-apply Terraform. **Never lock the policy**; a locked policy can't be removed.
- **Keep the tailnet hostname generic** (`node-a1`, `node-d1`). Tailscale HTTPS certificates publish it in public Certificate Transparency logs.

## Cost (expected use, asia-south1; FX ₹96.12 on 2026-10-02)
| | Pre-tax USD | Incl. 18% GST | ₹ / month |
|---|---|---|---|
| **Prod** (entry node 24/7, Claude ≈ 3,000 turns) | ≈ 24.5 | ≈ 29.0 | **≈ 2,784** |
| **Dev** (free US e2-micro on, light Claude use) | ≈ 2.1 | ≈ 2.5 | **≈ 243** |
| Dev with the node off | ≈ 1.1 | ≈ 1.3 | ≈ 127 |

Prod plus dev ≈ ₹3,030, inside the ₹5,000 budget. The biggest lines are Claude (≈ $14.5) and the prod e2-micro (≈ $7.4). Cloud Run, Firestore, Secret Manager, Scheduler, Tasks and Logging stay inside their free tiers at single-user volume. An account billed by Google Cloud India adds 18% GST; confirm under Billing → Account management. Line-by-line rates and sources are in the tracker.

## Not yet included (later phases)
Container image build + Artifact Registry with CMEK (Phase 1) · Firestore vector index (Phase 1) · per-object retention for monthly/audit copies (Phase 1) · Caddy + ID-token proxy on the entry node (Phase 2) · Speech-to-Text / Text-to-Speech permissions (Phase 2 voice decision) · VPC flow logs (cost decision) · CI apply pipeline (Phase 5).
