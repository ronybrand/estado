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

variable "proxy_secret" {
  description = "Segredo compartilhado com o backend (RATE_LIMIT_PROXY_SECRET, mesmo valor de BACKEND_PROXY_SECRET no frontend React na Vercel) - deixa o CloudFront repassar o IP real do visitante ao rate limit por IP, ver ADR 0016. Vazio desliga (o limite volta a ser por no de borda)"
  type        = string
  sensitive   = true
  default     = ""
}

variable "backend_github_repo" {
  description = "Repo do GitHub deste projeto (backend + Terraform), autorizado a assumir a role read-only do drift-check via OIDC, formato owner/repo - ver ADR 0015"
  type        = string
  default     = "ronybrand/estado"
}

variable "domain_name" {
  type        = string
  description = "Dominio proprio registrado no Route 53 (a zona hospedada precisa existir)."
  default     = "ronybrand.click"
}

variable "ami_id_us_east_1" {
  description = "AMI arm64 (Amazon Linux 2023) da instancia nova em us-east-1 - fase 5 de docs/plano-dominio-proprio.md. Fixada explicitamente, mesmo raciocinio do ami_id original: resolver a AMI mais recente a cada apply arriscaria substituir a instancia sem isso ser uma decisao deliberada."
  type        = string
  default     = "ami-0ae8605ed708e3c3e" # al2023-ami-2023.12.20260930.0-kernel-6.1-arm64
}
