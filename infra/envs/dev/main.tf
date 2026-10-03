# infra/envs/dev/main.tf
# Weekend 2.0 — dev on GCP (D4 = GCP, 2026-10-03). PROVISIONAL — depends on D1/D2.
# FREE BY DEFAULT. Everything that has a monthly cost is behind a switch that defaults to off
# ("on hold") until the owner approves the cost:
#   on hold  enable_cmek        Cloud KMS key (≈ $0.10/month)   → Google-managed encryption meanwhile
#   on hold  entry_node_enabled e2-micro is free in us-central1, but its egress is not:
#            entry_node_egress  "nat" (Cloud NAT ≈ $1/month) or "external_ip" (IPv4 billing Not verified)
#   free     Cloud Run (scale to zero), Firestore (default) quota, Secret Manager ≤ 6 versions,
#            Cloud Scheduler 2 of 3 free jobs, Cloud Tasks, Logging, budget, backup bucket in
#            US-CENTRAL1 (5 GB free). Dev holds NO real personal data.
# Any apply still needs an OPEN billing account linked to the project (free tiers require it).

data "google_kms_crypto_key" "data" {
  count = var.enable_cmek ? 1 : 0

  name     = "data"
  key_ring = "projects/${var.project_id}/locations/${var.region}/keyRings/${local.name_prefix}"
}

locals {
  name_prefix = "${var.project_name}-${var.env}" # weekend2-dev
  kms_key_id  = var.enable_cmek ? data.google_kms_crypto_key.data[0].id : null
}

module "network" {
  source = "../../modules/network"

  name_prefix = local.name_prefix
  region      = var.node_region
  subnet_cidr = var.subnet_cidr
  enable_nat  = var.entry_node_enabled && var.entry_node_egress == "nat"
}

module "secrets" {
  source = "../../modules/secrets"

  name_prefix = local.name_prefix
  region      = var.region
  kms_key_id  = local.kms_key_id
  secrets = {
    "tailscale/authkey"       = "Tailscale auth key for the dev entry node (one-off, tagged tag:weekend-dev, pre-approved)"
    "app/session-signing-key" = "HMAC key for passkey session cookies (dev)"
  }
}

module "database" {
  source = "../../modules/database"

  location              = var.region
  kms_key_id            = local.kms_key_id
  delete_protection     = false
  pitr                  = false
  backup_retention_days = 0
}

module "backup" {
  source = "../../modules/backup"

  project_id       = var.project_id
  region           = var.region
  location         = var.backup_location
  kms_key_id       = local.kms_key_id
  retention_days   = 1
  soft_delete_days = 0
  force_destroy    = true
}

module "app" {
  source = "../../modules/app"

  name_prefix         = local.name_prefix
  region              = var.region
  image               = var.app_image
  firestore_database  = module.database.database_name
  backup_bucket_name  = module.backup.bucket_name
  api_secrets         = { session_signing_key = module.secrets.ids["app/session-signing-key"] }
  vertex_location     = var.vertex_location
  model_default       = var.model_default
  model_strong        = var.model_strong
  max_instances       = 1
  log_level           = "DEBUG"
  deletion_protection = false
}

module "scheduler" {
  source = "../../modules/scheduler"

  name_prefix             = local.name_prefix
  region                  = var.region
  worker_url              = module.app.worker_url
  invoker_service_account = module.app.invoker_service_account
}

module "entry_node" {
  source = "../../modules/entry_node"
  count  = var.entry_node_enabled ? 1 : 0

  name_prefix         = local.name_prefix
  project_id          = var.project_id
  zone                = var.node_zone
  subnet_id           = module.network.subnet_id
  node_tag            = module.network.node_tag
  machine_type        = var.entry_node_machine_type
  disk_type           = "pd-standard" # 30 GB-month free with the always-free e2-micro
  disk_kms_key_id     = null          # node is in a US free-tier zone; no personal data in dev
  external_ip         = var.entry_node_egress == "external_ip"
  tailscale_secret_id = module.secrets.ids["tailscale/authkey"]
  api_service_name    = module.app.api_service_name
  api_region          = var.region
  tailnet_hostname    = var.tailnet_hostname
  tailscale_tag       = var.tailscale_tag
  deletion_protection = false
}

module "budget" {
  source = "../../modules/budget"

  name_prefix        = local.name_prefix
  billing_account_id = var.billing_account_id
  alert_email        = var.alert_email
  amount             = var.budget_amount_inr
}
