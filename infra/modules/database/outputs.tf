# infra/modules/database/outputs.tf
output "database_name" {
  description = "Firestore database ID."
  value       = google_firestore_database.this.name
}
