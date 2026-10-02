# infra/modules/secrets/main.tf
# Secret CONTAINERS only (P4). Values are never written by Terraform, so they never
# reach state or git. Set them out-of-band, e.g.:
#   aws secretsmanager put-secret-value --secret-id weekend2-prod/tailscale/authkey \
#     --secret-string file://authkey.txt   # then shred the file

resource "aws_secretsmanager_secret" "this" {
  for_each = var.secrets

  name                    = "${var.name_prefix}/${each.key}"
  description             = each.value
  kms_key_id              = var.kms_key_arn
  recovery_window_in_days = var.recovery_window_in_days
}
