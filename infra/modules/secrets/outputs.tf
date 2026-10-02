# infra/modules/secrets/outputs.tf
output "arns" {
  description = "Map of secret path suffix => secret ARN."
  value       = { for k, s in aws_secretsmanager_secret.this : k => s.arn }
}
