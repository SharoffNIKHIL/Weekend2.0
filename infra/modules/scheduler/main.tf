# infra/modules/scheduler/main.tf
# Daily jobs in IST. Reminders are NOT polled: the API creates one-time schedules
# ("reminder-<id>") in this group, so Aurora can stay paused between uses.

data "aws_caller_identity" "current" {}

resource "aws_scheduler_schedule_group" "this" {
  name = var.name_prefix
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "${var.name_prefix}-scheduler-role"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

data "aws_iam_policy_document" "invoke_worker" {
  statement {
    actions   = ["lambda:InvokeFunction"]
    resources = [var.worker_function_arn, "${var.worker_function_arn}:*"]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "${var.name_prefix}-scheduler-invoke-worker"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.invoke_worker.json
}

resource "aws_scheduler_schedule" "daily" {
  for_each = var.daily_jobs

  name                         = "${var.name_prefix}-${each.key}-daily"
  group_name                   = aws_scheduler_schedule_group.this.name
  schedule_expression          = each.value
  schedule_expression_timezone = "Asia/Kolkata"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = var.worker_function_arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ job = each.key })

    retry_policy {
      maximum_retry_attempts = 2
    }
  }
}
