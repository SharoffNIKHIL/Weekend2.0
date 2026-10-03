# infra/modules/registry/variables.tf
variable "name_prefix" {
  description = "Resource-name prefix (<project>-<env>)."
  type        = string
}

variable "region" {
  description = "Repository location."
  type        = string
}

variable "kms_key_id" {
  description = "CMEK key for the repository (null = Google-managed)."
  type        = string
  default     = null
}

variable "keep_versions" {
  description = "Newest image versions to keep; older ones are deleted."
  type        = number
}
