# infra/modules/kms/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}
