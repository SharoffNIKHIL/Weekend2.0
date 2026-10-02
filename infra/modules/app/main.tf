# infra/modules/app/main.tf
# Two Lambda functions (Python, arm64):
#   <prefix>-api    — chat/voice/memory API, exposed ONLY via a Function URL with AWS_IAM auth
#                     (only the entry node's role can call it) and response streaming.
#   <prefix>-worker — scheduled jobs (retention, exports, one-time reminders).
# Lambda runs outside the VPC; it reaches Aurora through the RDS Data API and Claude through Bedrock.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

data "archive_file" "placeholder" {
  type        = "zip"
  source_dir  = "${path.module}/placeholder_src"
  output_path = "${path.module}/.build/placeholder.zip"
}

locals {
  account_id   = data.aws_caller_identity.current.account_id
  region       = data.aws_region.current.region
  package_path = coalesce(var.package_path, data.archive_file.placeholder.output_path)
  package_hash = var.package_path == null ? data.archive_file.placeholder.output_base64sha256 : filebase64sha256(var.package_path)

  common_env = {
    DB_CLUSTER_ARN     = var.db_cluster_arn
    DB_SECRET_ARN      = var.db_secret_arn
    DB_NAME            = var.db_name
    BEDROCK_REGION     = local.region
    MODEL_DEFAULT      = var.model_default
    MODEL_STRONG       = var.model_strong
    BACKUP_BUCKET      = var.backup_bucket_name
    SCHEDULE_GROUP     = var.schedule_group_name
    APP_SECRETS_PREFIX = "${var.name_prefix}/"
    LOG_LEVEL          = var.log_level
    TZ_OWNER           = "Asia/Kolkata"
  }
}

# ---------- shared permissions ----------
data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "data_access" {
  statement {
    sid = "RdsDataApi"
    actions = [
      "rds-data:ExecuteStatement",
      "rds-data:BatchExecuteStatement",
      "rds-data:BeginTransaction",
      "rds-data:CommitTransaction",
      "rds-data:RollbackTransaction",
    ]
    resources = [var.db_cluster_arn]
  }

  statement {
    sid       = "ReadOwnSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = concat([var.db_secret_arn], var.app_secret_arns)
  }

  statement {
    sid       = "UseProjectKey"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = [var.kms_key_arn]
  }
}

# ---------- API function ----------
resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/lambda/${var.name_prefix}-api"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

resource "aws_iam_role" "api" {
  name               = "${var.name_prefix}-api-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

data "aws_iam_policy_document" "api" {
  source_policy_documents = [data.aws_iam_policy_document.data_access.json]

  statement {
    sid       = "WriteOwnLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.api.arn}:*"]
  }

  # Claude via the India geographic cross-Region inference profiles (in.*).
  # Cross-Region profiles also need permission on the model in each destination Region.
  statement {
    sid     = "InvokeClaudeIndiaProfiles"
    actions = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
    resources = [
      "arn:aws:bedrock:${local.region}:${local.account_id}:inference-profile/in.anthropic.*",
      "arn:aws:bedrock:ap-south-1::foundation-model/anthropic.*",
      "arn:aws:bedrock:ap-south-2::foundation-model/anthropic.*",
    ]
  }

  # The API creates/deletes one-time reminder schedules (no polling, so Aurora can pause).
  statement {
    sid       = "ManageReminderSchedules"
    actions   = ["scheduler:CreateSchedule", "scheduler:DeleteSchedule", "scheduler:GetSchedule"]
    resources = ["arn:aws:scheduler:${local.region}:${local.account_id}:schedule/${var.schedule_group_name}/reminder-*"]
  }

  statement {
    sid       = "PassSchedulerRole"
    actions   = ["iam:PassRole"]
    resources = [var.scheduler_role_arn]
  }
}

resource "aws_iam_role_policy" "api" {
  name   = "${var.name_prefix}-api-policy"
  role   = aws_iam_role.api.id
  policy = data.aws_iam_policy_document.api.json
}

resource "aws_lambda_function" "api" {
  function_name    = "${var.name_prefix}-api"
  description      = "Weekend 2.0 API (chat, voice, memory)"
  role             = aws_iam_role.api.arn
  runtime          = var.python_runtime
  architectures    = ["arm64"]
  handler          = "handler.lambda_handler"
  filename         = local.package_path
  source_code_hash = local.package_hash
  memory_size      = var.api_memory_mb
  timeout          = 60
  kms_key_arn      = var.kms_key_arn

  environment {
    variables = local.common_env
  }

  logging_config {
    log_format = "JSON"
    log_group  = aws_cloudwatch_log_group.api.name
  }

  depends_on = [aws_iam_role_policy.api]
}

resource "aws_lambda_function_url" "api" {
  function_name      = aws_lambda_function.api.function_name
  authorization_type = "AWS_IAM" # unsigned requests are rejected; only allowed IAM principals can call
  # BUFFERED: Lambda streams natively only on Node.js managed runtimes. Python needs the
  # Lambda Web Adapter or a custom runtime, which is a Phase 1 decision (see infra/README.md).
  invoke_mode = "BUFFERED"
}

# ---------- worker function ----------
resource "aws_cloudwatch_log_group" "worker" {
  name              = "/aws/lambda/${var.name_prefix}-worker"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

resource "aws_iam_role" "worker" {
  name               = "${var.name_prefix}-worker-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

data "aws_iam_policy_document" "worker" {
  source_policy_documents = [data.aws_iam_policy_document.data_access.json]

  statement {
    sid       = "WriteOwnLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.worker.arn}:*"]
  }

  statement {
    sid       = "ListBackupBucket"
    actions   = ["s3:ListBucket"]
    resources = [var.backup_bucket_arn]
  }

  statement {
    sid     = "WriteBackupObjects"
    actions = ["s3:PutObject", "s3:GetObject"]
    resources = [
      "${var.backup_bucket_arn}/daily/*",
      "${var.backup_bucket_arn}/monthly/*",
      "${var.backup_bucket_arn}/audit/*",
    ]
  }

  # The bucket default lock is 35 days. monthly/ (365 d) and audit/ (730 d) objects are
  # written with a per-object retain-until date that matches their lifecycle (P8/P10).
  statement {
    sid     = "SetLongRetentionOnKeptCopies"
    actions = ["s3:PutObjectRetention"]
    resources = [
      "${var.backup_bucket_arn}/monthly/*",
      "${var.backup_bucket_arn}/audit/*",
    ]
  }
}

resource "aws_iam_role_policy" "worker" {
  name   = "${var.name_prefix}-worker-policy"
  role   = aws_iam_role.worker.id
  policy = data.aws_iam_policy_document.worker.json
}

resource "aws_lambda_function" "worker" {
  function_name    = "${var.name_prefix}-worker"
  description      = "Weekend 2.0 scheduled jobs (retention, exports, reminders)"
  role             = aws_iam_role.worker.arn
  runtime          = var.python_runtime
  architectures    = ["arm64"]
  handler          = "handler.worker_handler"
  filename         = local.package_path
  source_code_hash = local.package_hash
  memory_size      = 512
  timeout          = 300
  kms_key_arn      = var.kms_key_arn

  environment {
    variables = local.common_env
  }

  logging_config {
    log_format = "JSON"
    log_group  = aws_cloudwatch_log_group.worker.name
  }

  depends_on = [aws_iam_role_policy.worker]
}
