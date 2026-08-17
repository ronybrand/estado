variable "admin_cidr" {
  description = "IP do administrador autorizado a SSH (/32) - nunca 0.0.0.0/0, ver ADR 0004"
  type        = string
  sensitive   = true

  validation {
    condition     = can(cidrhost(var.admin_cidr, 0)) && split("/", var.admin_cidr)[1] == "32"
    error_message = "admin_cidr deve ser um IP unico em formato CIDR /32 (ex: 203.0.113.5/32) - nunca uma faixa ampla como 0.0.0.0/0."
  }
}

variable "ami_id" {
  description = "AMI atualmente rodando na instancia - ver comentario em modules/portfolio-instance/variables.tf"
  type        = string
  default     = "ami-064f44895dd6e892a"
}

variable "frontend_bucket_name" {
  description = "Nome do bucket S3 que serve o build do Angular via CloudFront - precisa ser globalmente unico, ver ADR 0013"
  type        = string
}

variable "frontend_github_repo" {
  description = "Repo do GitHub do frontend Angular autorizado a assumir a role de deploy via OIDC, formato owner/repo"
  type        = string
}
