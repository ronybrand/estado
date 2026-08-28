# Bucket S3 dedicado ao state remoto do Terraform - ver ADR 0015. Precisa ser
# criado e aplicado ANTES do bloco `backend "s3"` em versions.tf (problema de
# bootstrap classico: o bucket que guarda o state nao pode ele mesmo depender
# desse state). Ordem de migracao: 1) aplicar este arquivo com state local
# (backend default); 2) so entao adicionar/habilitar o bloco `backend "s3"`
# em versions.tf e rodar `terraform init -migrate-state`.

resource "aws_s3_bucket" "terraform_state" {
  bucket = "estado-terraform-state-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name = "estado-terraform-state"
  }
}

resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Versionamento e a rede de seguranca contra um state corrompido/sobrescrito -
# mais critico aqui do que nos outros buckets do projeto, porque o state e a
# unica fonte da verdade de quais recursos reais o Terraform gerencia.
resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  depends_on = [aws_s3_bucket_versioning.terraform_state]
}

# Mesmo padrao de deny explicito ja usado em modules/frontend-static - aqui
# ainda mais critico, por ser o bucket mais sensivel do projeto (guarda o
# state, que pode conter atributos sensiveis dos recursos gerenciados).
data "aws_iam_policy_document" "terraform_state_bucket_policy" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.terraform_state.arn, "${aws_s3_bucket.terraform_state.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  policy = data.aws_iam_policy_document.terraform_state_bucket_policy.json
}
