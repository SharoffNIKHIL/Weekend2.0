# infra/modules/budget/main.tf
# Billing budget for this project with e-mail alerts at 50/80/100% actual and 100% forecast.
# Indian billing accounts are billed in INR. Budgets track cost before tax, so the limit is set
# below the GST-inclusive target (₹5,000 ÷ 1.18 ≈ ₹4,237 for prod).

data "google_project" "this" {}

resource "google_monitoring_notification_channel" "email" {
  display_name = "${var.name_prefix} budget alerts"
  type         = "email"

  labels = {
    email_address = var.alert_email
  }
}

resource "google_billing_budget" "this" {
  billing_account = var.billing_account_id
  display_name    = "${var.name_prefix}-monthly"

  budget_filter {
    projects = ["projects/${data.google_project.this.number}"]
  }

  amount {
    specified_amount {
      currency_code = var.currency
      units         = tostring(var.amount)
    }
  }

  dynamic "threshold_rules" {
    for_each = var.actual_thresholds
    content {
      threshold_percent = threshold_rules.value
      spend_basis       = "CURRENT_SPEND"
    }
  }

  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }

  all_updates_rule {
    monitoring_notification_channels = [google_monitoring_notification_channel.email.id]
  }
}
