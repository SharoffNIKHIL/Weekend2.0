# infra/modules/secrets/main.tf
# Secret CONTAINERS only (P4), encrypted with the project CMEK and kept in one region.
# Values are never written by Terraform, so they never reach state or git. Set them out-of-band:
#   gcloud secrets versions add weekend2-dev-tailscale-authkey --data-file=authkey.txt && rm -P authkey.txt

resource "google_secret_manager_secret" "this" {
  for_each = var.secrets

  secret_id = "${var.name_prefix}-${replace(each.key, "/", "-")}"
  labels    = { purpose = replace(each.key, "/", "-") }

  annotations = {
    description = each.value
  }

  replication {
    user_managed {
      replicas {
        location = var.region

        dynamic "customer_managed_encryption" {
          for_each = var.kms_key_id == null ? [] : [var.kms_key_id]
          content {
            kms_key_name = customer_managed_encryption.value
          }
        }
      }
    }
  }
}
