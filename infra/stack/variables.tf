# infra/stack/variables.tf
# No environment values here. Variables WITHOUT a default must come from infra/values/<env>.tfvars
# (non-identifying) or secrets.auto.tfvars (identifying). Defaults are only the SECURE / NO-COST
# choice, so a missing value never weakens security or adds cost. Format: infra/values/env.tfvars.example

# ---------- identifying (secrets.auto.tfvars — never committed) ----------
variable "project_id" {
  description = "GCP project ID of this environment."
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

# ---------- environment identity (values file) ----------
variable "env" {
  description = "Environment name; used in resource names (<project_name>-<env>-*). Lowercase, 2-8 chars."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,7}$", var.env))
    error_message = "env must be 2-8 lowercase letters/digits, starting with a letter."
  }
}

variable "project_name" {
  description = "Short project name used as the resource-name prefix."
  type        = string
  default     = "weekend2"
}

variable "owner" {
  description = "Value for the 'owner' label."
  type        = string
}

# ---------- locations (values file) ----------
variable "region" {
  description = "Primary region for data and services (Cloud Run, Firestore, Secret Manager, GCS, KMS)."
  type        = string
}

variable "backup_location" {
  description = "Backup bucket location (null = region)."
  type        = string
  default     = null
}

variable "node_region" {
  description = "Region of the entry-node subnet and Cloud NAT."
  type        = string
}

variable "node_zone" {
  description = "Zone of the entry node."
  type        = string
}

variable "subnet_cidr" {
  description = "Entry-node subnet CIDR."
  type        = string
}

# ---------- cost switches (default: off = no cost) ----------
variable "enable_cmek" {
  description = "Use the bootstrap CMEK key for Firestore, secrets, registry and bucket (KMS cost). false = Google-managed encryption."
  type        = bool
  default     = false
}

variable "entry_node_enabled" {
  description = "Create the tailnet entry node (VM + egress cost unless in the always-free zone). false = no VM."
  type        = bool
  default     = false
}

variable "entry_node_egress" {
  description = "\"nat\" (Cloud NAT, no public IP) or \"external_ip\" (egress-only IPv4)."
  type        = string
  default     = "nat"

  validation {
    condition     = contains(["nat", "external_ip"], var.entry_node_egress)
    error_message = "entry_node_egress must be nat or external_ip."
  }
}

variable "entry_node_machine_type" {
  description = "Entry-node machine type."
  type        = string
  default     = "e2-micro"
}

variable "entry_node_disk_type" {
  description = "Entry-node boot disk type (pd-standard is in the e2-micro free tier)."
  type        = string
  default     = "pd-standard"
}

variable "entry_node_disk_cmek" {
  description = "Encrypt the entry-node disk with the CMEK key (needs enable_cmek)."
  type        = bool
  default     = false
}

# ---------- protection and retention (default: the secure choice) ----------
variable "deletion_protection" {
  description = "Deletion protection on Firestore, Cloud Run, the VM; false also lets the backup bucket be force-destroyed."
  type        = bool
  default     = true
}

variable "firestore_pitr" {
  description = "Firestore point-in-time recovery."
  type        = bool
  default     = false
}

variable "firestore_backup_retention_days" {
  description = "Daily Firestore backup retention in days (0 = no backup schedule)."
  type        = number
  default     = 7
}

variable "backup_retention_days" {
  description = "Backup bucket retention policy in days."
  type        = number
  default     = 35
}

variable "backup_soft_delete_days" {
  description = "Backup bucket soft-delete days (0 = off)."
  type        = number
  default     = 7
}

variable "registry_keep_versions" {
  description = "Container image versions kept in Artifact Registry (keeps storage inside the 0.5 GB free tier)."
  type        = number
  default     = 3
}

# ---------- app (values file) ----------
variable "secrets" {
  description = "Secret Manager containers to create: map of short name => description. Values are added out-of-band."
  type        = map(string)
}

variable "api_secret_env" {
  description = "Secrets the API reads: map of env key => short secret name from var.secrets."
  type        = map(string)
}

variable "app_image" {
  description = "Initial container image. The app CD pipeline deploys new revisions; Terraform ignores later image changes."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}

variable "api_max_instances" {
  description = "Cloud Run API max instances (cost cap)."
  type        = number
  default     = 1
}

variable "log_level" {
  description = "Application log level."
  type        = string
  default     = "INFO"
}

variable "vertex_location" {
  description = "Vertex AI location for Claude (\"global\" = data may leave India; D2 accepted 2026-10-03, P7 X1)."
  type        = string
}

variable "model_default" {
  description = "Default Vertex AI model ID."
  type        = string
}

variable "model_strong" {
  description = "Stronger Vertex AI model ID."
  type        = string
}

variable "tailnet_hostname" {
  description = "Non-identifying tailnet hostname for the entry node."
  type        = string
  default     = "node-d1"
}

variable "tailscale_tag" {
  description = "Tailscale ACL tag for the entry node."
  type        = string
}

variable "budget_amount_inr" {
  description = "Monthly budget in INR before tax (GCP budgets exclude GST)."
  type        = number
}
