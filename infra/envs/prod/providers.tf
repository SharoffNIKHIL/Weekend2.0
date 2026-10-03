# infra/envs/prod/providers.tf
provider "google" {
  project               = var.project_id
  region                = var.region
  user_project_override = true # bill API quota (e.g. Billing Budgets) to this project
  billing_project       = var.project_id

  default_labels = {
    owner      = var.owner
    project    = "personal-ai-agent"
    env        = var.env
    managed_by = "terraform"
  }
}
