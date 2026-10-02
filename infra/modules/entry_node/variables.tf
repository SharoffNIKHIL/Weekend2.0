# infra/modules/entry_node/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}

variable "region" {
  description = "AWS region (used by the boot script)."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID."
  type        = string
}

variable "subnet_id" {
  description = "Public subnet ID (egress only)."
  type        = string
}

variable "kms_key_arn" {
  description = "Project KMS key (root volume, secret decryption)."
  type        = string
}

variable "tailscale_secret_arn" {
  description = "ARN of the Secrets Manager secret holding a Tailscale auth key."
  type        = string
}

variable "api_function_arn" {
  description = "API Lambda ARN the node may call via its Function URL."
  type        = string
}

variable "instance_type" {
  description = "Instance type. t4g.nano ($0.0028/h in ap-south-1, 2026-09-25 price list)."
  type        = string
  default     = "t4g.nano"
}

variable "root_volume_gb" {
  description = "Root volume size (gp3)."
  type        = number
  default     = 8
}

variable "tailnet_hostname" {
  description = "Hostname on the tailnet. Keep it non-identifying: HTTPS certs publish it in public CT logs."
  type        = string
  default     = "node-a1"
}

variable "tailscale_tag" {
  description = "Tailscale ACL tag for this node."
  type        = string
  default     = "tag:weekend"
}
