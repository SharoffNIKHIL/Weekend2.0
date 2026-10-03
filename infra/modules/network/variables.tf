# infra/modules/network/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-dev."
  type        = string
}

variable "region" {
  description = "Region of the entry-node subnet and Cloud NAT."
  type        = string
}

variable "subnet_cidr" {
  description = "CIDR of the entry-node subnet."
  type        = string
}

variable "allow_iap_ssh" {
  description = "Allow SSH from Google's IAP range to the entry node (admin access, no public IP)."
  type        = bool
  default     = true
}

variable "node_tag" {
  description = "Network tag carried by the entry node."
  type        = string
  default     = "entry-node"
}
