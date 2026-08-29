# Par de hardening padrao usado em todo bucket privado deste projeto -
# bloqueia qualquer acesso publico e forca SSE-S3 (AES256). Antes deste
# module, esse par estava copiado verbatim em 3 lugares (app-backup,
# frontend-static x2) - ver issue #28.

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = var.bucket_id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = var.bucket_id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
