# infra/modules/database/main.tf
# Aurora PostgreSQL Serverless v2 that pauses at 0 ACU when idle (pay only for storage).
# First request after a pause takes ~15 s (30 s+ after 24 h idle) — the app must retry.
# Access is ONLY via the RDS Data API (HTTPS + IAM); the security group has no ingress.

resource "aws_db_subnet_group" "this" {
  name        = "${var.name_prefix}-aurora-subnets"
  description = "Private subnets for ${var.name_prefix} Aurora"
  subnet_ids  = var.subnet_ids
}

resource "aws_security_group" "aurora" {
  name        = "${var.name_prefix}-aurora-sg"
  description = "Aurora: no inbound rules; access only through the RDS Data API"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name_prefix}-aurora-sg" }
}

resource "aws_rds_cluster" "this" {
  cluster_identifier = "${var.name_prefix}-aurora"
  engine             = "aurora-postgresql"
  engine_mode        = "provisioned" # required for Serverless v2
  engine_version     = var.engine_version
  database_name      = var.database_name
  master_username    = var.master_username

  # RDS creates and rotates the master password in Secrets Manager — never in state.
  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.kms_key_arn

  storage_encrypted      = true
  kms_key_id             = var.kms_key_arn
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.aurora.id]
  enable_http_endpoint   = true # RDS Data API

  backup_retention_period      = var.backup_retention_days
  preferred_backup_window      = "21:00-21:30"         # UTC = 02:30–03:00 IST
  preferred_maintenance_window = "sun:21:30-sun:22:00" # UTC = Mon 03:00–03:30 IST
  copy_tags_to_snapshot        = true
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = var.skip_final_snapshot
  final_snapshot_identifier    = var.skip_final_snapshot ? null : "${var.name_prefix}-aurora-final"

  serverlessv2_scaling_configuration {
    min_capacity             = 0
    max_capacity             = var.max_acu
    seconds_until_auto_pause = var.seconds_until_auto_pause
  }
}

resource "aws_rds_cluster_instance" "writer" {
  identifier                   = "${var.name_prefix}-aurora-1"
  cluster_identifier           = aws_rds_cluster.this.id
  instance_class               = "db.serverless"
  engine                       = aws_rds_cluster.this.engine
  engine_version               = aws_rds_cluster.this.engine_version
  publicly_accessible          = false
  auto_minor_version_upgrade   = true
  performance_insights_enabled = false
}
