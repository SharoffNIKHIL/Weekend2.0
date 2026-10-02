# infra/envs/prod/variables.tf
variable "aws_account_id" {
  description = "12-digit AWS account ID (guards against the wrong account). Set in terraform.tfvars (git-ignored)."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be exactly 12 digits."
  }
}

variable "region" {
  description = "Primary AWS region."
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Short project name used in resource names."
  type        = string
  default     = "weekend2"
}

variable "env" {
  description = "Environment name."
  type        = string
  default     = "prod"
}

variable "owner" {
  description = "Value for the 'owner' tag."
  type        = string
  default     = "nikhil"
}

variable "vpc_cidr" {
  description = "VPC CIDR."
  type        = string
  default     = "10.20.0.0/16"
}

variable "azs" {
  description = "Two AZs in the region."
  type        = list(string)
  default     = ["ap-south-1a", "ap-south-1b"]
}

variable "public_subnet_cidr" {
  description = "Public (entry node) subnet CIDR."
  type        = string
  default     = "10.20.0.0/24"
}

variable "private_subnet_cidrs" {
  description = "Private (Aurora) subnet CIDRs."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL version supporting scale-to-zero (16.3+). Confirm availability in ap-south-1 before plan."
  type        = string
}

variable "aurora_max_acu" {
  description = "Aurora maximum ACU."
  type        = number
  default     = 2
}

variable "aurora_seconds_until_auto_pause" {
  description = "Idle seconds before Aurora pauses (min 300)."
  type        = number
  default     = 300
}

variable "lambda_python_runtime" {
  description = "Lambda Python runtime."
  type        = string
  default     = "python3.14"
}

variable "bedrock_model_default" {
  description = "Default Bedrock inference profile (India geo)."
  type        = string
  default     = "in.anthropic.claude-haiku-4-5-20251001-v1:0"
}

variable "bedrock_model_strong" {
  description = "Strong Bedrock inference profile (India geo). Confirm exact ID before Phase 1."
  type        = string
  default     = "in.anthropic.claude-sonnet-5"
}

variable "app_package_path" {
  description = "Path to the built app zip (null = placeholder)."
  type        = string
  default     = null
}

variable "tailnet_hostname" {
  description = "Non-identifying tailnet hostname for the entry node."
  type        = string
  default     = "node-a1"
}

variable "budget_limit_usd" {
  description = "Monthly budget in USD (₹5,000 ÷ ₹96.12 = USD 52.02 on 2026-10-02)."
  type        = number
  default     = 52
}

variable "alert_email" {
  description = "E-mail for budget alerts. Set in terraform.tfvars (git-ignored)."
  type        = string
  sensitive   = true
}
