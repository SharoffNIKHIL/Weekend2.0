# infra/modules/app/main.tf
# Two Cloud Run services (scale to zero):
#   <prefix>-api    — chat/voice/memory API. Ingress INTERNAL ONLY and IAM-only invoke: only the
#                     entry node's service account (and admins) can call it; nothing on the internet can.
#   <prefix>-worker — scheduled jobs and reminders, invoked by Cloud Scheduler / Cloud Tasks with
#                     an OIDC token from the invoker service account.
# Claude is reached through Vertex AI (location var.vertex_location; "global" = data may leave India, P7).

data "google_project" "this" {}

locals {
  common_env = {
    GCP_PROJECT        = data.google_project.this.project_id
    GCP_REGION         = var.region
    FIRESTORE_DATABASE = var.firestore_database
    VERTEX_LOCATION    = var.vertex_location
    MODEL_DEFAULT      = var.model_default
    MODEL_STRONG       = var.model_strong
    BACKUP_BUCKET      = var.backup_bucket_name
    TASKS_QUEUE        = google_cloud_tasks_queue.reminders.id
    SECRET_PREFIX      = "${var.name_prefix}-"
    LOG_LEVEL          = var.log_level
    TZ_OWNER           = "Asia/Kolkata"
  }
}

# ---------- identities ----------
resource "google_service_account" "api" {
  account_id   = "${var.name_prefix}-api"
  display_name = "Weekend API runtime (${var.name_prefix})"
}

resource "google_service_account" "worker" {
  account_id   = "${var.name_prefix}-worker"
  display_name = "Weekend worker runtime (${var.name_prefix})"
}

resource "google_service_account" "invoker" {
  account_id   = "${var.name_prefix}-invoker"
  display_name = "Cloud Scheduler / Cloud Tasks → worker (${var.name_prefix})"
}

# Firestore has no per-database IAM for data access; datastore.user is project-wide data read/write.
# Vertex AI publisher models have no resource-level IAM, so aiplatform.user is project-wide.
resource "google_project_iam_member" "api" {
  for_each = toset(["roles/datastore.user", "roles/aiplatform.user"])

  project = data.google_project.this.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.api.email}"
}

resource "google_project_iam_member" "worker" {
  for_each = toset(["roles/datastore.user"])

  project = data.google_project.this.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.worker.email}"
}

resource "google_secret_manager_secret_iam_member" "api" {
  for_each = var.api_secrets # static keys, so the plan works before the secrets exist

  secret_id = each.value
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.api.email}"
}

# Worker writes exports/audit copies; it cannot delete (no objectAdmin).
resource "google_storage_bucket_iam_member" "worker_backup" {
  for_each = toset(["roles/storage.objectCreator", "roles/storage.objectViewer"])

  bucket = var.backup_bucket_name
  role   = each.value
  member = "serviceAccount:${google_service_account.worker.email}"
}

# ---------- reminders: one task per reminder (no polling; max 30 days ahead, the worker re-queues) ----------
resource "google_cloud_tasks_queue" "reminders" {
  name     = "${var.name_prefix}-reminders"
  location = var.region

  rate_limits {
    max_dispatches_per_second = 1
    max_concurrent_dispatches = 2
  }

  retry_config {
    max_attempts = 5
    min_backoff  = "10s"
    max_backoff  = "300s"
  }
}

resource "google_cloud_tasks_queue_iam_member" "api_enqueue" {
  name     = google_cloud_tasks_queue.reminders.name
  location = var.region
  role     = "roles/cloudtasks.enqueuer"
  member   = "serviceAccount:${google_service_account.api.email}"
}

# The API creates tasks that carry the invoker's OIDC token, so it must be able to act as the invoker.
resource "google_service_account_iam_member" "api_actas_invoker" {
  service_account_id = google_service_account.invoker.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.api.email}"
}

# ---------- Cloud Run services ----------
resource "google_cloud_run_v2_service" "api" {
  name                = "${var.name_prefix}-api"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_INTERNAL_ONLY"
  deletion_protection = var.deletion_protection

  template {
    service_account                  = google_service_account.api.email
    timeout                          = "60s"
    max_instance_request_concurrency = 10

    scaling {
      min_instance_count = 0
      max_instance_count = var.max_instances
    }

    containers {
      image = var.image

      resources {
        limits = {
          cpu    = "1"
          memory = "${var.api_memory_mb}Mi"
        }
        cpu_idle = true # request-based billing: no charge between requests
      }

      dynamic "env" {
        for_each = local.common_env
        content {
          name  = env.key
          value = env.value
        }
      }
    }
  }

  # The app CD pipeline (.github/workflows/app-cd.yml) rolls out new images; Terraform owns everything else.
  lifecycle {
    ignore_changes = [template[0].containers[0].image, client, client_version]
  }

  depends_on = [google_project_iam_member.api]
}

resource "google_cloud_run_v2_service" "worker" {
  name                = "${var.name_prefix}-worker"
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_INTERNAL_ONLY" # Cloud Scheduler / Cloud Tasks in this project count as internal
  deletion_protection = var.deletion_protection

  template {
    service_account                  = google_service_account.worker.email
    timeout                          = "300s"
    max_instance_request_concurrency = 1

    scaling {
      min_instance_count = 0
      max_instance_count = 1
    }

    containers {
      image = var.image

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true
      }

      dynamic "env" {
        for_each = local.common_env
        content {
          name  = env.key
          value = env.value
        }
      }
    }
  }

  # The app CD pipeline (.github/workflows/app-cd.yml) rolls out new images; Terraform owns everything else.
  lifecycle {
    ignore_changes = [template[0].containers[0].image, client, client_version]
  }

  depends_on = [google_project_iam_member.worker]
}

resource "google_cloud_run_v2_service_iam_member" "worker_invoker" {
  name     = google_cloud_run_v2_service.worker.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.invoker.email}"
}
