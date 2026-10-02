# infra/modules/network/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR block (private planning range)."
  type        = string
  default     = "10.20.0.0/16"
}

variable "azs" {
  description = "Two availability zones (Aurora DB subnet groups need at least 2)."
  type        = list(string)
  default     = ["ap-south-1a", "ap-south-1b"]

  validation {
    condition     = length(var.azs) == 2
    error_message = "Provide exactly two availability zones."
  }
}

variable "public_subnet_cidr" {
  description = "CIDR for the public subnet that holds only the entry node."
  type        = string
  default     = "10.20.0.0/24"
}

variable "private_subnet_cidrs" {
  description = "Two CIDRs for the private (Aurora) subnets."
  type        = list(string)
  default     = ["10.20.10.0/24", "10.20.11.0/24"]
}
