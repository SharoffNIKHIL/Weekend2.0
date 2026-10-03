# infra/bootstrap/deployer.tf
# Least-privilege Terraform DEPLOYER for this environment's project.
#   - No Owner/Editor. One admin role per service the stack manages (infra/stack + modules).
#   - Project IAM admin only through an IAM Condition: it can grant ONLY the roles the stack gives
#     its own runtime service accounts (no privilege escalation to Owner/Editor/IAM admin).
#   - No keys: the owner impersonates it (1-hour access tokens, roles/iam.serviceAccountTokenCreator
#     on THIS service account only); CI reaches it through Workload Identity Federation from the
#     "<env>-apply" GitHub environment on the <env> branch only.

locals {
  deployer_roles = [
    "roles/artifactregistry.admin",               # module registry
    "roles/cloudscheduler.admin",                 # module scheduler
    "roles/cloudtasks.admin",                     # module app (queue + queue IAM)
    "roles/compute.instanceAdmin.v1",             # module entry_node (off by default)
    "roles/compute.networkAdmin",                 # module network
    "roles/compute.securityAdmin",                # module network (IAP-SSH firewall rule)
    "roles/datastore.owner",                      # module database (Firestore + backup schedule)
    "roles/iam.serviceAccountAdmin",              # runtime service accounts + SA-level IAM
    "roles/iam.serviceAccountUser",               # Cloud Run / VM run as those service accounts
    "roles/monitoring.notificationChannelEditor", # module budget (e-mail channel)
    "roles/run.admin",                            # module app (Cloud Run + run.invoker)
    "roles/secretmanager.admin",                  # module secrets (containers + accessor IAM)
    "roles/serviceusage.serviceUsageConsumer",    # user_project_override quota billing
    "roles/storage.admin",                        # module backup + state bucket objects
  ]

  # The only project-level roles infra/modules grant (app: api/worker; entry_node: node).
  grantable_roles = [
    "roles/aiplatform.user",
    "roles/datastore.user",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
  ]
}

resource "google_service_account" "deployer" {
  account_id   = "${local.prefix}-tf"
  display_name = "Terraform deployer (${var.env}, least privilege)"
  description  = "Owner impersonation (1 h tokens) and GitHub CD via WIF. No keys."
}

resource "google_project_iam_member" "deployer" {
  for_each = toset(local.deployer_roles)

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.deployer.email}"
}

resource "google_project_iam_member" "deployer_iam_limited" {
  project = var.project_id
  role    = "roles/resourcemanager.projectIamAdmin"
  member  = "serviceAccount:${google_service_account.deployer.email}"

  condition {
    title       = "only-stack-roles"
    description = "May only grant the roles the Weekend stack gives its own service accounts"
    expression  = "api.getAttribute('iam.googleapis.com/modifiedGrantsByRole', []).hasOnly(${jsonencode(local.grantable_roles)})"
  }
}

# Budgets live on the billing account, not the project.
resource "google_billing_account_iam_member" "deployer_budgets" {
  count = var.billing_account_id == null ? 0 : 1

  billing_account_id = var.billing_account_id
  role               = "roles/billing.costsManager"
  member             = "serviceAccount:${google_service_account.deployer.email}"
}

# Read the CMEK key only when CMEK is on (data source in infra/stack).
resource "google_kms_crypto_key_iam_member" "deployer_viewer" {
  count = var.enable_cmek ? 1 : 0

  crypto_key_id = google_kms_crypto_key.data[0].id
  role          = "roles/cloudkms.viewer"
  member        = "serviceAccount:${google_service_account.deployer.email}"
}

# Owner → deployer: short-lived tokens via impersonation (gcloud --impersonate-service-account).
resource "google_service_account_iam_member" "deployer_owner_impersonation" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = var.owner_principal
}

# GitHub CD → deployer: only jobs in the "<env>-apply" environment running on refs/heads/<env>.
resource "google_service_account_iam_member" "deployer_wif" {
  service_account_id = google_service_account.deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.env_ref/${var.env}-apply@refs/heads/${var.env}"
}
