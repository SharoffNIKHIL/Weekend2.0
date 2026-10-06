# infra/stack/main.tf
# Weekend 2.0 — ONE Terraform root for every environment (dev, prod, any new one).
# The code holds NO environment values. Each environment supplies two files at plan/apply time:
#   infra/values/<env>.tfvars      non-identifying settings — committed ONLY on that env's branch
#   secrets.auto.tfvars            project ID, billing account, alert e-mail — GitHub environment
#                                  secrets in CI; ../credentials/<project>-<env>.secrets.tfvars locally
# New environment = new branch + new values file + bootstrap for its project. No code changes.
#
# Cost switches (all default to the PAID-SAFE "off" or the SECURE value; values files turn them on):
#   enable_cmek        Cloud KMS key (≈ $0.10/month)
#   entry_node_enabled e2-micro tailnet node; egress via Cloud NAT (≈ $1/month) or external IPv4
#   Everything else fits free tiers at one-user scale (DESIGN §21).

data "google_kms_crypto_key" "data" {
  count = var.enable_cmek ? 1 : 0

  name     = "data"
  key_ring = "projects/${var.project_id}/locations/${var.region}/keyRings/${local.name_prefix}"
}

locals {
  name_prefix = "${var.project_name}-${var.env}"
  kms_key_id  = var.enable_cmek ? data.google_kms_crypto_key.data[0].id : null
}

module "network" {
  source = "../modules/network"

  name_prefix = local.name_prefix
  region      = var.node_region
  subnet_cidr = var.subnet_cidr
  enable_nat  = var.entry_node_enabled && var.entry_node_egress == "nat"
}

module "secrets" {
  source = "../modules/secrets"

  name_prefix = local.name_prefix
  region      = var.region
  kms_key_id  = local.kms_key_id
  secrets     = var.secrets
}

module "database" {
  source = "../modules/database"

  location              = var.region
  kms_key_id            = local.kms_key_id
  delete_protection     = var.deletion_protection
  pitr                  = var.firestore_pitr
  backup_retention_days = var.firestore_backup_retention_days
}

module "backup" {
  source = "../modules/backup"

  project_id       = var.project_id
  region           = var.region
  location         = var.backup_location
  kms_key_id       = local.kms_key_id
  retention_days   = var.backup_retention_days
  soft_delete_days = var.backup_soft_delete_days
  force_destroy    = !var.deletion_protection
}

module "registry" {
  source = "../modules/registry"

  name_prefix   = local.name_prefix
  region        = var.region
  kms_key_id    = local.kms_key_id
  keep_versions = var.registry_keep_versions
}

module "app" {
  source = "../modules/app"

  name_prefix         = local.name_prefix
  region              = var.region
  image               = var.app_image
  firestore_database  = module.database.database_name
  backup_bucket_name  = module.backup.bucket_name
  api_secrets         = { for k, v in var.api_secret_env : k => module.secrets.ids[v] }
  vertex_location     = var.vertex_location
  model_default       = var.model_default
  model_strong        = var.model_strong
  max_instances       = var.api_max_instances
  log_level           = var.log_level
  deletion_protection = var.deletion_protection
}

module "scheduler" {
  source = "../modules/scheduler"

  name_prefix             = local.name_prefix
  region                  = var.region
  worker_url              = module.app.worker_url
  invoker_service_account = module.app.invoker_service_account
}

module "entry_node" {
  source = "../modules/entry_node"
  count  = var.entry_node_enabled ? 1 : 0

  name_prefix         = local.name_prefix
  project_id          = var.project_id
  zone                = var.node_zone
  subnet_id           = module.network.subnet_id
  node_tag            = module.network.node_tag
  machine_type        = var.entry_node_machine_type
  disk_type           = var.entry_node_disk_type
  disk_kms_key_id     = var.entry_node_disk_cmek ? local.kms_key_id : null
  external_ip         = var.entry_node_egress == "external_ip"
  tailscale_secret_id = module.secrets.ids["tailscale/authkey"]
  api_service_name    = module.app.api_service_name
  api_region          = var.region
  tailnet_hostname    = var.tailnet_hostname
  tailscale_tag       = var.tailscale_tag
  deletion_protection = var.deletion_protection
}

module "budget" {
  source = "../modules/budget"

  name_prefix        = local.name_prefix
  billing_account_id = var.billing_account_id
  alert_email        = var.alert_email
  amount             = var.budget_amount_inr
}
