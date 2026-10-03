# infra/modules/network/outputs.tf
output "network_id" {
  description = "VPC ID."
  value       = google_compute_network.this.id
}

output "subnet_id" {
  description = "Entry-node subnet ID."
  value       = google_compute_subnetwork.node.id
}

output "node_tag" {
  description = "Network tag for the entry node."
  value       = var.node_tag
}
