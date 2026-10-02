# infra/modules/app/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}

variable "kms_key_arn" {
  description = "Project KMS key (env vars, logs, secrets)."
  type        = string
}

variable "db_cluster_arn" {
  description = "Aurora cluster ARN for the RDS Data API."
  type        = string
}

variable "db_secret_arn" {
  description = "RDS-managed master secret ARN (Phase 1 replaces this with a least-privilege app user)."
  type        = string
}

variable "db_name" {
  description = "Database name."
  type        = string
}

variable "app_secret_arns" {
  description = "Other secrets the functions may read (session signing key, connector credentials)."
  type        = list(string)
  default     = []
}

variable "backup_bucket_name" {
  description = "Backup bucket name."
  type        = string
}

variable "backup_bucket_arn" {
  description = "Backup bucket ARN."
  type        = string
}

variable "schedule_group_name" {
  description = "EventBridge Scheduler group for one-time reminder schedules."
  type        = string
}

variable "scheduler_role_arn" {
  description = "Role EventBridge Scheduler assumes to invoke the worker."
  type        = string
}

variable "python_runtime" {
  description = "Lambda Python runtime (python3.14 is GA on Lambda since 2025-11)."
  type        = string
  default     = "python3.14"
}

variable "api_memory_mb" {
  description = "API function memory in MB."
  type        = number
  default     = 1024
}

variable "model_default" {
  description = "Default Bedrock inference profile ID (India geo). Confirm in the Bedrock console before Phase 1."
  type        = string
  default     = "in.anthropic.claude-haiku-4-5-20251001-v1:0"
}

variable "model_strong" {
  description = "Stronger model profile ID for hard tasks (India geo). Exact ID not verified — confirm with: aws bedrock list-inference-profiles --region ap-south-1"
  type        = string
  default     = "in.anthropic.claude-sonnet-5"
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention."
  type        = number
  default     = 14
}

variable "log_level" {
  description = "Application log level."
  type        = string
  default     = "INFO"
}

variable "package_path" {
  description = "Path to the built app zip. Null = use the placeholder handler."
  type        = string
  default     = null
}
