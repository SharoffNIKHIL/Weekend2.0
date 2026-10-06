# infra/values/dev.tfvars — DEV values. Lives ONLY on the `dev` branch (owner decision BRN-20261003, option A).
# Non-identifying only; project_id / billing_account_id / alert_email stay in secrets.
# Dev is FREE BY DEFAULT: every switch with a monthly cost is off. CD refuses to apply anything
# outside the free allowlist (infra/scripts/cost_guard.py). Dev holds NO real personal data.

env               = "dev"
owner             = "nikhil"
region            = "asia-south1"
backup_location   = "US-CENTRAL1" # 5 GB Standard storage free
node_region       = "us-central1" # always-free e2-micro region
node_zone         = "us-central1-a"
subnet_cidr       = "10.30.0.0/24"
budget_amount_inr = 850

# Cost switches — all OFF (no monthly cost)
enable_cmek        = false # KMS ≈ $0.10/month
entry_node_enabled = false # egress via NAT ≈ $1/month
entry_node_egress  = "nat"

# Dev is destroyable: no protection, minimal retention
deletion_protection             = false
firestore_pitr                  = false
firestore_backup_retention_days = 0
backup_retention_days           = 1
backup_soft_delete_days         = 0
registry_keep_versions          = 2

# App
log_level         = "DEBUG"
api_max_instances = 1
vertex_location   = "global" # D2 decided 2026-10-03; 🔓 X1, copy kept in Firestore
model_default     = "claude-haiku-4-5@20251001"
model_strong      = "claude-haiku-4-5@20251001" # dev: Haiku only (cost)
tailscale_tag     = "tag:weekend-dev"
tailnet_hostname  = "node-d1"
secrets = {
  "tailscale/authkey"       = "Tailscale auth key for the dev entry node (one-off, tagged tag:weekend-dev, pre-approved)"
  "app/session-signing-key" = "HMAC key for owner session tokens (dev)"
}
api_secret_env = {
  session_signing_key = "app/session-signing-key"
}
