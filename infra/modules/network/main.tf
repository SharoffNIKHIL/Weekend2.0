# infra/modules/network/main.tf
# One custom VPC with one subnet for the entry node. The node has NO external IP: it reaches
# the internet (Tailscale) through Cloud NAT and Google APIs through Private Google Access.
# Cloud Run and Firestore are serverless and need no subnet. No inbound rules except
# optional SSH from Google's IAP range (admin access without a public IP).

resource "google_compute_network" "this" {
  name                    = "${var.name_prefix}-vpc"
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
}

resource "google_compute_subnetwork" "node" {
  name                     = "${var.name_prefix}-node"
  network                  = google_compute_network.this.id
  region                   = var.region
  ip_cidr_range            = var.subnet_cidr
  private_ip_google_access = true
}

resource "google_compute_router" "this" {
  name    = "${var.name_prefix}-router"
  network = google_compute_network.this.id
  region  = var.region
}

resource "google_compute_router_nat" "this" {
  name                                = "${var.name_prefix}-nat"
  router                              = google_compute_router.this.name
  region                              = var.region
  nat_ip_allocate_option              = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat  = "LIST_OF_SUBNETWORKS"
  enable_endpoint_independent_mapping = true # helps Tailscale make direct (non-relayed) connections

  subnetwork {
    name                    = google_compute_subnetwork.node.id
    source_ip_ranges_to_nat = ["ALL_IP_RANGES"]
  }

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}

# Admin SSH only through IAP TCP forwarding (gcloud compute ssh --tunnel-through-iap).
resource "google_compute_firewall" "iap_ssh" {
  count = var.allow_iap_ssh ? 1 : 0

  name          = "${var.name_prefix}-allow-iap-ssh"
  network       = google_compute_network.this.id
  direction     = "INGRESS"
  priority      = 1000
  source_ranges = ["35.235.240.0/20"] # Google IAP TCP forwarding range
  target_tags   = [var.node_tag]

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}
