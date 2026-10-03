# infra/modules/app/versions.tf
terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.5"
    }
  }
}
