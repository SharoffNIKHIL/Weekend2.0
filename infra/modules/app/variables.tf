# infra/modules/app/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-dev."
  type        = string
}

variable "region" {
  description = "Cloud Run and Cloud Tasks region."
  type        = string
}

variable "image" {
  description = "Container image for both services. Default is Google's public hello image until Phase 1 builds the app."
  type        = string
  default     = "us-docker.pkg.dev/cloudrun/container/hello"
}

variable "firestore_database" {
  description = "Firestore database ID."
  type        = string
}

variable "backup_bucket_name" {
  description = "Backup bucket name."
  type        = string
}

variable "api_secrets" {
  description = "Secrets the API may read: map of static name => secret resource ID (session signing key, connector credentials)."
  type        = map(string)
  default     = {}
}

variable "vertex_location" {
  description = "Vertex AI location for Claude: \"global\" (no India region exists for current Claude models; P7 exit) or a supported region."
  type        = string
  default     = "global"
}

variable "model_default" {
  description = "Default model ID on Vertex AI."
  type        = string
  default     = "claude-haiku-4-5@20251001"
}

variable "model_strong" {
  description = "Stronger model ID on Vertex AI for hard tasks."
  type        = string
  default     = "claude-sonnet-5"
}

variable "api_memory_mb" {
  description = "API memory in MiB."
  type        = number
  default     = 512
}

variable "max_instances" {
  description = "Maximum API instances (caps cost)."
  type        = number
  default     = 2
}

variable "log_level" {
  description = "Application log level."
  type        = string
  default     = "INFO"
}

variable "deletion_protection" {
  description = "Block terraform destroy of the Cloud Run services. Dev sets false."
  type        = bool
  default     = true
}
