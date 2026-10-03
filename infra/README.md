# Weekend 2.0 — Infrastructure (Terraform)

All AWS resources for Weekend 2.0, in **ap-south-1 (Mumbai)**. Every resource is listed with its monthly cost in [`tracking/Weekend2.0_Tracker.xlsx`](../tracking/Weekend2.0_Tracker.xlsx) → **Resources** sheet.

> **Status: DRAFT, PROVISIONAL — depends on decision D1 (hosting).** This implements option **E2 (revised)**: serverless-first, plus one tiny entry server on the private tailnet. Nothing here has been applied.

## Design in one paragraph
Your phone and Mac reach a **t4g.nano entry node** over the Tailscale tailnet. The node has no inbound ports. It forwards each request to the **API Lambda's Function URL**, signing it with its IAM role; the URL rejects any unsigned call. Lambda (Python 3.14, arm64) runs outside the VPC. Replies are **buffered** for now: Lambda streams natively only on Node.js, so token streaming needs the Lambda Web Adapter (a Phase 1 decision). It talks to **Aurora PostgreSQL Serverless v2** through the **RDS Data API**; Aurora pauses at 0 ACU when idle. It reaches **Claude** through Bedrock's **India** inference profiles. Daily jobs run from **EventBridge Scheduler** in IST. Reminders are one-time schedules, so nothing polls the database. Everything is encrypted with one customer-managed **KMS** key, and all secrets live in **Secrets Manager**.

## Layout
```
infra/
├── bootstrap/            # remote-state bucket (local state, run once)
├── envs/dev/             # dev root; its values (values.auto.tfvars) live on the `dev` branch
├── envs/prod/            # prod: wires the modules together
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
    ├── policy.hujson     # template (public): only the owner reaches tag:weekend / tag:weekend-dev on 443
    └── policy.local.hujson  # your copy with the real login (git-ignored)
```

## Branches and environment values
`main` holds the base code only. Each environment's non-sensitive values are in `infra/envs/<env>/values.auto.tfvars`, committed **only on that environment's branch** (`dev` now, `prod` after D1). Work happens on `feature_<topic>` branches cut from `dev`. Sensitive values (`aws_account_id`, `alert_email`) stay in the git-ignored `terraform.tfvars`. The full flow and the CI guard rails are in [docs/BRANCHING.md](../docs/BRANCHING.md).

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
4. **Apply the tailnet policy**: copy [`tailscale/policy.hujson`](tailscale/policy.hujson) to `tailscale/policy.local.hujson` (git-ignored), replace `<OWNER_LOGIN>` there, and paste it into the Tailscale admin console. It replaces the default allow-all policy. Without it, any device on your tailnet can use the entry node, and through it the API (P1).
5. Check out the environment branch (`git switch dev`), which brings `values.auto.tfvars`. Copy `terraform.tfvars.example` → `terraform.tfvars` and `backend.hcl.example` → `backend.hcl`. Both copies are git-ignored and hold your account ID and e-mail.

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


## Dev environment (`envs/dev`)
Same modules as prod, sized to cost almost nothing and to be torn down. **Never put real personal data in dev.**

| Setting | Dev | Prod | Why |
|---|---|---|---|
| Name prefix / VPC | `weekend2-dev` / 10.30.0.0/16 | `weekend2-prod` / 10.20.0.0/16 | Both can exist side by side |
| Entry node | `t4g.small`, tailnet `node-d1`, `tag:weekend-dev`; can be switched off | `t4g.nano`, `node-a1`, `tag:weekend` | EC2 T4g free trial: 750 h/month of t4g.small, ap-south-1 included, **until 2026-12-31** |
| Aurora | 0–1 ACU, 1-day backups, no deletion protection, no final snapshot | 0–2 ACU, 7 days, protected | Destroyable |
| KMS / secrets | 7-day key deletion window; secrets deleted at once; no Google connector secret | 30 days / 7 days / 3 secrets | Fast destroy and re-apply |
| Backup bucket | 1-day default lock, `force_destroy` | 35 days, protected | Destroy can empty it |
| Logs / Claude | 3 days, DEBUG / Haiku 4.5 for both slots | 14 days, INFO / Haiku + Sonnet | Cost |
| Budget | `weekend2-dev-monthly`, USD 12 incl. tax | USD 52 | Separate alerting |

