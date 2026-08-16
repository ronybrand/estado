variable "app_name" {
  description = "Nome do app, usado pra nomear role/profile (ex: estado)"
  type        = string
}

variable "bucket_name" {
  description = "Nome do bucket S3 de backup - passado explicito, nao gerado pelo modulo, pra nao arriscar renomear um bucket existente num import"
  type        = string
}

variable "lifecycle_expiration_days" {
  description = "Dias ate um backup expirar no bucket. Placeholder, nao requisito de negocio - ver ADR 0006"
  type        = number
  default     = 30
}
