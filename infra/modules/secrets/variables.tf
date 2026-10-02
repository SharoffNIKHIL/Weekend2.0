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
