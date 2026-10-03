# infra/modules/database/main.tf
# Firestore (Native mode), the (default) database so the free daily quota applies.
# Serverless: no instance to pause, no idle charge. CMEK-encrypted (P3); vector search
# indexes for memory retrieval are added in Phase 1. CMEK is optional (KMS has a small monthly cost). Daily backups with fixed retention (P10).

resource "google_firestore_database" "this" {
  name                              = "(default)"
  location_id                       = var.location
  type                              = "FIRESTORE_NATIVE"
  concurrency_mode                  = "OPTIMISTIC"
  app_engine_integration_mode       = "DISABLED"
  point_in_time_recovery_enablement = var.pitr ? "POINT_IN_TIME_RECOVERY_ENABLED" : "POINT_IN_TIME_RECOVERY_DISABLED"
  delete_protection_state           = var.delete_protection ? "DELETE_PROTECTION_ENABLED" : "DELETE_PROTECTION_DISABLED"
  deletion_policy                   = var.delete_protection ? "ABANDON" : "DELETE"

  dynamic "cmek_config" {
    for_each = var.kms_key_id == null ? [] : [var.kms_key_id]
    content {
      kms_key_name = cmek_config.value
    }
  }
}

resource "google_firestore_backup_schedule" "daily" {
  count = var.backup_retention_days > 0 ? 1 : 0

  database  = google_firestore_database.this.name
  retention = "${var.backup_retention_days * 86400}s"

  daily_recurrence {}
}
