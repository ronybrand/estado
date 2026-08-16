output "public_ip" {
  value = module.portfolio.public_ip
}

output "backup_bucket" {
  value = module.estado_backup.bucket_name
}

output "cloudtrail_bucket" {
  value = aws_s3_bucket.cloudtrail.bucket
}
