# infra/modules/entry_node/outputs.tf
output "instance_id" {
  description = "Entry node instance ID (use with: aws ssm start-session --target <id>)."
  value       = aws_instance.this.id
}

output "role_arn" {
  description = "Entry node IAM role ARN."
  value       = aws_iam_role.entry.arn
}
