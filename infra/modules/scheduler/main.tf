# infra/modules/scheduler/main.tf
# Daily jobs in IST call the worker with an OIDC token. Reminders are NOT polled: the API
# creates one Cloud Task per reminder. Cloud Scheduler gives 3 free jobs per billing account.

resource "google_cloud_scheduler_job" "daily" {
  for_each = var.daily_jobs

  name             = "${var.name_prefix}-${each.key}-daily"
  region           = var.region
  schedule         = each.value
  time_zone        = "Asia/Kolkata"
  attempt_deadline = "320s"

  retry_config {
    retry_count = 2
  }

  http_target {
    http_method = "POST"
    uri         = "${var.worker_url}/jobs/${each.key}"
    body        = base64encode(jsonencode({ job = each.key }))
    headers     = { "Content-Type" = "application/json" }

    oidc_token {
      service_account_email = var.invoker_service_account
      audience              = var.worker_url
    }
  }
}
