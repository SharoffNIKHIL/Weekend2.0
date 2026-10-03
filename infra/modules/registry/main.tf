# infra/modules/registry/main.tf
# Docker repository for the app image (app CD pushes, Cloud Run pulls). Private by default (IAM).
# Cleanup keeps the newest N versions so storage stays inside the 0.5 GB/month free tier.

resource "google_artifact_registry_repository" "app" {
  repository_id          = "${var.name_prefix}-app"
  location               = var.region
  format                 = "DOCKER"
  description            = "Weekend 2.0 app images (${var.name_prefix})"
  kms_key_name           = var.kms_key_id
  cleanup_policy_dry_run = false

  docker_config {
    immutable_tags = true
  }

  cleanup_policies {
    id     = "keep-newest"
    action = "KEEP"
    most_recent_versions {
      keep_count = var.keep_versions
    }
  }

  cleanup_policies {
    id     = "delete-older"
    action = "DELETE"
    condition {
      tag_state = "ANY"
    }
  }
}
