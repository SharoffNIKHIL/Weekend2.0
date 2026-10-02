# infra/envs/prod/outputs.tf
output "api_function_url" {
  description = "API Function URL (IAM-only; reachable from the entry node's role)."
  value       = module.app.api_function_url
}

output "aurora_cluster_arn" {
  description = "Aurora cluster ARN."
  value       = module.database.cluster_arn
}

output "entry_node_instance_id" {
  description = "Entry node instance ID (aws ssm start-session --target <id>)."
  value       = module.entry_node.instance_id
}

output "backup_bucket" {
  description = "Backup bucket name."
  value       = module.backup.bucket_name
}

output "kms_key_alias" {
  description = "Project KMS key alias."
  value       = module.kms.alias_name
}

output "secret_arns" {
  description = "Secret container ARNs (set values out-of-band)."
  value       = module.secrets.arns
}
