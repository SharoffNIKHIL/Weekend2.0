# infra/modules/database/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "subnet_ids" {
  description = "Two private subnet IDs in different AZs."
  type        = list(string)
}

variable "kms_key_arn" {
  description = "KMS key for storage and the managed master-user secret."
  type        = string
}

variable "engine_version" {
  description = "Aurora PostgreSQL version. Scale-to-zero needs 16.3+ (or 15.7+, 14.12+, 13.15+). Check what Mumbai offers: aws rds describe-db-engine-versions --engine aurora-postgresql --region ap-south-1 --query 'DBEngineVersions[].EngineVersion'"
  type        = string
}

variable "database_name" {
  description = "Initial database name."
  type        = string
  default     = "weekend"
}

variable "master_username" {
  description = "Master username (password is managed by RDS in Secrets Manager)."
  type        = string
  default     = "weekend_admin"
}

variable "max_acu" {
  description = "Maximum Aurora Capacity Units. 2 ACU is ample for one user."
  type        = number
  default     = 2

  validation {
    condition     = var.max_acu >= 1 && var.max_acu <= 8
    error_message = "max_acu must be between 1 and 8 for this single-user design."
  }
}

variable "seconds_until_auto_pause" {
  description = "Idle seconds before pausing to 0 ACU (300–86400)."
  type        = number
  default     = 300

  validation {
    condition     = var.seconds_until_auto_pause >= 300 && var.seconds_until_auto_pause <= 86400
    error_message = "seconds_until_auto_pause must be between 300 and 86400."
  }
}

variable "backup_retention_days" {
  description = "Automated backup retention (days)."
  type        = number
  default     = 7
}
