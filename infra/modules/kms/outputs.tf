# infra/modules/kms/outputs.tf
output "key_arn" {
  description = "ARN of the project customer-managed KMS key."
  value       = aws_kms_key.data.arn
}

output "alias_name" {
  description = "Alias of the project KMS key."
  value       = aws_kms_alias.data.name
}
