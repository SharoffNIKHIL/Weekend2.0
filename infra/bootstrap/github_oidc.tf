# infra/bootstrap/github_oidc.tf
# Lets GitHub Actions run `terraform plan` for dev WITHOUT long-lived AWS keys.
# Only workflow jobs that run in this repo's `dev` environment can assume the role (fork PRs
# get no OIDC token). The role is read-only: it can plan, never apply.
# ⚠️ IAM change — apply only after the security checkpoint.

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "github_plan_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:dev"]
    }
  }
}

resource "aws_iam_role" "github_plan_dev" {
  name                 = "${var.project_name}-github-plan-dev"
  description          = "Read-only role for GitHub Actions terraform plan (dev environment only)"
  assume_role_policy   = data.aws_iam_policy_document.github_plan_assume.json
  max_session_duration = 3600
}

# ReadOnlyAccess covers the Describe/Get/List calls a plan makes. It does NOT include
# secretsmanager:GetSecretValue, so secret values stay unreadable.
resource "aws_iam_role_policy_attachment" "github_plan_readonly" {
  role       = aws_iam_role.github_plan_dev.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# The state bucket is SSE-KMS with the AWS-managed key; reading state needs Decrypt via S3.
data "aws_iam_policy_document" "github_plan_state" {
  statement {
    sid       = "ReadDevState"
    actions   = ["s3:GetObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.tfstate.arn, "${aws_s3_bucket.tfstate.arn}/dev/*"]
  }

  statement {
    sid       = "DecryptStateViaS3"
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["s3.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_plan_state" {
  name   = "${var.project_name}-github-plan-dev-state"
  role   = aws_iam_role.github_plan_dev.id
  policy = data.aws_iam_policy_document.github_plan_state.json
}

output "github_plan_dev_role_arn" {
  description = "Set as the `dev` environment secret AWS_PLAN_ROLE_ARN in GitHub."
  value       = aws_iam_role.github_plan_dev.arn
}
