# infra/modules/scheduler/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-dev."
  type        = string
}

variable "region" {
  description = "Cloud Scheduler region."
  type        = string
}

variable "worker_url" {
  description = "Worker Cloud Run URL."
  type        = string
}

variable "invoker_service_account" {
  description = "Service account whose OIDC token calls the worker."
  type        = string
}

variable "daily_jobs" {
  description = "Map of job name => unix cron (evaluated in Asia/Kolkata)."
  type        = map(string)
  default = {
    retention = "0 3 * * *"  # 03:00 IST — delete data past its retention (P5)
    export    = "30 3 * * *" # 03:30 IST — encrypted export to GCS (P6/P10)
  }
}
