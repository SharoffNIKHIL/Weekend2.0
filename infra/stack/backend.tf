# infra/stack/backend.tf
# Partial configuration — one state per environment, in that environment's bootstrap bucket:
#   terraform init -backend-config="bucket=<PROJECT_ID>-tfstate" -backend-config="prefix=stack/<env>"
# infra/scripts/tf.py and the CI workflows pass these; nothing environment-specific is stored here.
terraform {
  backend "gcs" {}
}