**Expected dev cost:** ≈ USD 10.8 ≈ ₹1,042/month incl. 18% GST, with the entry node on 24/7 and Aurora awake about 15 h a month. Most of that is the public IPv4 at USD 3.65; Lambda (always free), the T4g instance (trial), Scheduler, S3 and Budgets cost nothing or cents. Stop the node when you aren't testing to bring it to ≈ ₹660, or set `entry_node_enabled = false` for ≈ ₹545 and call the URL straight from your Mac. A new-account sign-up credit (USD 100–200) covers this. ⚠️ **Dev plus prod together (≈ ₹5,900) is over the ₹5,000 budget**, so destroy dev before prod goes live. The breakdown is in the tracker's **Dev env** sheet.

**AWS Free plan accounts** (accounts created on or after 2025-07-15 that haven't been upgraded) can't use some paid Marketplace offers. The Claude models on Bedrock may be among them (Not verified). If model enablement (pre-flight step 3) fails, upgrade the plan: Billing console → Free Tier → Upgrade plan.

### Dev run order
```bash
mkdir -p ~/.tfplans && chmod 700 ~/.tfplans
# 0. Pre-flight steps 1–5 above (Aurora version, model IDs, model enablement, tailnet policy, tfvars/backend for envs/dev)
# 1. State bucket (skip if already created for prod)
cd infra/bootstrap && terraform init && terraform plan -out="$HOME/.tfplans/weekend2-bootstrap.tfplan"
terraform apply "$HOME/.tfplans/weekend2-bootstrap.tfplan"

# 2. Key + secret containers first
cd ../envs/dev
terraform init -backend-config=backend.hcl
terraform apply -target=module.kms -target=module.secrets

# 3. Secret values: a ONE-OFF, pre-approved auth key tagged tag:weekend-dev (Tailscale admin → Settings → Keys)
aws secretsmanager put-secret-value --secret-id weekend2-dev/tailscale/authkey \
  --secret-string file://authkey.txt && rm -P authkey.txt
openssl rand -base64 48 | tr -d '\n' > sk.txt && aws secretsmanager put-secret-value \
  --secret-id weekend2-dev/app/session-signing-key --secret-string file://sk.txt && rm -P sk.txt

# 4. Everything else
terraform plan -out="$HOME/.tfplans/weekend2-dev.tfplan"   # review every line
terraform apply "$HOME/.tfplans/weekend2-dev.tfplan"
```

### Dev smoke tests
```bash
terraform output                                            # URL, instance ID, bucket
aws ssm start-session --target "$(terraform output -raw entry_node_instance_id)"
#   on the node:  sudo tailscale status   # expect node-d1 online, tagged tag:weekend-dev
pip install awscurl                                          # SigV4 curl, in a venv
awscurl --service lambda --region ap-south-1 "$(terraform output -raw api_function_url)"
#   expect {"status": "placeholder", "service": "weekend2-api"} (your admin identity is allowed)
curl -s -o /dev/null -w '%{http_code}\n' "$(terraform output -raw api_function_url)"   # unsigned: expect 403
```

### Save money / tear down
```bash
aws ec2 stop-instances  --instance-ids "$(terraform output -raw entry_node_instance_id)"   # no IPv4 or instance charge while stopped
aws ec2 start-instances --instance-ids "$(terraform output -raw entry_node_instance_id)"
terraform plan -destroy -out="$HOME/.tfplans/weekend2-dev-destroy.tfplan" && terraform apply "$HOME/.tfplans/weekend2-dev-destroy.tfplan"
```
Destroy schedules the KMS key for deletion after 7 days. Until then you can undo it with `aws kms cancel-key-deletion --key-id <KEY_ID>`.

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
