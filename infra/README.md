# Weekend 2.0 — Infrastructure (Terraform)

All AWS resources for Weekend 2.0, in **ap-south-1 (Mumbai)**. Every resource is listed with its monthly cost in [`tracking/Weekend2.0_Tracker.xlsx`](../tracking/Weekend2.0_Tracker.xlsx) → **Resources** sheet.

> **Status: DRAFT, PROVISIONAL — depends on decision D1 (hosting).** This implements option **E2 (revised)**: serverless-first, plus one tiny entry server on the private tailnet. Nothing here has been applied.

## Design in one paragraph
Your phone and Mac reach a **t4g.nano entry node** over the Tailscale tailnet. The node has no inbound ports. It forwards each request to the **API Lambda's Function URL**, signing it with its IAM role; the URL rejects any unsigned call. Lambda (Python 3.14, arm64) runs outside the VPC. It talks to **Aurora PostgreSQL Serverless v2** through the **RDS Data API**; Aurora pauses at 0 ACU when idle. It reaches **Claude** through Bedrock's **India** inference profiles. Daily jobs run from **EventBridge Scheduler** in IST. Reminders are one-time schedules, so nothing polls the database. Everything is encrypted with one customer-managed **KMS** key, and all secrets live in **Secrets Manager**.

## Layout
```
infra/
├── bootstrap/            # remote-state bucket (local state, run once)
├── envs/prod/            # the only environment: wires the modules together
└── modules/
    ├── network/          # VPC 10.20.0.0/16, public-a (entry node), private-a/b (Aurora), no NAT
    ├── kms/              # alias/weekend2-prod-data, yearly rotation
    ├── secrets/          # secret containers only — values are set outside Terraform
    ├── database/         # weekend2-prod-aurora (0–2 ACU, auto-pause 300 s, Data API)
    ├── app/              # weekend2-prod-api (+ IAM-only Function URL) and weekend2-prod-worker
    ├── scheduler/        # schedule group weekend2-prod + daily retention/export jobs (IST)
    ├── entry_node/       # weekend2-prod-entry (t4g.nano, tailnet name node-a1, SSM only)
    ├── backup/           # weekend2-prod-backups-<account-id> (Object Lock, SSE-KMS)
    └── budget/           # weekend2-prod-monthly (USD 52 ≈ ₹5,000; alerts 50/80/100% + forecast)
```

## Versions (checked 2026-10-02)
| Tool | Version | Source |
|---|---|---|
| Terraform | `>= 1.16.0, < 2.0.0` (validated with 1.16.5) | checkpoint-api.hashicorp.com |
| AWS provider | `~> 6.67` (6.67.0, 2026-09-30) | registry.terraform.io |
| Archive provider | `~> 2.8` (2.8.1) | registry.terraform.io |

`.terraform.lock.hcl` files are committed, with hashes for `darwin_arm64` and `linux_amd64`.

⚠️ **Homebrew's `terraform` is stuck at 1.5.7** (the last MPL release). Install the current version from HashiCorp's tap:
```bash
brew tap hashicorp/tap && brew install hashicorp/tap/terraform
terraform version   # expect 1.16.x
```

## Before the first plan (one-time, manual)
1. **Pick an Aurora version** that supports scale-to-zero (16.3+) and exists in Mumbai:
   ```bash
   aws rds describe-db-engine-versions --engine aurora-postgresql --region ap-south-1 \
     --query 'DBEngineVersions[].EngineVersion' --output text
   ```
2. **Confirm the Bedrock model IDs** in the India profile. The Sonnet 5 ID is `Not verified`:
   ```bash
   aws bedrock list-inference-profiles --region ap-south-1 \
     --query "inferenceProfileSummaries[?starts_with(inferenceProfileId,'in.')].inferenceProfileId"
   ```
   Anthropic models may need a one-time use-case form in the Bedrock console.
3. Copy `terraform.tfvars.example` → `terraform.tfvars` and `backend.hcl.example` → `backend.hcl`. Both copies are git-ignored and hold your account ID and e-mail.

## Run order
```bash
# 1. State bucket (once)
cd infra/bootstrap
terraform init && terraform plan -out=tfplan    # review, then:
terraform apply tfplan

# 2. Production
cd ../envs/prod
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan                      # review every line
terraform apply tfplan                          # needs a security checkpoint (IAM, network, cost)

# 3. Secret values (never via Terraform), e.g.:
aws secretsmanager put-secret-value --secret-id weekend2-prod/tailscale/authkey \
  --secret-string file://authkey.txt && rm -P authkey.txt
```

## Security notes
- **No inbound rules anywhere.** Admin access is `aws ssm start-session --target <instance-id>`.
- **The Function URL uses `AWS_IAM` auth.** Only the entry node's role (plus admins) can invoke it.
- **The Aurora master password is created and rotated by RDS** in Secrets Manager, so it never appears in Terraform state. Phase 1 adds a least-privilege app user.
- **Keep the tailnet hostname generic** (`node-a1`). Tailscale HTTPS certificates publish it in public Certificate Transparency logs.
- **Nothing polls Aurora,** so it can pause. A per-minute job would keep it awake around the clock (≈ $66/month at 0.5 ACU).

## Cost (expected use, prices from the AWS Price List API, ap-south-1)
About **USD 42.8 ≈ ₹4,114/month** including Claude, against a budget of ₹5,000. The per-resource breakdown is in the tracker's **Resources** sheet.

## Not yet included (later phases)
GitHub OIDC deploy role (Phase 5) · Ansible role for Caddy and the SigV4 proxy on the entry node (Phase 2) · VPC flow logs (cost decision) · Transcribe/Polly permissions (Phase 2 voice decision).
