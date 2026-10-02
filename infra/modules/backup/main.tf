# infra/modules/backup/main.tf
# Bucket for data exports (P6), audit-log copies (P8) and restore drills (P10).
# Object Lock (governance mode): the bucket default (35 days) covers daily/ copies. The
# worker sets a per-object retain-until date on monthly/ (365 d) and audit/ (730 d), so
# those stay locked for their whole lifecycle. P6 purges of locked versions are an
# owner-only admin action with --bypass-governance-retention (see infra/README.md).

resource "aws_s3_bucket" "this" {
  bucket              = "${var.name_prefix}-backups-${var.account_id}"
  object_lock_enabled = true
}

resource "aws_s3_bucket_versioning" "this" {
  bucket = aws_s3_bucket.this.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_object_lock_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    default_retention {
      mode = "GOVERNANCE"
      days = var.object_lock_days
    }
  }

  depends_on = [aws_s3_bucket_versioning.this]
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket                  = aws_s3_bucket.this.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    id     = "daily-exports"
    status = "Enabled"

    filter {
      prefix = "daily/"
    }

    expiration {
      days = 35
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }

  rule {
    id     = "monthly-exports"
    status = "Enabled"

    filter {
      prefix = "monthly/"
    }

    expiration {
      days = 365
    }

    noncurrent_version_expiration {
      noncurrent_days = 30 # an overwrite stays recoverable for 30 days
    }
  }

  rule {
    id     = "audit-log-copies"
    status = "Enabled"

    filter {
      prefix = "audit/"
    }

    expiration {
      days = 730
    }

    noncurrent_version_expiration {
      noncurrent_days = 30 # an overwrite stays recoverable for 30 days
    }
  }
}

data "aws_iam_policy_document" "tls_only" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.this.arn,
      "${aws_s3_bucket.this.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "this" {
  bucket = aws_s3_bucket.this.id
  policy = data.aws_iam_policy_document.tls_only.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}
