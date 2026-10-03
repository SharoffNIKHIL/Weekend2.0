# infra/modules/secrets/variables.tf
variable "name_prefix" {
  description = "Prefix for secret IDs, e.g. weekend2-dev."
  type        = string
}

variable "region" {
  description = "Single replica location (data residency)."
  type        = string
}

variable "kms_key_id" {
  description = "CMEK key in the replica's location. null = Google-managed encryption."
  type        = string
  default     = null
}

variable "secrets" {
  description = "Map of secret path (e.g. tailscale/authkey) => description."
  type        = map(string)
}
