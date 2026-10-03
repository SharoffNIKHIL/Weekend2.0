# infra/modules/backup/main.tf
# Bucket for data exports (P6), audit-log copies (P8) and restore drills (P10).
# A bucket retention policy blocks deletion and overwrite of every object for N days (not locked,
# so the owner can still remove the policy for a P6 purge — see infra/README.md). CMEK, private,
# uniform access. Retention policies and Object Versioning are mutually exclusive, so versioning is off.

resource "google_storage_bucket" "this" {
  name                        = "${var.project_id}-backups"
  location                    = coalesce(var.location, var.region)
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = var.force_destroy

  dynamic "encryption" {
    for_each = var.kms_key_id == null ? [] : [var.kms_key_id]
    content {
      default_kms_key_name = encryption.value
    }
  }

  retention_policy {
    retention_period = var.retention_days * 86400
    is_locked        = false
  }

  soft_delete_policy {
    retention_duration_seconds = var.soft_delete_days * 86400
  }

  lifecycle_rule {
    condition {
      age            = 35
      matches_prefix = ["daily/"]
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age            = 365
      matches_prefix = ["monthly/"]
    }
    action {
      type = "Delete"
    }
  }

  lifecycle_rule {
    condition {
      age            = 730
      matches_prefix = ["audit/"]
    }
    action {
      type = "Delete"
    }
  }
}
