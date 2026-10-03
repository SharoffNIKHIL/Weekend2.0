# infra/values/dev.bootstrap.tfvars — DEV bootstrap values (dev branch only). Identifying values
# (project_id, billing_account_id, owner_principal) come from ../credentials/<project>-dev.secrets.tfvars.
env            = "dev"
enable_cmek    = false         # on hold: KMS costs
state_location = "us-central1" # 5 GB Standard storage free
