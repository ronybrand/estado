variable "github_repo" {
  description = "Repo do GitHub autorizado a assumir a role, formato owner/repo (ex: ronybrand/angular_estado)"
  type        = string
}

variable "role_name" {
  description = "Nome da IAM role assumida pelo GitHub Actions"
  type        = string
  default     = "estado-frontend-deploy"
}

variable "frontend_bucket_arn" {
  description = "ARN do bucket S3 do frontend (modules/frontend-static) que a role pode sincronizar"
  type        = string
}

variable "cloudfront_distribution_arn" {
  description = "ARN da CloudFront distribution que a role pode invalidar"
  type        = string
}
