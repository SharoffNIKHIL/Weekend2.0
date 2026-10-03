# infra/modules/entry_node/outputs.tf
output "instance_name" {
  description = "Instance name (gcloud compute ssh <name> --zone <zone> --tunnel-through-iap)."
  value       = google_compute_instance.this.name
}

output "service_account" {
  description = "Entry node service account."
  value       = google_service_account.entry.email
}
