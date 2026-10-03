# infra/envs/dev/variables.tf
variable "project_id" {
  description = "GCP project ID for dev. Sensitive-ish: kept in terraform.tfvars / GitHub secret, not in code."
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
  default     = "dev"
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
  description = "Region of the entry-node subnet and Cloud NAT (us-central1 = always-free e2-micro region)."
  type        = string
  default     = "us-central1"
}

variable "node_zone" {
  description = "Zone of the entry node."
  type        = string
  default     = "us-central1-a"
}

variable "subnet_cidr" {
  description = "Entry-node subnet CIDR."
  type        = string
  default     = "10.30.0.0/24"
}

variable "entry_node_enabled" {
  description = "Create the tailnet entry node. false = no VM (the internal-only API is then unreachable; useful for infra-only tests)."
  type        = bool
  default     = true
}

variable "entry_node_machine_type" {
  description = "Always-free: one e2-micro per billing account in us-central1/us-east1/us-west1."
  type        = string
  default     = "e2-micro"
}

variable "tailnet_hostname" {
  description = "Non-identifying tailnet hostname for the entry node."
  type        = string
  default     = "node-d1"
}

variable "tailscale_tag" {
  description = "Tailscale ACL tag for the entry node."
  type        = string
  default     = "tag:weekend-dev"
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
  default     = "claude-haiku-4-5@20251001"
}

variable "budget_amount_inr" {
  description = "Monthly budget in INR before tax (GCP budgets exclude GST)."
  type        = number
  default     = 850
}
