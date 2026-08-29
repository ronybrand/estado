# Preserva o state ao mover esses 2 recursos pro module compartilhado
# private-encrypted-bucket (issue #28) - sem isso, o Terraform trataria
# como destruir + recriar os recursos reais na AWS.

moved {
  from = aws_s3_bucket_public_access_block.backups
  to   = module.backups_hardening.aws_s3_bucket_public_access_block.this
}

moved {
  from = aws_s3_bucket_server_side_encryption_configuration.backups
  to   = module.backups_hardening.aws_s3_bucket_server_side_encryption_configuration.this
}
