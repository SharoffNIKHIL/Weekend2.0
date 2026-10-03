# infra/modules/budget/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-dev."
  type        = string
}

variable "billing_account_id" {
  description = "Billing account ID (XXXXXX-XXXXXX-XXXXXX). Set in terraform.tfvars / GitHub secret."
  type        = string
  sensitive   = true
}

variable "alert_email" {
  description = "E-mail for budget alerts. Set in terraform.tfvars / GitHub secret."
  type        = string
  sensitive   = true
}

variable "currency" {
  description = "Must match the billing account currency (INR for Indian accounts)."
  type        = string
  default     = "INR"
}

variable "amount" {
  description = "Monthly budget amount in var.currency, before tax."
  type        = number
}

variable "actual_thresholds" {
  description = "Alert thresholds on actual spend (fractions)."
  type        = list(number)
  default     = [0.5, 0.8, 1.0]
}
