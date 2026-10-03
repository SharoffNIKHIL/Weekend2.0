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

        customer_managed_encryption {
          kms_key_name = var.kms_key_id
        }
      }
    }
  }
}
