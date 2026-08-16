output "public_ip" {
  value = module.portfolio.public_ip
}

output "backup_bucket" {
  value = module.estado_backup.bucket_name
}
