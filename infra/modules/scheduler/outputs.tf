# infra/modules/scheduler/outputs.tf
output "job_names" {
  description = "Scheduler job names."
  value       = [for j in google_cloud_scheduler_job.daily : j.name]
}
