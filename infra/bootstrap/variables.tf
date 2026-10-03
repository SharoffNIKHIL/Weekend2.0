# infra/bootstrap/variables.tf
variable "region" {
  description = "AWS region for the Terraform state bucket."
  type        = string
  default     = "ap-south-1"
}

variable "aws_account_id" {
  description = "12-digit AWS account ID. Guards against applying to the wrong account. Set in terraform.tfvars (git-ignored)."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be exactly 12 digits."
  }
}

variable "owner" {
  description = "Value for the 'owner' tag."
  type        = string
  default     = "nikhil"
}

variable "project_name" {
  description = "Short project name used as the resource-name prefix."
  type        = string
  default     = "weekend2"
}

variable "github_repository" {
  description = "GitHub repository (owner/name) allowed to assume the dev plan role via OIDC."
  type        = string
  default     = "SharoffNIKHIL/Weekend2.0"
}
