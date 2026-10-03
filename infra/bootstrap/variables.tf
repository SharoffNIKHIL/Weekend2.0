# infra/bootstrap/variables.tf
variable "project_id" {
  description = "GCP project ID for this environment. From ../credentials/<project>-<env>.secrets.tfvars (never committed)."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.project_id))
    error_message = "project_id must be a valid GCP project ID (6-30 chars, lowercase letters, digits, hyphens)."
  }
}

variable "env" {
  description = "Environment this project serves."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,7}$", var.env))
    error_message = "env must be 2-8 lowercase letters/digits, starting with a letter."
  }
}

variable "region" {
  description = "Primary region (state bucket, KMS key ring)."
  type        = string
  default     = "asia-south1"
}

variable "owner" {
  description = "Value for the 'owner' label."
  type        = string
  default     = "nikhil"
}

variable "project_name" {
  description = "Short project name used as the resource-name prefix."
  type        = string
  default     = "weekend2"
}

variable "github_repository" {
  description = "GitHub repository (owner/name) allowed to plan via Workload Identity Federation."
  type        = string
  default     = "SharoffNIKHIL/Weekend2.0"
}

variable "billing_account_id" {
  description = "Billing account ID (XXXXXX-XXXXXX-XXXXXX). When set, the plan service account gets read access to budgets. Set in terraform.tfvars."
  type        = string
  default     = null
  sensitive   = true
}

variable "enable_cmek" {
  description = "Create the Cloud KMS key ring + key (P3 CMEK). Costs ≈ $0.06/key version/month + operations. Required for prod; on hold for free dev."
  type        = bool
  default     = false
}

variable "state_location" {
  description = "State bucket location override. us-central1/us-east1/us-west1 get 5 GB Standard storage free. null = var.region."
  type        = string
  default     = null
}

variable "owner_principal" {
  description = "IAM principal allowed to impersonate the deployer (user:<e-mail>). Identifying: put it in the secrets file."
  type        = string
  sensitive   = true
}
