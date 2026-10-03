# infra/modules/backup/variables.tf
variable "project_id" {
  description = "Project ID (makes the bucket name globally unique)."
  type        = string
}

variable "region" {
  description = "Bucket location."
  type        = string
}

variable "kms_key_id" {
  description = "CMEK key in the same location."
  type        = string
}

variable "retention_days" {
  description = "Bucket retention policy: objects can't be deleted or overwritten for this many days."
  type        = number
  default     = 35
}

variable "soft_delete_days" {
  description = "Soft-delete window after deletion (0 disables; 7–90 otherwise)."
  type        = number
  default     = 7

  validation {
    condition     = var.soft_delete_days == 0 || (var.soft_delete_days >= 7 && var.soft_delete_days <= 90)
    error_message = "soft_delete_days must be 0 or between 7 and 90."
  }
}

variable "force_destroy" {
  description = "Let terraform destroy delete every object. Keep false in prod."
  type        = bool
  default     = false
}
