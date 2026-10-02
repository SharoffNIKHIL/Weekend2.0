# infra/modules/network/outputs.tf
output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.this.id
}

output "public_subnet_id" {
  description = "Public subnet ID for the entry node."
  value       = aws_subnet.public_a.id
}

output "private_subnet_ids" {
  description = "Private subnet IDs for Aurora."
  value       = [for s in aws_subnet.private : s.id]
}
