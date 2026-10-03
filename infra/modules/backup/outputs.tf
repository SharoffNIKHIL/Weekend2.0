# infra/modules/backup/outputs.tf
output "bucket_name" {
  description = "Backup bucket name."
  value       = google_storage_bucket.this.name
}
