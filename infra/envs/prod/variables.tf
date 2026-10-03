# infra/envs/prod/variables.tf
variable "project_id" {
  description = "GCP project ID for prod. Sensitive-ish: kept in terraform.tfvars / GitHub secret, not in code."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.project_id))
    error_message = "project_id must be a valid GCP project ID."
  }
}

variable "billing_account_id" {
  description = "Billing account ID (XXXXXX-XXXXXX-XXXXXX) for the budget."
  type        = string
  sensitive   = true
}

variable "alert_email" {
  description = "E-mail for budget alerts."
  type        = string
  sensitive   = true
}

variable "region" {
  description = "Primary region (Cloud Run, Firestore, Secret Manager, GCS, KMS)."
  type        = string
  default     = "asia-south1"
}

variable "env" {
  description = "Environment name."
  type        = string
  default     = "prod"
}

variable "project_name" {
  description = "Short project name used in resource names."
  type        = string
  default     = "weekend2"
}

variable "owner" {
  description = "Value for the 'owner' label."
  type        = string
  default     = "nikhil"
}

variable "node_region" {
  description = "Region of the entry-node subnet and Cloud NAT."
  type        = string
  default     = "asia-south1"
}

variable "node_zone" {
  description = "Zone of the entry node."
  type        = string
  default     = "asia-south1-a"
}

variable "subnet_cidr" {
  description = "Entry-node subnet CIDR."
  type        = string
  default     = "10.20.0.0/24"
}

variable "tailnet_hostname" {
  description = "Non-identifying tailnet hostname for the entry node."
  type        = string
  default     = "node-a1"
}

variable "tailscale_tag" {
  description = "Tailscale ACL tag for the entry node."
  type        = string
  default     = "tag:weekend"
}

variable "app_image" {
  description = "Container image for the API and worker (placeholder until Phase 1)."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}

variable "vertex_location" {
  description = "Vertex AI location for Claude. \"global\": current Claude models have no India region (P7 data exit)."
  type        = string
  default     = "global"
}

variable "model_default" {
  description = "Default Vertex AI model ID."
  type        = string
  default     = "claude-haiku-4-5@20251001"
}

variable "model_strong" {
  description = "Stronger Vertex AI model ID."
  type        = string
  default     = "claude-sonnet-5"
}

variable "budget_amount_inr" {
  description = "Monthly budget in INR before tax (GCP budgets exclude GST)."
  type        = number
  default     = 4237
}
