# infra/modules/secrets/outputs.tf
output "ids" {
  description = "Map of secret path => secret resource ID."
  value       = { for k, s in google_secret_manager_secret.this : k => s.id }
}

output "secret_ids" {
  description = "Map of secret path => short secret ID (for gcloud)."
  value       = { for k, s in google_secret_manager_secret.this : k => s.secret_id }
}
