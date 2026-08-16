output "instance_profile_name" {
  value = aws_iam_instance_profile.backup.name
}

output "role_name" {
  value = aws_iam_role.backup.name
}

output "bucket_name" {
  value = aws_s3_bucket.backups.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.backups.arn
}
