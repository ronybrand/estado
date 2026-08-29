variable "github_repo" {
  description = "Repo do GitHub autorizado a assumir a role, formato owner/repo (ex: ronybrand/estado)"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN do provider OIDC do GitHub ja existente na conta (module.estado_frontend_deploy.oidc_provider_arn) - so pode existir um por conta, este modulo nao cria um novo"
  type        = string
}

variable "role_name" {
  description = "Nome da IAM role assumida pelo GitHub Actions pro drift-check"
  type        = string
  default     = "estado-terraform-plan"
}

variable "state_bucket_arn" {
  description = "ARN do bucket S3 do state remoto (terraform/state-backend.tf), pro qual a role precisa de leitura/escrita (o locking nativo do S3 escreve um lockfile mesmo durante um plan)"
  type        = string
}
