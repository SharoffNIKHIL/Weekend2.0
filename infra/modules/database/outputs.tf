# infra/modules/database/outputs.tf
output "cluster_arn" {
  description = "Aurora cluster ARN (used by the RDS Data API)."
  value       = aws_rds_cluster.this.arn
}

output "master_secret_arn" {
  description = "ARN of the RDS-managed master-user secret."
  value       = aws_rds_cluster.this.master_user_secret[0].secret_arn
}

output "database_name" {
  description = "Database name."
  value       = aws_rds_cluster.this.database_name
}
