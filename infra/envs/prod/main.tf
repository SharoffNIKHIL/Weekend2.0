# infra/envs/prod/main.tf
# Weekend 2.0 — prod on GCP (D4 = GCP, 2026-10-03). PROVISIONAL — depends on D1/D2.
# Created but idle: nothing is planned or applied for prod yet (docs/BRANCHING.md).
# Everything in asia-south1 (Mumbai) except Claude on Vertex AI ("global", P7 exit).

data "google_kms_crypto_key" "data" {
  name     = "data"
  key_ring = "projects/${var.project_id}/locations/${var.region}/keyRings/${local.name_prefix}"
}

locals {
  name_prefix = "${var.project_name}-${var.env}" # weekend2-prod
}

module "network" {
  source = "../../modules/network"

  name_prefix = local.name_prefix
  region      = var.node_region
  subnet_cidr = var.subnet_cidr
  enable_nat  = true
}

module "secrets" {
  source = "../../modules/secrets"

  name_prefix = local.name_prefix
  region      = var.region
  kms_key_id  = data.google_kms_crypto_key.data.id
  secrets = {
    "tailscale/authkey"              = "Tailscale auth key for the entry node (one-off, tagged tag:weekend, pre-approved)"
    "app/session-signing-key"        = "HMAC key for passkey session cookies"
    "connectors/google-oauth-client" = "Google OAuth client for Calendar/Gmail connectors (Phase 3)"
  }
}

module "database" {
  source = "../../modules/database"

  location              = var.region
  kms_key_id            = data.google_kms_crypto_key.data.id
  delete_protection     = true
  pitr                  = false
  backup_retention_days = 7
}

module "backup" {
  source = "../../modules/backup"

  project_id       = var.project_id
  region           = var.region
  kms_key_id       = data.google_kms_crypto_key.data.id
  retention_days   = 35
  soft_delete_days = 7
  force_destroy    = false
}

module "app" {
  source = "../../modules/app"

  name_prefix        = local.name_prefix
  region             = var.region
  image              = var.app_image
  firestore_database = module.database.database_name
  backup_bucket_name = module.backup.bucket_name
  api_secrets = {
    session_signing_key = module.secrets.ids["app/session-signing-key"]
    google_oauth_client = module.secrets.ids["connectors/google-oauth-client"]
  }
  vertex_location     = var.vertex_location
  model_default       = var.model_default
  model_strong        = var.model_strong
  deletion_protection = true
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

  name_prefix         = local.name_prefix
  project_id          = var.project_id
  zone                = var.node_zone
  subnet_id           = module.network.subnet_id
  node_tag            = module.network.node_tag
  machine_type        = "e2-micro"
  disk_type           = "pd-balanced"
  disk_kms_key_id     = data.google_kms_crypto_key.data.id
  tailscale_secret_id = module.secrets.ids["tailscale/authkey"]
  api_service_name    = module.app.api_service_name
  api_region          = var.region
  tailnet_hostname    = var.tailnet_hostname
  tailscale_tag       = var.tailscale_tag
  deletion_protection = true
}

module "budget" {
  source = "../../modules/budget"

  name_prefix        = local.name_prefix
  billing_account_id = var.billing_account_id
  alert_email        = var.alert_email
  amount             = var.budget_amount_inr
}
