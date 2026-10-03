# infra/modules/database/variables.tf
variable "location" {
  description = "Firestore location (asia-south1 keeps data in India)."
  type        = string
}

variable "kms_key_id" {
  description = "CMEK key in the same location. null = Google-managed encryption (free; dev only — P3 needs CMEK in prod)."
  type        = string
  default     = null
}

variable "delete_protection" {
  description = "Block deletion. Keep true in prod; dev sets false so it can be destroyed."
  type        = bool
  default     = true
}

variable "pitr" {
  description = "Point-in-time recovery (7 days, extra storage cost)."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Daily backup retention in days (0 = no backups; max 98)."
  type        = number
  default     = 7

  validation {
    condition     = var.backup_retention_days >= 0 && var.backup_retention_days <= 98
    error_message = "backup_retention_days must be between 0 and 98."
  }
}
