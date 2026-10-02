# infra/modules/scheduler/variables.tf
variable "name_prefix" {
  description = "Prefix for resource names, e.g. weekend2-prod."
  type        = string
}

variable "worker_function_arn" {
  description = "Worker Lambda ARN invoked by the schedules."
  type        = string
}

variable "daily_jobs" {
  description = "Map of job name => cron expression (evaluated in Asia/Kolkata)."
  type        = map(string)
  default = {
    retention = "cron(0 3 * * ? *)"  # 03:00 IST — delete data past its retention (P5)
    export    = "cron(30 3 * * ? *)" # 03:30 IST — encrypted export to S3 (P6/P10)
  }
}
