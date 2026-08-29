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

variable "state_key" {
  description = "Key do state remoto dentro do bucket (mesmo valor do bloco backend \"s3\" em versions.tf) - usada pra restringir a escrita da role ao lockfile nativo do S3 (key + \".tflock\"), sem dar PutObject/DeleteObject sobre o resto do bucket"
  type        = string
  default     = "estado/terraform.tfstate"
}

variable "workflow_filename" {
  description = "Nome do arquivo do workflow autorizado a assumir esta role (em .github/workflows/), usado na condicao job_workflow_ref do trust policy - restringe o token OIDC a esse workflow especifico, nao a qualquer workflow do repo rodando em master"
  type        = string
  default     = "terraform-drift-check.yml"
}
