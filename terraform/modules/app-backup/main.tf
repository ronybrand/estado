# Bucket de backup write-only + role/profile IAM que uma instancia EC2 assume
# pra so conseguir gravar nele - nunca ler nem apagar. Ver ADR 0006.

resource "aws_s3_bucket" "backups" {
  bucket = var.bucket_name

  tags = {
    Name = "${var.app_name}-db-backups"
    App  = var.app_name
  }
}

module "backups_hardening" {
  source    = "../private-encrypted-bucket"
  bucket_id = aws_s3_bucket.backups.id
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id

  rule {
    id     = "expire-old-backups"
    status = "Enabled"

    filter {
      prefix = ""
    }

    expiration {
      days = var.lifecycle_expiration_days
    }
  }
}

data "aws_iam_policy_document" "assume_role_ec2" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "backup" {
  name               = "${var.app_name}-backup-role"
  assume_role_policy = data.aws_iam_policy_document.assume_role_ec2.json
}

data "aws_iam_policy_document" "backup_s3_write" {
  statement {
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.backups.arn}/*"]
  }
}

resource "aws_iam_role_policy" "backup_s3_write" {
  name   = "${var.app_name}-backup-s3-write"
  role   = aws_iam_role.backup.id
  policy = data.aws_iam_policy_document.backup_s3_write.json
}

resource "aws_iam_instance_profile" "backup" {
  name = "${var.app_name}-backup-profile"
  role = aws_iam_role.backup.name
}
