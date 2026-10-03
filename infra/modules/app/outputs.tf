# infra/modules/app/outputs.tf
output "api_url" {
  description = "API URL (internal ingress, IAM-only)."
  value       = google_cloud_run_v2_service.api.uri
}

output "api_service_name" {
  description = "API Cloud Run service name."
  value       = google_cloud_run_v2_service.api.name
}

output "worker_url" {
  description = "Worker URL (internal ingress, invoker SA only)."
  value       = google_cloud_run_v2_service.worker.uri
}

output "invoker_service_account" {
  description = "Service account Cloud Scheduler / Cloud Tasks use to call the worker."
  value       = google_service_account.invoker.email
}

output "tasks_queue_id" {
  description = "Cloud Tasks queue for reminders."
  value       = google_cloud_tasks_queue.reminders.id
}
