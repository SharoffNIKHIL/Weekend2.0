# infra/envs/dev/outputs.tf
output "api_url" {
  description = "API URL (internal ingress, IAM-only; reachable from the entry node)."
  value       = module.app.api_url
}

output "worker_url" {
  description = "Worker URL (internal ingress; Cloud Scheduler / Cloud Tasks only)."
  value       = module.app.worker_url
}

output "entry_node" {
  description = "Entry node name (gcloud compute ssh <name> --zone <zone> --tunnel-through-iap)."
  value       = try(module.entry_node[0].instance_name, null)
}

output "backup_bucket" {
  description = "Backup bucket name."
  value       = module.backup.bucket_name
}

output "secret_ids" {
  description = "Secret IDs (set values with gcloud secrets versions add)."
  value       = module.secrets.secret_ids
}
