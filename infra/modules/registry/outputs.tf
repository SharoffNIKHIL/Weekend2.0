# infra/modules/registry/outputs.tf
output "repository_url" {
  description = "Docker push/pull prefix: <region>-docker.pkg.dev/<project>/<repo>."
  value       = "${google_artifact_registry_repository.app.location}-docker.pkg.dev/${google_artifact_registry_repository.app.project}/${google_artifact_registry_repository.app.repository_id}"
}
