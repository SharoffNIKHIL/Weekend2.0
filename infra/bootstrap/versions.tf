# infra/bootstrap/versions.tf
terraform {
  required_version = ">= 1.16.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.67"
    }
  }
  # Bootstrap uses LOCAL state on purpose: it creates the remote-state bucket.
  # Keep infra/bootstrap/terraform.tfstate safe (it is git-ignored).
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      owner      = var.owner
      project    = "personal-ai-agent"
      env        = "shared"
      managed_by = "terraform"
    }
  }
}
