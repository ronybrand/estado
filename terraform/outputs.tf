output "public_ip" {
  value = module.portfolio.public_ip
}

# Fase 5.1 do plano de migracao (docs/plano-dominio-proprio.md) - instancia
# nova em us-east-1, sem trafego ainda.
output "public_ip_us_east_1" {
  value = module.portfolio_us_east_1.public_ip
}

output "instance_id_us_east_1" {
  value = module.portfolio_us_east_1.instance_id
}

output "backup_bucket" {
  value = module.estado_backup.bucket_name
}

output "cloudtrail_bucket" {
  value = aws_s3_bucket.cloudtrail.bucket
}

output "frontend_bucket" {
  value = module.estado_frontend.bucket_name
}

output "frontend_cloudfront_domain" {
  value = module.estado_frontend.distribution_domain_name
}

output "frontend_cloudfront_distribution_id" {
  value = module.estado_frontend.distribution_id
}

output "frontend_deploy_role_arn" {
  value = module.estado_frontend_deploy.role_arn
}

output "terraform_state_bucket" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "terraform_plan_role_arn" {
  value = module.estado_terraform_plan.role_arn
}
