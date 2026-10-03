# infra/modules/entry_node/main.tf
# The only server: an e2-micro that joins the tailnet and forwards requests to the internal
# Cloud Run API, attaching a Google ID token from its own service account. Egress via Cloud NAT
# (default) or an egress-only external IP; admin via IAP SSH + OS Login. Shielded VM.

resource "google_service_account" "entry" {
  account_id   = "${var.name_prefix}-entry"
  display_name = "Weekend entry node (${var.name_prefix})"
}

resource "google_project_iam_member" "entry" {
  for_each = toset(["roles/logging.logWriter", "roles/monitoring.metricWriter"])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.entry.email}"
}

resource "google_secret_manager_secret_iam_member" "authkey" {
  secret_id = var.tailscale_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.entry.email}"
}

resource "google_cloud_run_v2_service_iam_member" "invoke_api" {
  name     = var.api_service_name
  location = var.api_region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.entry.email}"
}

resource "google_compute_instance" "this" {
  name                      = "${var.name_prefix}-entry"
  machine_type              = var.machine_type
  zone                      = var.zone
  tags                      = [var.node_tag]
  allow_stopping_for_update = true
  deletion_protection       = var.deletion_protection

  boot_disk {
    auto_delete       = true
    kms_key_self_link = var.disk_kms_key_id # null = Google-managed key (dev only, no personal data)

    initialize_params {
      image = var.image
      size  = var.disk_gb
      type  = var.disk_type
    }
  }

  network_interface {
    subnetwork = var.subnet_id

    # Egress-only external IP when the env doesn't use Cloud NAT. No inbound firewall rules
    # exist except SSH from Google's IAP range, so the IP accepts nothing else.
    dynamic "access_config" {
      for_each = var.external_ip ? [1] : []
      content {
        network_tier = "STANDARD"
      }
    }
  }

  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }

  metadata = {
    enable-oslogin         = "TRUE"
    block-project-ssh-keys = "TRUE"
  }

  metadata_startup_script = templatefile("${path.module}/startup.sh.tftpl", {
    hostname      = var.tailnet_hostname
    secret_id     = var.tailscale_secret_id
    tailscale_tag = var.tailscale_tag
  })

  service_account {
    email  = google_service_account.entry.email
    scopes = ["cloud-platform"] # access is limited by IAM roles above
  }

  scheduling {
    provisioning_model = "STANDARD"
    automatic_restart  = true
  }

  lifecycle {
    ignore_changes = [boot_disk[0].initialize_params[0].image] # image updates are rolled out deliberately
  }

  depends_on = [google_secret_manager_secret_iam_member.authkey]
}
