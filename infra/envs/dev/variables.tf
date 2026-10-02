# infra/envs/dev/variables.tf
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
  default     = "dev"
}

variable "owner" {
  description = "Value for the 'owner' tag."
  type        = string
  default     = "nikhil"
}

variable "vpc_cidr" {
  description = "VPC CIDR (different from prod's 10.20.0.0/16 so both can exist side by side)."
  type        = string
  default     = "10.30.0.0/16"
}

variable "azs" {
  description = "Two AZs in the region."
  type        = list(string)
  default     = ["ap-south-1a", "ap-south-1b"]
}

variable "public_subnet_cidr" {
  description = "Public (entry node) subnet CIDR."
  type        = string
  default     = "10.30.0.0/24"
}

variable "private_subnet_cidrs" {
  description = "Private (Aurora) subnet CIDRs."
  type        = list(string)
  default     = ["10.30.10.0/24", "10.30.11.0/24"]
}

variable "aurora_engine_version" {
  description = "Aurora PostgreSQL version supporting scale-to-zero (16.3+). Confirm availability in ap-south-1 before plan."
  type        = string
}

variable "aurora_max_acu" {
  description = "Aurora maximum ACU (same as prod)."
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
  description = "Strong Bedrock inference profile (India geo, same as prod). Confirm exact ID before Phase 1."
  type        = string
  default     = "in.anthropic.claude-sonnet-5"
}

variable "app_package_path" {
  description = "Path to the built app zip (null = placeholder)."
  type        = string
  default     = null
}

variable "entry_node_enabled" {
  description = "Create the tailnet entry node. false = test the Function URL straight from your Mac with SigV4 (no EC2, no public IPv4 charge)."
  type        = bool
  default     = true
}

variable "entry_node_instance_type" {
  description = "Entry node instance type: normal on-demand t4g.nano, same as prod. Dev runs only when needed, so stop the node between sessions."
  type        = string
  default     = "t4g.nano"
}

variable "tailnet_hostname" {
  description = "Non-identifying tailnet hostname for the dev entry node."
  type        = string
  default     = "node-d1"
}

variable "tailscale_tag" {
  description = "Tailscale ACL tag for the dev node (separate from prod's tag:weekend)."
  type        = string
  default     = "tag:weekend-dev"
}

variable "budget_limit_usd" {
  description = "Monthly dev budget in USD, tax included (≈ ₹1,153 at ₹96.12 on 2026-10-02)."
  type        = number
  default     = 12
}

variable "alert_email" {
  description = "E-mail for budget alerts. Set in terraform.tfvars (git-ignored)."
  type        = string
  sensitive   = true
}
