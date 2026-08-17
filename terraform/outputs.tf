output "public_ip" {
  value = module.portfolio.public_ip
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
