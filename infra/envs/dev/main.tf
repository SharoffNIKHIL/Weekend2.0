# infra/envs/dev/main.tf
# Weekend 2.0 — dev. Same modules as prod (D1 option "E2 (revised)", PROVISIONAL — depends on D1),
# sized for free tier / near-zero cost and built to be destroyed:
#   - entry node t4g.small (EC2 T4g free trial, 750 h/month, until 2026-12-31) and can be switched off
#   - Aurora 0–1 ACU, 1-day backups, no deletion protection, no final snapshot
#   - KMS 7-day deletion window; secrets deleted at once; backup bucket force-destroyable, 1-day lock
#   - no Google connector secret (Phase 3); 3-day log retention; USD 12 budget
# Never put real personal data in dev.

data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.env}" # weekend2-dev
}

module "network" {
  source = "../../modules/network"

  name_prefix          = local.name_prefix
  vpc_cidr             = var.vpc_cidr
  azs                  = var.azs
  public_subnet_cidr   = var.public_subnet_cidr
  private_subnet_cidrs = var.private_subnet_cidrs
}

module "kms" {
  source = "../../modules/kms"

  name_prefix             = local.name_prefix
  deletion_window_in_days = 7
}

module "secrets" {
  source = "../../modules/secrets"

  name_prefix             = local.name_prefix
  kms_key_arn             = module.kms.key_arn
  recovery_window_in_days = 0 # dev: delete at once so destroy/apply cycles can reuse the names
  secrets = {
    "tailscale/authkey"       = "Tailscale auth key for the dev entry node (one-off, tagged tag:weekend-dev, pre-approved)"
    "app/session-signing-key" = "HMAC key for passkey session cookies (dev)"
  }
}

module "database" {
  source = "../../modules/database"

  name_prefix              = local.name_prefix
  vpc_id                   = module.network.vpc_id
  subnet_ids               = module.network.private_subnet_ids
  kms_key_arn              = module.kms.key_arn
  engine_version           = var.aurora_engine_version
  max_acu                  = var.aurora_max_acu
  seconds_until_auto_pause = var.aurora_seconds_until_auto_pause
  backup_retention_days    = 1
  deletion_protection      = false
  skip_final_snapshot      = true
}

module "backup" {
  source = "../../modules/backup"

  name_prefix      = local.name_prefix
  account_id       = data.aws_caller_identity.current.account_id
  kms_key_arn      = module.kms.key_arn
  object_lock_days = 1
  force_destroy    = true
}

module "scheduler" {
  source = "../../modules/scheduler"

  name_prefix         = local.name_prefix
  worker_function_arn = module.app.worker_function_arn
}

module "app" {
  source = "../../modules/app"

  name_prefix         = local.name_prefix
  kms_key_arn         = module.kms.key_arn
  db_cluster_arn      = module.database.cluster_arn
  db_secret_arn       = module.database.master_secret_arn
  db_name             = module.database.database_name
  app_secret_arns     = [module.secrets.arns["app/session-signing-key"]]
  backup_bucket_name  = module.backup.bucket_name
  backup_bucket_arn   = module.backup.bucket_arn
  schedule_group_name = module.scheduler.group_name
  scheduler_role_arn  = module.scheduler.role_arn
  python_runtime      = var.lambda_python_runtime
  model_default       = var.bedrock_model_default
  model_strong        = var.bedrock_model_strong
  package_path        = var.app_package_path
  log_retention_days  = 3
  log_level           = "DEBUG"
}

module "entry_node" {
  source = "../../modules/entry_node"
  count  = var.entry_node_enabled ? 1 : 0

  name_prefix          = local.name_prefix
  region               = var.region
  vpc_id               = module.network.vpc_id
  subnet_id            = module.network.public_subnet_id
  kms_key_arn          = module.kms.key_arn
  tailscale_secret_arn = module.secrets.arns["tailscale/authkey"]
  api_function_arn     = module.app.api_function_arn
  instance_type        = var.entry_node_instance_type
  tailnet_hostname     = var.tailnet_hostname
  tailscale_tag        = var.tailscale_tag
}

module "budget" {
  source = "../../modules/budget"

  name_prefix = local.name_prefix
  limit_usd   = var.budget_limit_usd
  alert_email = var.alert_email
}
