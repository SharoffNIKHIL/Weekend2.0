# infra/modules/budget/outputs.tf
output "budget_name" {
  description = "Budget display name."
  value       = google_billing_budget.this.display_name
}
