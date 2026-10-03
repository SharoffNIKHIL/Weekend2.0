# infra/envs/dev/main.tf
# Weekend 2.0 — dev on GCP (D4 = GCP, 2026-10-03). PROVISIONAL — depends on D1/D2.
# Free tier wherever GCP has one, and built to be destroyed:
#   - entry node: always-free e2-micro + 10 GB pd-standard in a US free-tier zone (dev holds NO
#     real personal data; prod runs the node in asia-south1). No external IP; Cloud NAT egress.
#   - Cloud Run scale-to-zero, Firestore (default) free quota, Secret Manager ≤ 6 versions free,
#     Cloud Scheduler 2 of 3 free jobs, Cloud Tasks / Logging within free tiers
#   - Firestore without delete protection or backups; bucket force_destroy, 1-day retention
# The KMS key ring/key come from infra/bootstrap (GCP key rings can't be deleted).

data "google_kms_crypto_key" "data" {
  name     = "data"
  key_ring = "projects/${var.project_id}/locations/${var.region}/keyRings/${local.name_prefix}"
}

locals {
  name_prefix = "${var.project_name}-${var.env}" # weekend2-dev
}

module "network" {
  source = "../../modules/network"

  name_prefix = local.name_prefix
  region      = var.node_region
  subnet_cidr = var.subnet_cidr
}

module "secrets" {
  source = "../../modules/secrets"

  name_prefix = local.name_prefix
  region      = var.region
  kms_key_id  = data.google_kms_crypto_key.data.id
  secrets = {
    "tailscale/authkey"       = "Tailscale auth key for the dev entry node (one-off, tagged tag:weekend-dev, pre-approved)"
    "app/session-signing-key" = "HMAC key for passkey session cookies (dev)"
  }
}

module "database" {
  source = "../../modules/database"

  location              = var.region
  kms_key_id            = data.google_kms_crypto_key.data.id
  delete_protection     = false
  pitr                  = false
  backup_retention_days = 0
}

module "backup" {
  source = "../../modules/backup"

  project_id       = var.project_id
  region           = var.region
  kms_key_id       = data.google_kms_crypto_key.data.id
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
  api_secret_ids      = [module.secrets.ids["app/session-signing-key"]]
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
  disk_type           = "pd-standard"
  disk_kms_key_id     = null # key is in asia-south1, node is in a US free-tier zone; no personal data in dev
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
