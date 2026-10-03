# infra/bootstrap/main.tf
# One-time, per environment project (local state, run by the owner):
#   1. enable the APIs the stack needs
#   2. Terraform state bucket (GCS: versioned, private; native state locking)
#   3. OPTIONAL (enable_cmek, small monthly cost): the project's CMEK key ring + key (P3).
#      GCP key rings can never be deleted, so they live here, outside the destroyable env stack
#   4. OPTIONAL (enable_cmek): let Google service agents (Firestore, Secret Manager, Storage,
#      Compute) use that key
#   5. GitHub Actions → GCP via Workload Identity Federation: a read-only plan service account,
#      usable only from this repo's matching GitHub environment. No JSON keys anywhere.
#   6. Least-privilege DEPLOYER service account <prefix>-tf (deployer.tf): used by the owner through
#      impersonation (1-hour tokens) and by the CD workflow from the <env> branch only.

provider "google" {
  project               = var.project_id
  region                = var.region
  user_project_override = true
  billing_project       = var.project_id

  default_labels = {
    owner      = var.owner
    project    = "personal-ai-agent"
    env        = var.env
    managed_by = "terraform"
  }
}

provider "google-beta" {
  project               = var.project_id
  region                = var.region
  user_project_override = true
  billing_project       = var.project_id
}

# Only needed to name the Compute service agent for CMEK; skipped otherwise so the very first
# plan works before the Cloud Resource Manager API is enabled.
data "google_project" "this" {
  count = var.enable_cmek ? 1 : 0
}

locals {
  services = [
    "aiplatform.googleapis.com",
    "artifactregistry.googleapis.com",
    "cloudbilling.googleapis.com",
    "billingbudgets.googleapis.com",
    "cloudkms.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "cloudscheduler.googleapis.com",
    "cloudtasks.googleapis.com",
    "compute.googleapis.com",
    "firestore.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "iap.googleapis.com",
    "logging.googleapis.com",
    "monitoring.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "serviceusage.googleapis.com",
    "storage.googleapis.com",
    "sts.googleapis.com",
  ]
  prefix = "${var.project_name}-${var.env}"
}

resource "google_project_service" "this" {
  for_each = toset(local.services)

  service            = each.value
  disable_on_destroy = false
}

# ---------- Terraform state ----------
resource "google_storage_bucket" "tfstate" {
  name                        = "${var.project_id}-tfstate"
  location                    = coalesce(var.state_location, var.region)
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      days_since_noncurrent_time = 90
    }
    action {
      type = "Delete"
    }
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.this]
}

# ---------- CMEK (P3) ----------
resource "google_kms_key_ring" "this" {
  count = var.enable_cmek ? 1 : 0

  name     = local.prefix
  location = var.region

  depends_on = [google_project_service.this]
}

resource "google_kms_crypto_key" "data" {
  count = var.enable_cmek ? 1 : 0

  name            = "data"
  key_ring        = google_kms_key_ring.this[0].id
  purpose         = "ENCRYPT_DECRYPT"
  rotation_period = "31536000s" # 365 days

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_project_service_identity" "this" {
  provider = google-beta
  for_each = var.enable_cmek ? toset(["firestore.googleapis.com", "secretmanager.googleapis.com"]) : toset([])

  service = each.value

  depends_on = [google_project_service.this]
}

data "google_storage_project_service_account" "this" {
  count = var.enable_cmek ? 1 : 0

  depends_on = [google_project_service.this]
}

locals {
  key_users = var.enable_cmek ? {
    firestore     = "serviceAccount:${google_project_service_identity.this["firestore.googleapis.com"].email}"
    secretmanager = "serviceAccount:${google_project_service_identity.this["secretmanager.googleapis.com"].email}"
    storage       = "serviceAccount:${data.google_storage_project_service_account.this[0].email_address}"
    compute       = "serviceAccount:service-${data.google_project.this[0].number}@compute-system.iam.gserviceaccount.com"
  } : {}
}

resource "google_kms_crypto_key_iam_member" "service_agents" {
  for_each = local.key_users

  crypto_key_id = google_kms_crypto_key.data[0].id
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = each.value
}

# ---------- GitHub Actions → read-only plan (Workload Identity Federation) ----------
resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"
  description               = "GitHub Actions OIDC for ${var.github_repository}"

  depends_on = [google_project_service.this]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-oidc"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"        = "assertion.sub"
    "attribute.repository"  = "assertion.repository"
    "attribute.environment" = "assertion.environment"
    "attribute.ref"         = "assertion.ref"
    "attribute.env_ref"     = "assertion.environment + '@' + assertion.ref"
  }
  # Only this repo, only jobs running in the matching GitHub environments (fork PRs get no token):
  # "<env>" = read-only plan on PRs; "<env>-apply" = CD after the owner approves the deployment.
  attribute_condition = "assertion.repository == '${var.github_repository}' && (assertion.environment == '${var.env}' || assertion.environment == '${var.env}-apply')"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_service_account" "github_plan" {
  account_id   = "${local.prefix}-gh-plan"
  display_name = "GitHub Actions terraform plan (${var.env}, read-only)"
}

# Viewer reads resource config but not secret payloads or object contents; securityReviewer
# reads IAM policies (needed to plan *_iam_member resources).
resource "google_project_iam_member" "github_plan" {
  for_each = toset(["roles/viewer", "roles/iam.securityReviewer"])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.github_plan.email}"
}

# Read state (CI plans run with -lock=false, so no write access is needed).
resource "google_storage_bucket_iam_member" "github_plan_state" {
  bucket = google_storage_bucket.tfstate.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.github_plan.email}"
}

resource "google_billing_account_iam_member" "github_plan_budgets" {
  count = var.billing_account_id == null ? 0 : 1

  billing_account_id = var.billing_account_id
  role               = "roles/billing.viewer"
  member             = "serviceAccount:${google_service_account.github_plan.email}"
}

resource "google_service_account_iam_member" "github_plan_wif" {
  service_account_id = google_service_account.github_plan.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}
