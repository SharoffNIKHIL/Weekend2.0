# infra/.tflint.hcl — used by infra-ci (tflint --recursive --config "$PWD/infra/.tflint.hcl")
config {
  call_module_type = "local"
}

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

plugin "google" {
  enabled = true
  version = "0.40.0"
  source  = "github.com/terraform-linters/tflint-ruleset-google"
}
