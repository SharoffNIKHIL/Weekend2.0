# infra/envs/prod/providers.tf
provider "aws" {
  region              = var.region
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      owner      = var.owner
      project    = "personal-ai-agent"
      env        = var.env
      managed_by = "terraform"
    }
  }
}
