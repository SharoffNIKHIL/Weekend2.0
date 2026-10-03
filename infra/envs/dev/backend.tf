# infra/envs/dev/backend.tf
# Partial configuration: values come from backend.hcl (git-ignored). See backend.hcl.example.
#   terraform init -backend-config=backend.hcl
terraform {
  backend "gcs" {}
}
