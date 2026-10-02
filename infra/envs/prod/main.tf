# infra/envs/prod/main.tf
# Weekend 2.0 — production (single owner). Design: DESIGN.md §16–17, D1 option "E2 (revised)":
# serverless-first + one tiny entry node on the tailnet. PROVISIONAL — depends on D1.

data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.env}" # weekend2-prod
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

  name_prefix = local.name_prefix
}

module "secrets" {
  source = "../../modules/secrets"

  name_prefix = local.name_prefix
  kms_key_arn = module.kms.key_arn
  secrets = {
    "tailscale/authkey"              = "Tailscale auth key for the entry node (reusable=false, tagged, pre-approved)"
    "app/session-signing-key"        = "HMAC key for passkey session cookies"
    "connectors/google-oauth-client" = "Google OAuth client for Calendar/Gmail connectors (Phase 3)"
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
}

module "backup" {
  source = "../../modules/backup"

  name_prefix = local.name_prefix
  account_id  = data.aws_caller_identity.current.account_id
  kms_key_arn = module.kms.key_arn
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
  app_secret_arns     = [module.secrets.arns["app/session-signing-key"], module.secrets.arns["connectors/google-oauth-client"]]
  backup_bucket_name  = module.backup.bucket_name
  backup_bucket_arn   = module.backup.bucket_arn
  schedule_group_name = module.scheduler.group_name
  scheduler_role_arn  = module.scheduler.role_arn
  python_runtime      = var.lambda_python_runtime
  model_default       = var.bedrock_model_default
  model_strong        = var.bedrock_model_strong
  package_path        = var.app_package_path
}

module "entry_node" {
  source = "../../modules/entry_node"

  name_prefix          = local.name_prefix
  region               = var.region
  vpc_id               = module.network.vpc_id
  subnet_id            = module.network.public_subnet_id
  kms_key_arn          = module.kms.key_arn
  tailscale_secret_arn = module.secrets.arns["tailscale/authkey"]
  api_function_arn     = module.app.api_function_arn
  tailnet_hostname     = var.tailnet_hostname
}

module "budget" {
  source = "../../modules/budget"

  name_prefix = local.name_prefix
  limit_usd   = var.budget_limit_usd
  alert_email = var.alert_email
}
