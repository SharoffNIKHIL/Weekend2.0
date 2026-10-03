# infra/modules/entry_node/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-dev."
  type        = string
}

variable "project_id" {
  description = "Project ID (for project-level log/metric writer roles)."
  type        = string
}

variable "zone" {
  description = "Zone. Always-free e2-micro: us-central1/us-east1/us-west1 zones only (dev). Prod: asia-south1."
  type        = string
}

variable "subnet_id" {
  description = "Subnet in the zone's region."
  type        = string
}

variable "node_tag" {
  description = "Network tag (matches the IAP SSH firewall rule)."
  type        = string
}

variable "machine_type" {
  description = "Machine type."
  type        = string
  default     = "e2-micro"
}

variable "image" {
  description = "Boot image."
  type        = string
  default     = "debian-cloud/debian-12"
}

variable "disk_gb" {
  description = "Boot disk size (GB)."
  type        = number
  default     = 10
}

variable "disk_type" {
  description = "pd-standard (always-free 30 GB in US free-tier regions) or pd-balanced."
  type        = string
  default     = "pd-balanced"
}

variable "disk_kms_key_id" {
  description = "CMEK key for the boot disk (must be in the disk's region). null = Google-managed key."
  type        = string
  default     = null
}

variable "tailscale_secret_id" {
  description = "Secret resource ID holding a one-off Tailscale auth key."
  type        = string
}

variable "api_service_name" {
  description = "Cloud Run API service the node may invoke."
  type        = string
}

variable "api_region" {
  description = "Region of the Cloud Run API."
  type        = string
}

variable "tailnet_hostname" {
  description = "Hostname on the tailnet. Keep it non-identifying: HTTPS certs publish it in public CT logs."
  type        = string
}

variable "tailscale_tag" {
  description = "Tailscale ACL tag for this node."
  type        = string
}

variable "deletion_protection" {
  description = "Block instance deletion. Dev sets false."
  type        = bool
  default     = true
}

variable "external_ip" {
  description = "Give the VM an ephemeral external IP for egress instead of Cloud NAT (in-use IPv4 is billed; free-tier treatment Not verified)."
  type        = bool
  default     = false
}
