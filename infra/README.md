# Weekend 2.0 — Infrastructure (Terraform)

All AWS resources for Weekend 2.0, in **ap-south-1 (Mumbai)**. Every resource is listed with its monthly cost in [`tracking/Weekend2.0_Tracker.xlsx`](../tracking/Weekend2.0_Tracker.xlsx) → **Resources** sheet.

> **Status: DRAFT, PROVISIONAL — depends on decision D1 (hosting).** This implements option **E2 (revised)**: serverless-first, plus one tiny entry server on the private tailnet. Nothing here has been applied.

## Design in one paragraph
Your phone and Mac reach a **t4g.nano entry node** over the Tailscale tailnet. The node has no inbound ports. It forwards each request to the **API Lambda's Function URL**, signing it with its IAM role; the URL rejects any unsigned call. Lambda (Python 3.14, arm64) runs outside the VPC. Replies are **buffered** for now: Lambda streams natively only on Node.js, so token streaming needs the Lambda Web Adapter (a Phase 1 decision). It talks to **Aurora PostgreSQL Serverless v2** through the **RDS Data API**; Aurora pauses at 0 ACU when idle. It reaches **Claude** through Bedrock's **India** inference profiles. Daily jobs run from **EventBridge Scheduler** in IST. Reminders are one-time schedules, so nothing polls the database. Everything is encrypted with one customer-managed **KMS** key, and all secrets live in **Secrets Manager**.

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
    └── budget/           # weekend2-prod-monthly (USD 52 ≈ ₹5,000 incl. GST; alerts 50/80/100% + forecast)
└── tailscale/
    └── policy.hujson     # tailnet policy: only the owner reaches tag:weekend on 443
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
3. **Enable the Claude models once, as your admin user** (⚠️ security checkpoint: third-party EULA and cost). The Lambda role deliberately has no `aws-marketplace:*` rights, so its first call would fail with `AccessDeniedException` if the model isn't enabled yet. Submit the Anthropic use-case form in the Bedrock console, then for each model:
   ```bash
   aws bedrock list-foundation-model-agreement-offers --model-id <MODEL_ID> --region ap-south-1
   aws bedrock create-foundation-model-agreement --model-id <MODEL_ID> --offer-token <TOKEN> --region ap-south-1
   aws bedrock get-foundation-model-availability --model-id <MODEL_ID> --region ap-south-1   # expect AVAILABLE
   ```
4. **Apply the tailnet policy** [`tailscale/policy.hujson`](tailscale/policy.hujson) in the Tailscale admin console (replace `<OWNER_LOGIN>`). It replaces the default allow-all policy. Without it, any device on your tailnet can use the entry node, and through it the API (P1).
5. Copy `terraform.tfvars.example` → `terraform.tfvars` and `backend.hcl.example` → `backend.hcl`. Both copies are git-ignored and hold your account ID and e-mail.

## Run order
```bash
# Saved plans contain variable values in cleartext: keep them OUTSIDE the repo.
mkdir -p ~/.tfplans && chmod 700 ~/.tfplans

# 1. State bucket (once)
cd infra/bootstrap
terraform init && terraform plan -out="$HOME/.tfplans/weekend2-bootstrap.tfplan"   # review, then:
terraform apply "$HOME/.tfplans/weekend2-bootstrap.tfplan"

# 2. Key + secret containers first, so the entry node finds its auth key at first boot
cd ../envs/prod
terraform init -backend-config=backend.hcl
terraform apply -target=module.kms -target=module.secrets

# 3. Secret values (never via Terraform). Use a one-off, tagged, pre-authorised key.
aws secretsmanager put-secret-value --secret-id weekend2-prod/tailscale/authkey \
  --secret-string file://authkey.txt && rm -P authkey.txt

# 4. Everything else
terraform plan -out="$HOME/.tfplans/weekend2-prod.tfplan"   # review every line
terraform apply "$HOME/.tfplans/weekend2-prod.tfplan"       # needs a security checkpoint (IAM, network, cost)
```
If the entry node still came up without the key, it retries for about 20 minutes and then logs an error to `/var/log/cloud-init-output.log`. Recover with `terraform apply -replace=module.entry_node.aws_instance.this`, because `user_data` runs only on first boot.

## Security notes
- **No inbound rules anywhere.** Admin access is `aws ssm start-session --target <instance-id>`.
- **The Function URL uses `AWS_IAM` auth.** Only the entry node's role (plus admins) can invoke it, and the role's `lambda:InvokeFunction` right only works via the URL (`lambda:InvokedViaFunctionUrl`). After the first apply, test once from the node with a SigV4-signed request (expect 200). If it returns 403, drop that condition; the policy is still scoped to the one function.
- **The tailnet policy is the real front door.** The entry node signs every forwarded request with its own role, so who reaches the node decides who reaches the API. Keep `tailscale/policy.hujson` applied, and keep the passkey session check in the app (Phase 2) as a second factor.
- **The Aurora master password is created and rotated by RDS** in Secrets Manager, so it never appears in Terraform state. Phase 1 adds a least-privilege app user.
- **Keep the tailnet hostname generic** (`node-a1`). Tailscale HTTPS certificates publish it in public Certificate Transparency logs.
- **Backups and Object Lock:** `daily/` copies are locked by the 35-day bucket default; the worker locks `monthly/` (365 d) and `audit/` (730 d) per object. The worker cannot delete. A P6 "delete all my data" request that must purge locked copies is an owner-only admin action (security checkpoint):
  ```bash
  aws s3api delete-object --bucket <BACKUP_BUCKET> --key <KEY> --version-id <VERSION_ID> --bypass-governance-retention
  ```
- **Nothing polls Aurora,** so it can pause. A per-minute job would keep it awake around the clock (≈ $66/month at 0.5 ACU).

## Cost (expected use, prices from the AWS Price List API, ap-south-1)
About **USD 42.8 ≈ ₹4,114/month before tax**, including Claude. An account billed by **AWS India (AISPL)** adds **18% GST**, so the expected bill is **≈ USD 50.5 ≈ ₹4,854**, about 97% of the ₹5,000 budget. That leaves only about ₹146 of headroom. The AWS Budget counts tax (the default), so it tracks what you actually pay. Check which entity bills you under Billing console → Account. If the expected bill is too close to the cap, the cheapest levers are capping Aurora at 1 ACU or dropping the public IPv4 (IPv6 egress). The per-resource breakdown is in the tracker's **Resources** sheet.

## Not yet included (later phases)
Python response streaming via Lambda Web Adapter (Phase 1) · Tailscale policy via the `tailscale/tailscale` provider (optional) · GitHub OIDC deploy role (Phase 5) · Ansible role for Caddy and the SigV4 proxy on the entry node (Phase 2) · VPC flow logs (cost decision) · Transcribe/Polly permissions (Phase 2 voice decision).
