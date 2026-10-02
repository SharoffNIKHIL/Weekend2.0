# infra/modules/secrets/variables.tf
variable "name_prefix" {
  description = "Prefix for secret names, e.g. weekend2-prod."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key used to encrypt the secrets."
  type        = string
}

variable "secrets" {
  description = "Map of secret path suffix => description."
  type        = map(string)
}

variable "recovery_window_in_days" {
  description = "Days a deleted secret stays recoverable: 0 (delete at once, dev only) or 7–30."
  type        = number
  default     = 7

  validation {
    condition     = var.recovery_window_in_days == 0 || (var.recovery_window_in_days >= 7 && var.recovery_window_in_days <= 30)
    error_message = "recovery_window_in_days must be 0 or between 7 and 30."
  }
}
