# infra/modules/budget/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}

variable "limit_usd" {
  description = "Monthly budget in USD (₹5,000 ≈ USD 52 at ₹96.12 on 2026-10-02)."
  type        = number
}

variable "alert_email" {
  description = "E-mail address for budget alerts (set in terraform.tfvars, git-ignored)."
  type        = string
  sensitive   = true
}

variable "actual_thresholds_percent" {
  description = "Alert thresholds on actual spend, in percent."
  type        = list(number)
  default     = [50, 80, 100]
}
