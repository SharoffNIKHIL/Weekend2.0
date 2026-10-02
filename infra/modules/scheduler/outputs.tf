# infra/modules/scheduler/outputs.tf
output "group_name" {
  description = "Schedule group name."
  value       = aws_scheduler_schedule_group.this.name
}

output "role_arn" {
  description = "Scheduler execution role ARN (passed when creating reminder schedules)."
  value       = aws_iam_role.scheduler.arn
}
