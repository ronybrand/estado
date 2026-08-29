# Preserva o state ao mover esses 4 recursos (2 por bucket) pro module
# compartilhado private-encrypted-bucket (issue #28) - sem isso, o
# Terraform trataria como destruir + recriar os recursos reais na AWS.

moved {
  from = aws_s3_bucket_public_access_block.frontend
  to   = module.frontend_hardening.aws_s3_bucket_public_access_block.this
}

moved {
  from = aws_s3_bucket_server_side_encryption_configuration.frontend
  to   = module.frontend_hardening.aws_s3_bucket_server_side_encryption_configuration.this
}

moved {
  from = aws_s3_bucket_public_access_block.frontend_logs
  to   = module.frontend_logs_hardening.aws_s3_bucket_public_access_block.this
}

moved {
  from = aws_s3_bucket_server_side_encryption_configuration.frontend_logs
  to   = module.frontend_logs_hardening.aws_s3_bucket_server_side_encryption_configuration.this
}
