# infra/modules/backup/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}

variable "account_id" {
  description = "AWS account ID, used to make the bucket name globally unique."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key for SSE-KMS."
  type        = string
}

variable "object_lock_days" {
  description = "Default Object Lock retention (governance mode), in days."
  type        = number
  default     = 35
}

variable "force_destroy" {
  description = "Let terraform destroy delete every object, including governance-locked ones. Keep false in prod."
  type        = bool
  default     = false
}
