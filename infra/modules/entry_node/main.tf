# infra/modules/entry_node/main.tf
# The only server: a t4g.nano that joins the tailnet and forwards requests to the
# API Function URL, signing them with its instance role. No inbound ports at all;
# admin access is via SSM Session Manager (no SSH key, no port 22).

data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_security_group" "entry" {
  name        = "${var.name_prefix}-entry-sg"
  description = "Entry node: NO inbound; egress only for Tailscale and AWS APIs"
  vpc_id      = var.vpc_id

  tags = { Name = "${var.name_prefix}-entry-sg" }
}

resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.entry.id
  description       = "HTTPS: AWS APIs, Tailscale control plane, package repos"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "wireguard" {
  security_group_id = aws_security_group.entry.id
  description       = "Tailscale WireGuard (direct peer connections)"
  ip_protocol       = "udp"
  from_port         = 41641
  to_port           = 41641
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "stun" {
  security_group_id = aws_security_group.entry.id
  description       = "Tailscale STUN (NAT traversal)"
  ip_protocol       = "udp"
  from_port         = 3478
  to_port           = 3478
  cidr_ipv4         = "0.0.0.0/0"
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "entry" {
  name               = "${var.name_prefix}-entry-role"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.entry.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "entry" {
  statement {
    sid       = "ReadTailscaleAuthKey"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [var.tailscale_secret_arn]
  }

  statement {
    sid       = "DecryptWithProjectKey"
    actions   = ["kms:Decrypt"]
    resources = [var.kms_key_arn]
  }

  # Since Oct 2025 an AWS_IAM Function URL needs BOTH actions. Each one uses its own
  # documented condition key (AWS Lambda docs: urls-auth). InvokedViaFunctionUrl stops
  # this role from calling the plain Invoke API and skipping the URL path.
  statement {
    sid       = "CallApiFunctionUrl"
    actions   = ["lambda:InvokeFunctionUrl"]
    resources = [var.api_function_arn]

    condition {
      test     = "StringEquals"
      variable = "lambda:FunctionUrlAuthType"
      values   = ["AWS_IAM"]
    }
  }

  statement {
    sid       = "InvokeApiOnlyViaFunctionUrl"
    actions   = ["lambda:InvokeFunction"]
    resources = [var.api_function_arn]

    condition {
      test     = "Bool"
      variable = "lambda:InvokedViaFunctionUrl"
      values   = ["true"]
    }
  }
}

resource "aws_iam_role_policy" "entry" {
  name   = "${var.name_prefix}-entry-policy"
  role   = aws_iam_role.entry.id
  policy = data.aws_iam_policy_document.entry.json
}

resource "aws_iam_instance_profile" "entry" {
  name = "${var.name_prefix}-entry-profile"
  role = aws_iam_role.entry.name
}

resource "aws_instance" "this" {
  ami                         = data.aws_ssm_parameter.al2023_arm64.value
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [aws_security_group.entry.id]
  associate_public_ip_address = true # egress only; the security group has no inbound rules
  iam_instance_profile        = aws_iam_instance_profile.entry.name
  monitoring                  = false

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    hostname            = var.tailnet_hostname
    region              = var.region
    tailscale_secret_id = var.tailscale_secret_arn
    tailscale_tag       = var.tailscale_tag
  })

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required" # IMDSv2 only
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    kms_key_id            = var.kms_key_arn
    delete_on_termination = true
  }

  credit_specification {
    cpu_credits = "standard" # avoid 'unlimited' burst surcharges
  }

  tags = { Name = "${var.name_prefix}-entry" }

  lifecycle {
    ignore_changes = [ami, user_data] # AMI updates are rolled out deliberately, not on every plan
  }

  depends_on = [aws_iam_role_policy.entry]
}
