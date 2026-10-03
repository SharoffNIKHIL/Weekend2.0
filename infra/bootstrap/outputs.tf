# infra/bootstrap/outputs.tf
output "state_bucket" {
  description = "GCS bucket for Terraform state (backend.hcl: bucket)."
  value       = google_storage_bucket.tfstate.name
}

output "kms_key_id" {
  description = "CMEK key used by the env stack (null when enable_cmek = false)."
  value       = try(google_kms_crypto_key.data[0].id, null)
}

output "wif_provider" {
  description = "Set as GitHub environment secret GCP_WIF_PROVIDER."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "plan_service_account" {
  description = "Set as GitHub environment secret GCP_PLAN_SERVICE_ACCOUNT."
  value       = google_service_account.github_plan.email
}

output "deployer_service_account" {
  description = "Least-privilege deployer: set as GitHub environment secret GCP_DEPLOY_SERVICE_ACCOUNT (<env>-apply) and in ../credentials/<project>-<env>.terraform.json."
  value       = google_service_account.deployer.email
}
