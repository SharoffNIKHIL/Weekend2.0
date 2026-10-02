# infra/modules/app/outputs.tf
output "api_function_arn" {
  description = "API Lambda ARN."
  value       = aws_lambda_function.api.arn
}

output "api_function_url" {
  description = "API Function URL (AWS_IAM auth — unsigned calls get 403)."
  value       = aws_lambda_function_url.api.function_url
}

output "worker_function_arn" {
  description = "Worker Lambda ARN."
  value       = aws_lambda_function.worker.arn
}
